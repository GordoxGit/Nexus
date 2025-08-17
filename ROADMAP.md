# 🗺️ Roadmap du Projet Henebrain

Ce document détaille le plan de développement du plugin Henebrain. Il se concentre actuellement sur la première étape majeure pour garantir des fondations solides.

---

## 📍 Étape 1 : Le Cœur du Gameplay (MVP - Minimum Viable Product) - v0.1.0

**Objectif :** Mettre en place les mécaniques fondamentales pour qu'une partie complète puisse se dérouler du début à la fin de manière stable et fonctionnelle.

### 1.1 - Gestion des Arènes (Configuration et Stockage)
-   [ ] **Classe de Gestion (`ArenaManager`) :**
    -   Développer une classe singleton pour charger, sauvegarder et accéder aux données de toutes les arènes.
-   [ ] **Commandes Administrateur (`/hadmin`) :**
    -   Permission requise : `henebrain.admin`.
    -   `/hadmin create <nom_arene>` : Crée une nouvelle arène vide dans la configuration et envoie un message de confirmation.
    -   `/hadmin setlobby <nom_arene>` : Définit le point de téléportation du lobby d'attente à la position actuelle de l'administrateur.
    -   `/hadmin setspawn <nom_arene> <equipe(rouge|bleu)>` : Définit le point de spawn de l'équipe spécifiée à la position actuelle.
    -   `/hadmin setbrain <nom_arene> <equipe(rouge|bleu)>` : Enregistre la position du bloc que l'administrateur regarde comme étant le "Cerveau" de l'équipe.
    -   `/hadmin setbridge <nom_arene> <pos1|pos2>` : Définit les deux coins d'une région cubique (`Cuboid`) qui représente le pont central.
-   [ ] **Structure de Stockage (`arenas.yml`) :**
    -   Utiliser un fichier YAML pour stocker les données de manière persistante.
    -   **Exemple de structure pour une arène :**
        ```yaml
        arenas:
          canyon:
            lobby: world,x,y,z,yaw,pitch
            teams:
              rouge:
                spawn: world,x,y,z,yaw,pitch
                brain: world,x,y,z
              bleu:
                spawn: world,x,y,z,yaw,pitch
                brain: world,x,y,z
            bridge:
              pos1: world,x,y,z
              pos2: world,x,y,z
        ```

### 1.2 - Gestion des Joueurs et des Équipes
-   [ ] **Commandes Joueur (`/hb`) :**
    -   `/hb join <nom_arene>` : Ajoute le joueur à la file d'attente de l'arène. Le téléporte au lobby de l'arène.
    -   `/hb leave` : Retire le joueur de l'arène (en attente ou en jeu) et le téléporte à un lobby principal (configurable).
-   [ ] **Système d'Équipes (`TeamManager`) :**
    -   Assigner le premier joueur à l'équipe Rouge, le deuxième à Bleu, le troisième à Rouge, etc., pour assurer un équilibre constant.
    -   Gérer le départ d'un joueur et ses conséquences sur l'équilibre des équipes.
    -   Stocker l'inventaire du joueur à son entrée et le lui restaurer à sa sortie.
    -   Nettoyer l'inventaire et les effets de potion du joueur lorsqu'il rejoint une partie.

### 1.3 - Cycle de Vie d'une Partie (`GameStateManager`)
-   [ ] **Implémenter un système d'états de jeu :** `WAITING`, `STARTING`, `PLAYING`, `ENDING`.
-   [ ] **`WAITING` (En attente) :**
    -   État par défaut. Le jeu attend qu'un nombre minimum de joueurs (configurable, ex: 2) rejoigne.
    -   Afficher le nombre de joueurs / joueurs requis dans l'Action Bar toutes les 2 secondes.
    -   Passe à `STARTING` lorsque le nombre de joueurs est atteint.
-   [ ] **`STARTING` (Démarrage) :**
    -   Lance un compte à rebours de 10 secondes, affiché en grand titre (`Title`) à tous les joueurs de l'arène.
    -   Geler les joueurs pour les empêcher de bouger pendant le décompte.
    -   À la fin du décompte, téléporter chaque joueur au spawn de son équipe respective, lui donner son équipement de base (épée en fer, armure en cuir colorée, bloc de laine de la couleur de l'équipe en infini).
    -   Passe à `PLAYING`.
-   [ ] **`PLAYING` (En jeu) :**
    -   Libérer les joueurs. Le PvP est activé. La pose de blocs est autorisée.
    -   Début du suivi des scores et des événements de jeu.
    -   Passe à `ENDING` lorsqu'une condition de victoire est remplie.
-   [ ] **`ENDING` (Fin) :**
    -   Geler tous les joueurs.
    -   Afficher un message de victoire/défaite en `Title` (`L'équipe ROUGE a gagné !`).
    -   Après 10 secondes, téléporter tous les joueurs vers le lobby principal du serveur.
    -   Réinitialiser l'arène pour la prochaine partie.

### 1.4 - Mécaniques de Score et de Victoire
-   [ ] **Condition de Victoire - Le "Cerveau" :**
    -   Utiliser l'événement `BlockBreakEvent` pour écouter la destruction d'un bloc.
    -   Vérifier si le bloc détruit correspond à l'emplacement d'un "Cerveau".
    -   Vérifier que le joueur qui le casse appartient à l'équipe adverse.
    -   Si les conditions sont remplies, déclencher immédiatement l'état `ENDING`.
-   [ ] **Condition de Score - Le Pont :**
    -   Utiliser l'événement `PlayerMoveEvent` pour détecter les mouvements des joueurs.
    -   Créer un `HashMap<UUID, Boolean>` pour suivre les joueurs ayant traversé le pont (`hasCrossedBridge`).
    -   Quand un joueur entre dans la zone du pont, mettre sa valeur à `true`.
    -   Quand un joueur avec la valeur `true` revient dans la zone de sa base (à définir, peut-être une région autour du spawn), lui accorder un point, le notifier, et remettre sa valeur à `false`.
    -   Utiliser un **Scoreboard** pour afficher les scores des équipes Rouge et Bleu en temps réel.
-   [ ] **Gestion de la Mort et Réapparition :**
    -   Écouter l'événement `PlayerDeathEvent`.
    -   Annuler le message de mort public.
    -   Remettre la valeur `hasCrossedBridge` du joueur à `false`.
    -   Faire réapparaître le joueur instantanément au spawn de son équipe (`player.spigot().respawn()`).
