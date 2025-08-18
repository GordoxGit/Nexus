Feuille de Route du Projet Henebrain
Cette feuille de route présente les grandes étapes prévues pour le développement de la première version de Henebrain.

Version 1.0.0
[ ] Jalon 1 : Structure de Base et Gestion des Données

Initialisation du projet Maven.

Mise en place de la connexion à la base de données MySQL.

Création d'un gestionnaire de données pour les profils des joueurs (stats, Elo, etc.).

[ ] Jalon 2 : Système de Gestion des Arènes

Développement de l'API interne pour créer, charger et réinitialiser les arènes.

Création des commandes administratives pour gérer les arènes en jeu.

[ ] Jalon 3 : Logique de Jeu Fondamentale

Implémentation du cycle de vie d'une partie (attente, début, fin).

Gestion des manches (rounds), incluant la réinitialisation du pont.

Définition des conditions de victoire et de défaite.

[ ] Jalon 4 : Système Économique et Boutique

Développement du système de points (gains par élimination, victoire, etc.).

Création de l'interface de la boutique d'avant-manche.

Implémentation de la logique d'achat et de persistance des équipements.

[ ] Jalon 5 : Matchmaking et Classement (Elo)

Mise en place du système de file d'attente (queue) pour les différents modes de jeu.

Intégration d'un algorithme de calcul Elo (type Glicko-2).

Gestion de la mise à jour du classement après chaque partie.

[ ] Jalon 6 : Panneau d'Administration et Finalisation

Développement de l'interface graphique (GUI) pour configurer le plugin en jeu.

Tests complets, débogage et optimisation des performances.
