# CivGo – plugins (Spigot 1.21.11, Java 21)

4 petits plugins séparés. Chacun a son `config.yml` : **mets les coordonnées de ton Cratère** dans Noirs, Éclats et Événements.

| Plugin | Rôle | Dépendance |
|---|---|---|
| CivGoNoirs | Nuits imprévisibles (annonce, ciel sombre, mobs renforcés, danger selon la distance au Cratère) | aucune |
| CivGoEclats | Monnaie unique (minage dans le Cratère, stock limité qui se régénère, fournisseur Vault) | **Vault** |
| CivGoCicatrice | Nom RP au 1er login + Cicatrice qui brille pendant les Noirs | aucune |
| CivGoEvenements | Pulsation du Cratère, nuit exceptionnelle, pluie d'Éclats, chute de la Lune | aucune (utilise Noirs/Éclats si présents) |

## Compiler
Prérequis : JDK 21 + Maven.
    mvn clean package
Les jars sont dans `CivGoXxx/target/CivGoXxx.jar`. Copie-les dans `plugins/` puis redémarre le serveur.

## Installation conseillée
Plugins tout faits à ajouter : Vault, EssentialsX, LuckPerms, Towny (ou Lands), ChatControl (ou TAB), CoreProtect.
N'installe **pas** d'autre plugin d'économie : CivGoEclats est le fournisseur Vault (EssentialsX `/pay`, `/balance` utilisent les Éclats).

## Commandes
- `/noir [start [minutes]|stop|status]` – perm `civgo.noirs.admin`
- `/eclats solde|deposer|retirer <n>|payer <joueur> <n>` ; `/eclats admin give|take|set <joueur> <n>`, `stock [n]`, `pluie <n>` – perm `civgo.eclats.admin`
- `/identite`, `/identite whois <texte>|reset <pseudo>|setnom <pseudo> <nom>`, `/cicatrice [pseudo]` – perm `civgo.cicatrice.admin`
- `/civevent pulsation|nuit|pluie|lune` et `/civevent dans <minutes> <type>` – perm `civgo.events.admin`

## Comment ça marche
- **Éclats** : les blocs `ore-material` (par défaut `BUDDING_AMETHYST`, non obtenable en survie) placés dans le rayon du Cratère donnent 1 Éclat tant que le stock > 0. Le stock se régénère (+5 toutes les 10 min par défaut). Les Éclats sont des éclats d'améthyste spéciaux ; `/eclats deposer` les convertit en solde Vault.
- **Noirs ↔ Cicatrice** : pendant un Noir, CivGoNoirs pose la métadonnée `civgo_noir` sur le monde ; CivGoCicatrice la lit pour faire briller les joueurs.
- **Nom RP** : pseudo masqué au-dessus des têtes (option `hide-nametags`). Clic droit sur un joueur proche = on retient son nom RP ; accroupi = on voit aussi sa Cicatrice. Pour le chat RP (distance, /me, /do) utilise ChatControl.
- **Chute de la Lune** : dans `CivGoEvenements/config.yml`, mets la commande de ton plugin CivGo Cinematique dans `commands.lune`.

## Légèreté (offre gratuite)
Un seul timer toutes les 5 secondes par plugin, aucune boucle par tick.
