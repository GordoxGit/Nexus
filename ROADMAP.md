# 🗺️ Roadmap du Projet Henebrain

Ce document détaille le plan de développement du plugin Henebrain, étape par étape.

---

## 📍 Étape 1 : Le Cœur du Gameplay (MVP - Minimum Viable Product) - v0.1.0

**Objectif :** Mettre en place les mécaniques fondamentales pour qu'une partie complète puisse se dérouler du début à la fin.

### 1.1 - Gestion des Arènes
-   [ ] **Commande Admin (`/hadmin`) :**
    -   [ ] `/hadmin create <nom_arene>` : Crée une nouvelle configuration d'arène.
    -   [ ] `/hadmin setlobby <nom_arene>` : Définit le lobby d'attente de l'arène.
    -   [ ] `/hadmin setspawn <nom_arene> <equipe(rouge|bleu)>` : Définit le point de spawn d'une équipe.
    -   [ ] `/hadmin setbrain <nom_arene> <equipe(rouge|bleu)>` : Définit l'emplacement du bloc "Cerveau" d'une équipe.
-   [ ] **Stockage :**
    -   [ ] Créer un gestionnaire d'arènes (`ArenaManager`).
    -   [ ] Sauvegarder les informations des arènes dans un fichier de configuration (e.g., `arenas.yml`).

### 1.2 - Gestion des Équipes et des Joueurs
-   [ ] **Commandes Joueur (`/hb`) :**
    -   [ ] `/hb join <nom_arene>` : Permet à un joueur de rejoindre une partie.
    -   [ ] `/hb leave` : Permet à un joueur de quitter une partie en cours ou en attente.
-   [ ] **Système d'Équipes :**
    -   [ ] Création des équipes Rouge et Bleu.
    -   [ ] Assignation automatique et équilibrage des joueurs dans les équipes lors de la connexion à une arène.
    -   [ ] Téléportation des joueurs au spawn de leur équipe au début de la partie.
    -   [ ] Gestion de l'inventaire (nettoyage à l'entrée, stuff de base au démarrage).

### 1.3 - Cycle de Vie d'une Partie (`GameStateManager`)
-   [ ] **États de jeu :**
    -   [ ] `WAITING` (En attente) : Le jeu attend le nombre minimum de joueurs.
    -   [ ] `STARTING` (Démarrage) : Un compte à rebours se lance avant le début de la partie.
    -   [ ] `PLAYING` (En jeu) : La partie est active.
    -   [ ] `ENDING` (Fin) : La partie est terminée, affichage des résultats et retour au lobby.
-   [ ] **Transitions :**
    -   [ ] Démarrage automatique de la partie quand le nombre de joueurs requis est atteint.
    -   [ ] Gestion des déconnexions/reconnexions de joueurs.

### 1.4 - Mécaniques de Score et de Victoire
-   [ ] **Le "Cerveau" (Brain) :**
    -   [ ] Détecter quand un joueur casse le bloc "Cerveau" de l'équipe adverse.
    -   [ ] Déclencher la fin de la partie et déclarer l'équipe gagnante.
-   [ ] **Le Pont et le Score :**
    -   [ ] Définir une zone centrale (le pont).
    -   [ ] Attribuer un point à l'équipe d'un joueur lorsqu'il traverse le pont et revient à sa base sans mourir.
    -   [ ] Afficher le score en temps réel (via un scoreboard ou l'action bar).
-   [ ] **Gestion de la Mort :**
    -   [ ] Réapparition instantanée du joueur au spawn de son équipe.
    -   [ ] Annulation du "voyage" pour marquer un point si le joueur meurt.

---

## 📍 Étape 2 : Améliorations et Qualité de Vie - v0.2.0

-   [ ] **Système de Kits :** Introduction de kits de base (Guerrier, Archer).
-   [ ] **Statistiques :** Suivi des statistiques des joueurs (kills, morts, victoires, points marqués).
-   [ ] **Configuration Avancée :** Rendre plus d'éléments configurables (messages, temps, inventaires).

---

## 📍 Étape 3 : Fonctionnalités Avancées - v0.3.0

-   [ ] **Kits Personnalisés :** Création d'un éditeur de kits en jeu.
-   [ ] **Power-ups :** Apparition de bonus temporaires sur la carte.
-   [ ] **Classements :** Commandes pour afficher les meilleurs joueurs (`/hb top`).
-   [ ] **Support Multi-Arènes :** Gestion de plusieurs parties simultanément de manière fluide.
