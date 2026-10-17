package fr.civgo.eclats;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CivGoEclats extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Double> balances = new HashMap<>();
    private final Random rnd = new Random();
    private int stock;
    private NamespacedKey key;
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "eclat");
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();

        getServer().getServicesManager().register(Economy.class, new EclatsEconomy(this), this, ServicePriority.Highest);
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("eclats").setExecutor(this);

        // Régénération lente du stock (timer espacé)
        long regenTicks = Math.max(1, getConfig().getInt("stock.regen-interval-minutes", 10)) * 1200L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int max = getConfig().getInt("stock.max", 500);
            stock = Math.min(max, stock + getConfig().getInt("stock.regen-amount", 5));
        }, regenTicks, regenTicks);

        // Sauvegarde automatique toutes les 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ------------------------------------------------------------ données

    private void loadData() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        stock = y.getInt("stock", getConfig().getInt("stock.start", 200));
        ConfigurationSection s = y.getConfigurationSection("balances");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    balances.put(UUID.fromString(k), s.getDouble(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("stock", stock);
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            y.set("balances." + e.getKey(), e.getValue());
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Sauvegarde impossible : " + ex.getMessage());
        }
    }

    public double getBalance(UUID id) {
        return balances.getOrDefault(id, getConfig().getDouble("starting-balance", 0));
    }

    public void setBalance(UUID id, double v) {
        balances.put(id, Math.max(0, v));
    }

    // ------------------------------------------------------------ items

    public ItemStack makeEclat(int amount) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§dÉclat");
        m.setLore(List.of("§7Un fragment arraché au Cratère.", "§8Monnaie de CivGo"));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isEclat(ItemStack it) {
        if (it == null || it.getType() != Material.AMETHYST_SHARD || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ minage dans le Cratère

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Material ore = Material.matchMaterial(getConfig().getString("ore-material", "BUDDING_AMETHYST"));
        Block b = e.getBlock();
        if (ore == null || b.getType() != ore) return;

        World w = Bukkit.getWorld(getConfig().getString("world", "world"));
        if (w == null || !b.getWorld().equals(w)) return;

        double dx = b.getX() + 0.5 - getConfig().getDouble("crater.x", 0);
        double dz = b.getZ() + 0.5 - getConfig().getDouble("crater.z", 0);
        if (Math.hypot(dx, dz) > getConfig().getDouble("radius", 80)) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        e.setDropItems(false);
        if (stock <= 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§8La veine est tarie... elle se reforme lentement."));
            return;
        }
        stock--;
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), makeEclat(1));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§d+1 Éclat"));
    }

    // ------------------------------------------------------------ pluie d'Éclats

    public void rain(int total) {
        World w = Bukkit.getWorld(getConfig().getString("world", "world"));
        if (w == null) return;
        new BukkitRunnable() {
            int left = total;

            @Override
            public void run() {
                List<Player> ps = w.getPlayers();
                if (left <= 0 || ps.isEmpty()) {
                    cancel();
                    return;
                }
                int batch = Math.min(left, 3);
                for (int i = 0; i < batch; i++) {
                    Player p = ps.get(rnd.nextInt(ps.size()));
                    Location l = p.getLocation().add(rnd.nextInt(17) - 8, 18, rnd.nextInt(17) - 8);
                    Item item = w.dropItem(l, makeEclat(1));
                    item.setGlowing(true);
                    w.spawnParticle(Particle.END_ROD, l, 6, 0.2, 0.2, 0.2, 0.01);
                }
                left -= batch;
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    // ------------------------------------------------------------ commandes

    private String fmt(double v) {
        long n = Math.round(v);
        return n + " " + (n == 1 ? getConfig().getString("currency-singular", "Éclat")
                : getConfig().getString("currency-plural", "Éclats"));
    }

    private Integer parseInt(CommandSender s, String str) {
        try {
            int n = Integer.parseInt(str);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException ex) {
            s.sendMessage(ChatColor.RED + "Nombre invalide (entier positif attendu).");
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "solde" : a[0].toLowerCase();
        boolean admin = s.hasPermission("civgo.eclats.admin");

        switch (sub) {
            case "solde" -> {
                if (a.length > 1 && admin) {
                    OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                    s.sendMessage(ChatColor.LIGHT_PURPLE + a[1] + " possède " + fmt(getBalance(t.getUniqueId())) + ".");
                } else if (s instanceof Player p) {
                    s.sendMessage(ChatColor.LIGHT_PURPLE + "Tu possèdes " + fmt(getBalance(p.getUniqueId())) + ".");
                } else {
                    s.sendMessage("Usage : /eclats solde <joueur>");
                }
            }
            case "deposer" -> {
                if (!(s instanceof Player p)) return true;
                ItemStack[] inv = p.getInventory().getStorageContents();
                int total = 0;
                for (int i = 0; i < inv.length; i++) {
                    if (isEclat(inv[i])) {
                        total += inv[i].getAmount();
                        inv[i] = null;
                    }
                }
                if (total == 0) {
                    s.sendMessage(ChatColor.RED + "Tu n'as aucun Éclat sur toi.");
                    return true;
                }
                p.getInventory().setStorageContents(inv);
                setBalance(p.getUniqueId(), getBalance(p.getUniqueId()) + total);
                s.sendMessage(ChatColor.LIGHT_PURPLE + "Tu déposes " + fmt(total) + ". Solde : " + fmt(getBalance(p.getUniqueId())) + ".");
            }
            case "retirer" -> {
                if (!(s instanceof Player p) || a.length < 2) {
                    s.sendMessage("Usage : /eclats retirer <nombre>");
                    return true;
                }
                Integer n = parseInt(s, a[1]);
                if (n == null) return true;
                if (n > 2000) {
                    s.sendMessage(ChatColor.RED + "Maximum 2000 par retrait.");
                    return true;
                }
                if (getBalance(p.getUniqueId()) < n) {
                    s.sendMessage(ChatColor.RED + "Solde insuffisant.");
                    return true;
                }
                setBalance(p.getUniqueId(), getBalance(p.getUniqueId()) - n);
                int left = n;
                while (left > 0) {
                    int part = Math.min(64, left);
                    Map<Integer, ItemStack> over = p.getInventory().addItem(makeEclat(part));
                    for (ItemStack rest : over.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    }
                    left -= part;
                }
                s.sendMessage(ChatColor.LIGHT_PURPLE + "Tu retires " + fmt(n) + ". Solde : " + fmt(getBalance(p.getUniqueId())) + ".");
            }
            case "payer" -> {
                if (!(s instanceof Player p) || a.length < 3) {
                    s.sendMessage("Usage : /eclats payer <joueur> <nombre>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null || t.equals(p)) {
                    s.sendMessage(ChatColor.RED + "Joueur introuvable (ou toi-même).");
                    return true;
                }
                Integer n = parseInt(s, a[2]);
                if (n == null) return true;
                if (getBalance(p.getUniqueId()) < n) {
                    s.sendMessage(ChatColor.RED + "Solde insuffisant.");
                    return true;
                }
                setBalance(p.getUniqueId(), getBalance(p.getUniqueId()) - n);
                setBalance(t.getUniqueId(), getBalance(t.getUniqueId()) + n);
                s.sendMessage(ChatColor.LIGHT_PURPLE + "Tu donnes " + fmt(n) + " à " + t.getDisplayName() + ChatColor.LIGHT_PURPLE + ".");
                t.sendMessage(ChatColor.LIGHT_PURPLE + "Tu reçois " + fmt(n) + " de " + p.getDisplayName() + ChatColor.LIGHT_PURPLE + ".");
            }
            case "admin" -> {
                if (!admin) {
                    s.sendMessage(ChatColor.RED + "Tu n'as pas la permission.");
                    return true;
                }
                adminCommand(s, a);
            }
            default -> s.sendMessage(ChatColor.GRAY + "/eclats <solde|deposer|retirer|payer>");
        }
        return true;
    }

    private void adminCommand(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(ChatColor.GRAY + "/eclats admin <give|take|set> <joueur> <n> | stock [n] | pluie <n>");
            return;
        }
        switch (a[1].toLowerCase()) {
            case "give", "take", "set" -> {
                if (a.length < 4) {
                    s.sendMessage(ChatColor.RED + "Usage : /eclats admin " + a[1] + " <joueur> <n>");
                    return;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[2]);
                if (!t.isOnline() && !t.hasPlayedBefore()) {
                    s.sendMessage(ChatColor.RED + "Joueur inconnu.");
                    return;
                }
                int n;
                try {
                    n = Integer.parseInt(a[3]);
                } catch (NumberFormatException ex) {
                    s.sendMessage(ChatColor.RED + "Nombre invalide.");
                    return;
                }
                UUID id = t.getUniqueId();
                switch (a[1].toLowerCase()) {
                    case "give" -> setBalance(id, getBalance(id) + n);
                    case "take" -> setBalance(id, getBalance(id) - n);
                    default -> setBalance(id, n);
                }
                s.sendMessage(ChatColor.LIGHT_PURPLE + a[2] + " : " + fmt(getBalance(id)) + ".");
            }
            case "stock" -> {
                if (a.length > 2) {
                    Integer n = parseInt(s, a[2]);
                    if (n == null) return;
                    stock = Math.min(n, getConfig().getInt("stock.max", 500));
                }
                s.sendMessage(ChatColor.LIGHT_PURPLE + "Stock du Cratère : " + stock + " / " + getConfig().getInt("stock.max", 500));
            }
            case "pluie" -> {
                if (a.length < 3) {
                    s.sendMessage(ChatColor.RED + "Usage : /eclats admin pluie <n>");
                    return;
                }
                Integer n = parseInt(s, a[2]);
                if (n == null) return;
                rain(Math.min(n, 1000));
                s.sendMessage(ChatColor.LIGHT_PURPLE + "Pluie d'Éclats lancée (" + Math.min(n, 1000) + ").");
            }
            default -> s.sendMessage(ChatColor.RED + "Sous-commande inconnue.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (a.length == 1) {
            return s.hasPermission("civgo.eclats.admin")
                    ? List.of("solde", "deposer", "retirer", "payer", "admin")
                    : List.of("solde", "deposer", "retirer", "payer");
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("civgo.eclats.admin")) {
            return List.of("give", "take", "set", "stock", "pluie");
        }
        return null; // noms de joueurs par défaut
    }
}
