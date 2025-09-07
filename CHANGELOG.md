# Changelog

## [0.1.0-alpha] - 2025-09-07
### Added
- **Système d'Arènes Initial** : Implémentation de la gestion basique des arènes avec création, sauvegarde et chargement des spawns depuis la base de données.
- **Base de Données** : Mise en place de l'infrastructure de la base de données avec HikariCP pour le pooling de connexions.
- **Commandes d'Administration** : Ajout de la commande `/nx arena` pour la gestion des arènes.

### Changed
- **Migration de Flyway vers Liquibase** : Remplacement du système de migration Flyway par Liquibase pour résoudre des problèmes de compatibilité profonds avec l'environnement du serveur et les versions de MariaDB.

### Fixed
- **Problèmes de Démarrage (Startup Crash)** : Correction d'une série d'erreurs critiques qui empêchaient le plugin de démarrer.
    - Résolution des problèmes de dépendances avec `triumph-gui` en utilisant les bonnes coordonnées Maven depuis Sonatype.
    - Correction du problème de `ServiceLoader` de Liquibase en configurant un `ServicesResourceTransformer` dans le `maven-shade-plugin` pour fusionner correctement les fichiers de services.
    - Stabilisation de l'initialisation de Liquibase en assurant que le ClassLoader du plugin est utilisé au bon moment.
