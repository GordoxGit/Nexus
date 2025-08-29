Document de Conception Final et Complet : Projet Heneria & Mode de Jeu Nexus
1. Identité et Vision du Projet
Nom du Projet : Heneria (Serveur), Nexus (Plugin Principal).

Mode de jeu : Une réinvention du Hikabrain dans un style "Arcade Compétitif". L'ambiance combine une présentation dynamique et moderne (effets visuels, sons) avec des mécaniques de fond sérieuses et stratégiques (classement Elo, économie), ce qui justifie le nom "Nexus" pour le projet.

Vision & Objectif : Devenir le serveur de référence pour les meilleurs modes de jeux innovants sur Minecraft 1.21, en offrant une expérience stable, équilibrée, engageante et de haute qualité.

Plateforme : Minecraft 1.21, conçu pour Paper et Spigot.

Architecture Technique : Java 21, Maven, MariaDB 10.6+, HikariCP, Flyway, triumph-gui, PlaceholderAPI.

Dépôt Github : https://github.com/GordoxGit/Nexus

2. Processus et Protocole de Développement
Collaboration IA : Le projet est dirigé par un architecte humain qui conçoit des tickets de développement détaillés pour des IA spécialisées (Codex pour le codage).

Méthodologie : Le développement suit un cycle de conception détaillée, de création de tickets techniques très détaillés pour chaque fonctionnalité, et d'implémentation par l'IA.

Référence de Code : Le dépôt GitHub est la source de vérité et doit être analysé par l'IA avant chaque nouvelle tâche pour assurer la cohérence.

Documentation Centrale (projet.md) : Un fichier projet.md sera créé à la racine du dépôt. Il servira de document de suivi principal, contenant l'intégralité des étapes de conception et de développement du plugin, détaillées au maximum. Ce fichier sera constamment mis à jour et servira de référence absolue pour la création de chaque ticket, garantissant que toutes les tâches se suivent logiquement vers l'objectif final.

