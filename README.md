# Henebrain

Henebrain est un plugin Spigot pour Minecraft 1.21. Il fournit l'architecture de base pour un futur jeu personnalisé.

## Installation & Compilation

1. Assurez-vous d'avoir Java 17 et Maven installés.
2. Clonez ce dépôt.
3. Exécutez `mvn package` à la racine du projet.
4. Le fichier `Henebrain-1.0.0-SNAPSHOT.jar` sera généré dans le dossier `target/` et pourra être placé dans le dossier `plugins` de votre serveur Spigot.

## Commandes d'Administration

Toutes les commandes suivantes nécessitent la permission `henebrain.admin` et se basent sur la commande principale `/hb` :

| Commande | Description |
| --- | --- |
| `/hb admin create <nom>` | Crée une nouvelle arène avec le nom donné. |
| `/hb admin delete <nom>` | Supprime l'arène spécifiée. |
| `/hb admin setlobby <nom_arène>` | Définit la position actuelle du joueur comme lobby de l'arène. |
| `/hb admin setspawn <nom_arène> <nom_équipe>` | Définit le spawn de l'équipe donnée à la position du joueur. |
| `/hb admin setpoint <nom_arène>` | Définit la position actuelle du joueur comme point à marquer. |
| `/hb admin addmode <nom_arène> <mode>` | Ajoute un mode de jeu supporté à l'arène. |
| `/hb admin removemode <nom_arène> <mode>` | Retire un mode de jeu supporté de l'arène. |

## Commandes Joueur

| Commande | Description | Permission |
| --- | --- | --- |
| `/hb join <nom_arène>` | Rejoint la file d'attente de l'arène indiquée. | `henebrain.join` |
