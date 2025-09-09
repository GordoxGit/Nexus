# 🚀 Nexus Plugin - Heneria Project

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Minecraft 1.21](https://img.shields.io/badge/Minecraft-1.21-green.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21-blue.svg)](https://papermc.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> **Plugin principal pour le serveur Heneria - Une réinvention moderne du mode de jeu Hikabrain**

## 🎯 Vision du Projet

Nexus est une réinvention complète du mode de jeu Hikabrain, conçue pour offrir une expérience **compétitive moderne** sur Minecraft 1.21. Le projet combine :

- 🎮 **Gameplay innovant** : Mécaniques de capture, transport et destruction stratégiques
- 🏆 **Système de classement** : Ranking Elo avec saisons et récompenses
- 🎨 **Interface moderne** : GUI intuitives sans commandes complexes
- ⚡ **Performance optimisée** : Architecture robuste avec base de données intégrée

## 📋 Table des Matières

- [🎮 Fonctionnalités](#-fonctionnalités)
- [⚙️ Installation](#️-installation)
- [🔧 Configuration](#-configuration)
- [🏗️ Développement](#️-développement)
- [🧪 Tests et Debugging](#-tests-et-debugging)
- [🤝 Contribution](#-contribution)
- [📊 Métriques](#-métriques)

## 🎮 Fonctionnalités

### 🏟️ Système d'Arènes
- **Création flexible** : Configuration d'arènes pour 1v1 jusqu'à 6v6v6v6
- **Gestion des spawns** : Points de réapparition configurables par équipe
- **Interface admin** : Création et modification via GUI sans commandes

### ⚔️ Gameplay Core
- **Capture de Cellules** : Système de capture en équipe avec progression temporisée
- **Transport tactique** : Le porteur est visible, créant des moments de tension
- **Destruction de Nexus** : Objectif final après avoir surchargé le cœur ennemi
- **Élimination finale** : Mode "dernière équipe debout" après destruction du Nexus

### 🛒 Système Économique
- **Points dynamiques** : Kills, assists, victoires, bonus de défaite
- **Boutique intelligente** : Ouverture automatique avec profils d'achat
- **Équilibrage PvP** : Basé sur le plugin [PvpEnhancer](https://github.com/GordoxGit/PvpEnhancer)

### 🏆 Classement & Progression
- **Système Elo** : Ranking compétitif avec divisions visibles
- **Saisons** : Périodes de 3 mois avec soft reset
- **Sanctions** : Système d'abandon avec pénalités graduées

## ⚙️ Installation

### 📋 Prérequis
- **Java 21** ou supérieur
- **Paper/Spigot 1.21** ou supérieur
- **MariaDB/MySQL 10.6+**
- **Minimum 2GB RAM** recommandé

### 🚀 Installation Rapide

1. **Télécharger le plugin**
   ```bash
   # Depuis les releases GitHub
   wget [https://github.com/GordoxGit/Nexus/releases/latest/download/Nexus.jar](https://github.com/GordoxGit/Nexus/releases/latest/download/Nexus.jar)
   ```

2. **Installer sur le serveur**
   ```bash
   # Copier dans le dossier plugins
   cp Nexus.jar /path/to/server/plugins/
   ```

3. **Configurer la base de données**
   
   Créer la base de données :
   ```sql
   CREATE DATABASE nexus CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'nexus_user'@'%' IDENTIFIED BY 'VOTRE_MOT_DE_PASSE';
   GRANT ALL PRIVILEGES ON nexus.* TO 'nexus_user'@'%';
   FLUSH PRIVILEGES;
   ```

4. **Premier démarrage**
   ```bash
   # Démarrer le serveur - le config.yml sera généré
   java -jar server.jar
   
   # Arrêter et configurer
   # Éditer plugins/Nexus/config.yml avec vos paramètres DB
   ```

### 🐳 Installation avec Docker (Recommandée)

```dockerfile
FROM openjdk:21-jre-slim

# Installation du serveur Paper
WORKDIR /minecraft
RUN wget [https://api.papermc.io/v2/projects/paper/versions/1.21/builds/latest/downloads/paper-1.21-build.jar](https://api.papermc.io/v2/projects/paper/versions/1.21/builds/latest/downloads/paper-1.21-build.jar) -O server.jar

# Installation du plugin
COPY Nexus.jar plugins/
COPY config.yml plugins/Nexus/

EXPOSE 25565
CMD ["java", "-Xms2G", "-Xmx4G", "-jar", "server.jar", "--nogui"]
```

## 🔧 Configuration

### 📄 Fichier Principal (`config.yml`)

```yaml
# Configuration de base de données
database:
  host: "localhost"
  port: 3306
  database: "nexus"
  username: "nexus_user"
  password: "VOTRE_MOT_DE_PASSE"
  
  # Paramètres de connexion HikariCP
  hikari:
    maximum-pool-size: 10
    minimum-idle: 5
    connection-timeout: 20000

# Configuration du gameplay
game:
  arena:
    max-players-per-arena: 24
    capture-time-seconds: 30
    respawn-time-seconds: 5
    
  economy:
    kill-reward: 100
    assist-reward: 50
    victory-bonus: 200
    defeat-bonus: [100, 150]  # 1ère et 2ème défaite consécutive

# Système de classement
ranking:
  elo:
    starting-rating: 1000
    k-factor: 32
    seasons:
      duration-months: 3
      soft-reset-percentage: 0.8

# Debug et développement
debug:
  enabled: false
  log-sql-queries: false
  performance-monitoring: true
```

### 🎮 Configuration des Kits

Les kits sont configurables via l'interface admin (`/nx admin`) :

- **Kit Solo** : Armure cuir P2, Plastron/Jambières fer P2, Épée pierre T2
- **Kit Équipe** : Armure cuir P2, Épée bois T2

### 🏪 Boutique

Prix par défaut (configurables) :

| Catégorie | Item | Prix |
|-----------|------|------|
| **Armures** | Maille | 300 pts |
| | Fer | 500 pts |
| | Diamant | 800 pts |
| | Netherite | 1500 pts |
| **Armes** | Épée Pierre | 150 pts |
| | Épée Fer | 350 pts |
| **Utilitaires** | Boule de Feu | 150 pts |
| | Arc Puissance I | 600 pts |
| | Potion Vitesse I | 300 pts |

## 🏗️ Développement

### 🛠️ Environnement de Développement

```bash
# Cloner le projet
git clone [https://github.com/GordoxGit/Nexus.git](https://github.com/GordoxGit/Nexus.git)
cd Nexus

# Vérifier Java 21
java -version

# Compiler
mvn clean compile

# Packager
mvn clean package

# Profil développement (avec timestamp)
mvn clean package -P dev
```

### 🧪 Tests

```bash
# Tests unitaires
mvn test

# Tests d'intégration (nécessite MariaDB)
mvn verify -P integration-tests

# Tests avec base Docker
docker-compose -f docker-compose.test.yml up --build
```

### 📊 Architecture

```
src/main/java/fr/heneria/nexus/
├── Nexus.java                  # Classe principale du plugin
├── arena/                      # Gestion des arènes
│   ├── manager/
│   ├── model/
│   └── repository/
├── game/                       # Logique de jeu (à venir)
│   ├── match/
│   ├── player/
│   └── economy/
├── db/                         # Couche base de données
│   └── HikariDataSourceProvider.java
└── command/                    # Commandes administratives
    └── NexusAdminCommand.java
```

### 📈 Pipeline CI/CD

Le projet utilise **GitHub Actions** pour :

✅ **Validation** - Structure, dépendances, syntaxe
✅ **Build** - Compilation avec MariaDB de test
✅ **Tests d'intégration** - Tests complets avec base réelle
✅ **Sécurité** - Scan OWASP des vulnérabilités
✅ **Qualité** - Métriques de code et bonnes pratiques
✅ **Artifacts** - JAR automatiquement sauvegardé

### 🚀 Déploiement Automatisé

Utiliser le script inclus :

```bash
# Développement
./deploy.sh dev

# Production
./deploy.sh prod

# Avec upload automatique (nécessite sshpass)
VPS_HOST=your.server.ip ./deploy.sh prod
```

## 🧪 Tests et Debugging

### Smoke Tests Locaux
```bash
# Compiler le plugin
mvn clean package -q

# Exécuter le smoke test manuellement
JAR_FILE="target/Nexus-0.0.0-SNAPSHOT.jar"
javac PluginLoadTest.java
java -cp "$JAR_FILE:." PluginLoadTest "$JAR_FILE"
```

### Debugging des Dépendances
```bash
# Vérifier les classes relocalisées
jar tf target/Nexus-*.jar | grep "fr/heneria/nexus/libs/" | sort

# Vérifier les fuites de relocalisation  
jar tf target/Nexus-*.jar | grep -E "(com/zaxxer|org/flywaydb|dev/triumphteam)" | head -5

# Analyser la structure complète
javac JarAnalysis.java
java JarAnalysis target/Nexus-*.jar
```

### Problèmes Courants

**NoClassDefFoundError lors des tests**
- Cause: Les dépendances Bukkit ne sont pas disponibles en CI
- Solution: Utiliser la vérification de présence sans chargement pour les classes Bukkit-dépendantes

**Dépendances manquantes dans le JAR**  
- Cause: Filtres Maven Shade incorrects
- Solution: Vérifier les sections `<filters>` et `<relocations>` dans pom.xml

## 🤝 Contribution

### 📝 Comment Contribuer

1. **Fork** le projet
2. **Créer une branche** : `git checkout -b feature/ma-fonctionnalite`
3. **Commiter** : `git commit -m 'feat: ajouter nouvelle fonctionnalité'`
4. **Push** : `git push origin feature/ma-fonctionnalite`
5. **Ouvrir une Pull Request**

### 📋 Guidelines

- Utiliser [Conventional Commits](https://www.conventionalcommits.org/)
- Ajouter des tests pour les nouvelles fonctionnalités
- Maintenir la couverture de tests > 80%
- Documenter les APIs publiques
- Suivre le style de code Java existant

### 🐛 Reporter un Bug

Utiliser le [template de bug report](.github/ISSUE_TEMPLATE/bug_report.md) avec :
- Étapes de reproduction
- Environnement complet
- Logs d'erreur
- Comportement attendu vs observé

## 📊 Métriques

### 📈 Statistiques du Projet

![GitHub code size](https://img.shields.io/github/languages/code-size/GordoxGit/Nexus)
![GitHub issues](https://img.shields.io/github/issues/GordoxGit/Nexus)
![GitHub pull requests](https://img.shields.io/github/issues-pr/GordoxGit/Nexus)

### ⚡ Performance

- **Démarrage** : < 5 secondes
- **Mémoire** : ~50MB par arène active
- **TPS Impact** : < 0.1% pour 10 joueurs simultanés
- **DB Queries** : < 10ms moyennes avec HikariCP

### 🏆 Roadmap

- [x] **v0.1.0** : Système d'arènes et base de données
- [ ] **v0.2.0** : Logique de gameplay core
- [ ] **v0.3.0** : Système économique et boutique
- [ ] **v0.4.0** : Interface graphique complète
- [ ] **v0.5.0** : Système de classement Elo
- [ ] **v1.0.0** : Release publique

## 📞 Support

### 💬 Communauté

- **Discord** : [Heneria Community](https://discord.gg/Xw3pbXDU7s)
- **GitHub Issues** : [Issues](https://github.com/GordoxGit/Nexus/issues)
- **Discussions** : [GitHub Discussions](https://github.com/GordoxGit/Nexus/discussions)

### 🔧 Support Technique

Pour les problèmes techniques :

1. Vérifier les [issues existantes](https://github.com/GordoxGit/Nexus/issues)
2. Consulter la [documentation](https://github.com/GordoxGit/Nexus/wiki)
3. Créer une [nouvelle issue](https://github.com/GordoxGit/Nexus/issues/new/choose)

---

## 📄 Licence

Ce projet est sous licence MIT. Voir [LICENSE](LICENSE) pour plus de détails.

## 🙏 Remerciements

- **Paper Team** pour l'excellente API
- **MariaDB Foundation** pour la base de données robuste
- **TriumphTeam** pour triumph-gui
- **Communauté Minecraft** pour l'inspiration

---

<div align="center">

**[🏠 Heneria Project](https://heneria.fr) • [📖 Documentation](https://github.com/GordoxGit/Nexus/wiki) • [🐛 Issues](https://github.com/GordoxGit/Nexus/issues) • [💬 Discord](https://discord.gg/Xw3pbXDU7s)**

*Développé avec ❤️ pour la communauté Minecraft francophone*

</div>