Gestion des Versions : Le projet suit le Versioning Sémantique (ex: v0.0.0, jusqu'à v1.0.0 pour la sortie publique).

Documentation Annexe : Le CHANGELOG.md et le README.md sont tenus à jour. La ROADMAP peut être complétée mais les anciens éléments ne sont pas supprimés.

Tests Publics : Le développement se fera sur un VPS dédié qui, après le lancement officiel, servira de serveur de test public (PTS) pour permettre à la communauté de tester les nouveautés et de fournir des retours avant leur déploiement.

3. Mécaniques de Gameplay Fondamentales (Nexus)
Structure d'une Partie : Une partie se joue en plusieurs manches. La première équipe à remporter 3 manches gagne la partie.

Condition de Victoire d'une Manche : La victoire s'obtient en éliminant tous les joueurs de l'équipe adverse après que leur "Cœur Nexus" ait été détruit.

Déroulement d'une Manche :

Phase 1 - Capture des Cellules : Une Cellule d'Énergie se trouve au milieu de la carte, protégée par des barrières invisibles. Pour capturer une Cellule, une équipe doit maintenir une présence majoritaire près d'elle pendant 30 secondes pour désactiver une première couche de protection, puis à nouveau 30 secondes pour une seconde couche. L'équipe adverse peut contester et pauser le timer. Chaque équipe a sa propre progression de capture sur les deux couches.

Phase 2 - Transport et Surcharge : Une fois une Cellule déverrouillée par une équipe, le premier joueur de cette équipe à la toucher en devient le porteur. Il est alors visible de tous (effet "glow"). S'il est tué, la Cellule est réinitialisée au centre. Le porteur doit atteindre le Cœur Nexus adverse pour le "surcharger". Il faut deux surcharges pour détruire la protection du Cœur.

Phase 3 - Destruction du Nexus : Une fois la protection détruite, le Cœur Nexus devient vulnérable et peut être détruit en lui infligeant des dégâts physiques.

Phase Finale - Élimination : Une fois le Cœur d'une équipe détruit, ses membres ne peuvent plus réapparaître. La dernière équipe en vie remporte la manche.

Mort & Réapparition : Un joueur éliminé réapparaît tant que le Cœur Nexus de son équipe est intact. Si le Cœur est détruit, la mort devient définitive pour la manche et le joueur devient spectateur.

Structure des Arènes : Le plugin n'impose pas une structure de déplacement (pont, etc.). La conception est flexible :

Flexibilité Totale : Le système de création d'arènes via le GUI Admin permettra de définir n'importe quelle zone de jeu, ses limites, les emplacements des Cœurs, les points de spawn des Cellules et des joueurs.

Cartes dédiées : L'intention est d'avoir des cartes (mondes) distinctes pour chaque taille d'équipe (une carte pour les modes 1vX, une autre pour les 2vX, etc.), afin d'optimiser la taille et l'agencement du terrain de jeu. Le plugin doit être capable de gérer un grand nombre de joueurs (ex: 6v6v6v6) si la configuration de l'arène le permet.

4. Équipement, Économie et Boutique
Principe de Configurabilité : Tous les aspects de cette section (Kits, Points, Gestion de l'équipement, Boutique, Prix) sont entièrement configurables depuis le menu GUI Admin.

Kits de Base :

Kit Solo (1v1, 1v1v1, 1v1v1v1) : Armure en cuir (P2), Plastron/Jambières en Fer (P2), Épée en Pierre (T2), Arc et accès boutique.

Kit Équipe (2v2+) : Armure en cuir (P2), Épée en Bois (T2) et accès boutique.

Système Économique (Points) :

Kill : +100 points

Assistance : +50 points

Bonus de victoire de manche : +200 points par joueur

Revenu de base par manche : +100 points pour tous (sauf les gagnants)

Bonus de série de défaites : +100 points à la première défaite, +150 à la deuxième. Remis à zéro après une victoire.

Gestion de l'Équipement :

L'équipement acheté est conservé après la mort durant une manche.

L'équipement est réinitialisé au kit de base au début de chaque nouvelle manche.

Boutique :

Ouverture automatique pendant une phase d'achat de 20 secondes.

Intègre des "profils d'achat" configurables en lobby et en jeu.

Grille de Prix des Améliorations et Objets (valeurs par défaut) :

Armures (Amélioration Plastron/Jambières - P2) : Maille (300), Fer (500), Diamant (800), Netherite (1500).

Armes (Épées - T2) : Pierre (150), Fer (350), Diamant (700), Netherite (1200).

Objets Utilitaires : Boule de Feu (150), Arc Puissance I/II/III (600/900/1200), Flèches x16 (100), Potion Vitesse I jetable 8s (300), Potion Force I 30s (800), Cisaille Efficacité I/II (400/1200).

Pouvoir ultime : En cours de conception.

Équilibrage PvP : Basé sur le plugin PvpEnhancer : https://github.com/GordoxGit/PvpEnhancer

5. Systèmes Techniques & Expérience Utilisateur
Architecture Multi-serveur & Hubs : Infrastructure basée sur un Hub Heneria, un Lobby Nexus et des serveurs de jeu, connectés par Velocity.

Expérience Joueur "Zéro Commande" & GUI : La philosophie est de minimiser les commandes pour une expérience moderne, rapide et intuitive via des interfaces visuelles.

Navigation par Items : Les joueurs interagissent via des items dans leur inventaire (Sélecteur de mode, Sélecteur d'équipe, Quitter, etc.).

PNJ Interactifs : Des entités personnalisées avec animations et hologrammes dynamiques dans le lobby pour rejoindre les files d'attente.

Système Social "Amis & Party" : Un GUI complet pour gérer amis (avec statut détaillé) et party (avec invitation via recherche et chat cliquable, et transfert de chef).

Système de Classement (Elo) et Sanctions :

Rangs visibles (Non Classé, Bronze, Argent, Or, etc.).

Saisons de 3 mois avec "soft reset" et récompenses cosmétiques mensuelles.

Un système de passe de combat non pay-to-win sera exploré (cosmétiques, monnaie virtuelle).

Sanctions pour abandon de partie classée, avec interdictions croissantes (5m -> 10m -> 1h -> 24h -> Permanent), gérables par les admins.

Interface d'Administration (Centre de Contrôle Total) :

Principe Directeur : Absolument tous les paramètres du jeu sont configurables via le GUI admin.

Mode Admin : Accessible via /nx admin (permission requise), fait apparaître un item de configuration dans l'inventaire.

Fonctionnalités Clés :

Tableau de Bord des Arènes : Vue centrale des arènes avec leur statut d'élaboration.

Gestion des PNJ : Outil pour créer, placer et configurer les PNJ de sélection de mode et de boutique.

Édition Complète : L'admin peut tout gérer : arènes (création, sélection de zones via baguette type WorldEdit), items de la boutique, kits, sanctions, etc.
Fin du ticket.
