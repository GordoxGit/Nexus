# Roadmap du Projet Henebrain

Ce document est le cahier des charges du développement du plugin Henebrain. Il est destiné à évoluer au fil du projet.

---

## 🎯 Étape 1 : Initialisation Complète et Noyau Fonctionnel (Version 0.1.0)

**Objectif :** Construire une base de plugin extrêmement solide, entièrement administrable, configurable et prête pour l'ajout des mécaniques de jeu. Chaque aspect doit être de qualité professionnelle.

### **1. Infrastructure et Environnement de Projet**
- **Nettoyage du Dépôt Git :** Suppression de tout l'historique et des fichiers existants pour un départ propre.
- **Mise en place de Gradle :**
    - Utilisation du Kotlin DSL (`build.gradle.kts`).
    - Configuration pour la compilation d'un plugin Spigot 1.21.
    - Génération automatique du `plugin.yml` à partir des informations du projet.
- **Architecture des Fichiers :**
    - Structure de packages Java logique et évolutive (`fr.gordox.henebrain` et sous-packages).
    - Classe principale `Henebrain.java` comme point d'entrée.
- **Intégration Continue (CI/CD) :**
    - Configuration d'un workflow GitHub Actions.
    - Compilation et vérification automatiques du code à chaque modification (`push` / `pull_request`).
    - Archivage du JAR compilé pour les tests et les déploiements.
- **Documentation Fondamentale :**
    - `README.md` : Présentation du projet.
    - `CHANGELOG.md` : Suivi des versions et des modifications.
    - `.gitignore` : Assurer un dépôt propre sans fichiers inutiles.

### **2. Systèmes Centraux et Utilitaires**
- **Gestionnaire de Configuration (`ConfigManager`) :**
    - Chargement dynamique du `config.yml`.
    - Création automatique du fichier de configuration s'il est manquant, avec des valeurs par défaut.
    - API interne simple pour accéder aux configurations (`getString`, `getInt`, etc.).
    - Fonction de rechargement à chaud.
- **Gestionnaire de Langues (`LanguageManager`) :**
    - Système de traduction basé sur des fichiers `.yml` externes (ex: `fr_FR.yml`).
    - Prise en charge des codes de couleur Minecraft (`&`).
    - Support des placeholders dynamiques (ex: remplacer `%player%` par le nom du joueur).
    - Fonction de rechargement à chaud des messages.
- **API Interne Centralisée :**
    - Implémentation du Singleton Pattern sur la classe principale (`Henebrain.getInstance()`).
    - Fournir un accès statique et simple à toutes les instances des managers depuis n'importe où dans le code.

### **3. Commandes et Administration**
- **Gestionnaire de Commandes Modulaire :**
    - Création d'une structure abstraite pour gérer facilement des sous-commandes.
    - Le système doit gérer nativement les permissions, la syntaxe, la description et l'auto-complétion (Tab Completion).
- **Implémentation de la Commande Principale `/henebrain` (alias `/hb`) :**
    - **`/hb` ou `/hb version`**: Affiche la version actuelle du plugin, son auteur et des informations utiles. (Permission: `henebrain.command.version`)
    - **`/hb help`**: Affiche une liste dynamique de toutes les sous-commandes disponibles pour le joueur, en fonction de ses permissions. (Permission: `henebrain.command.help`)
    - **`/hb reload`**: Permet de recharger tous les fichiers de configuration et de langue sans redémarrer le serveur. (Permission: `henebrain.admin.reload`)
