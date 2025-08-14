# HikaBrain — Heneria

[![Java 21](https://img.shields.io/badge/Java-21-red?logo=openjdk)](https://openjdk.org/)
[![Spigot/Paper 1.21](https://img.shields.io/badge/Spigot/Paper-1.21-yellow?logo=spigotmc)](https://www.spigotmc.org/)
[![Gradle](https://img.shields.io/badge/Gradle-build-blue?logo=gradle)](https://gradle.org/)
[![Version](https://img.shields.io/badge/Version-1.4.0-informational)](CHANGELOG.md)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

Site : [heneria.com](https://heneria.com)

## Sommaire
- [Vue d’ensemble](#vue-densemble)
- [Prérequis](#prérequis)
- [Installation & Build](#installation--build)
- [Configuration rapide](#configuration-rapide)
- [Commandes & permissions](#commandes--permissions)
- [Fonctionnalités clés](#fonctionnalités-clés)
- [Déploiement & exploitation](#déploiement--exploitation)
- [Performances & bonnes pratiques](#performances--bonnes-pratiques)
- [Débogage / erreurs courantes](#débogage--erreurs-courantes)
- [Roadmap courte](#roadmap-courte)
- [Contribution](#contribution)
- [Licence](#licence)

## Vue d’ensemble
Mini-jeu HikaBrain performant et multi-arènes : pont cassable, lits non-interactifs et UI propre.

Architecture rapide : `GameManager`, `listeners/*`, `ui/*`, `arena/*`, `services/*`.

Mondes autorisés : `world_hika` (par défaut) via `allowed-worlds`.

## Prérequis
- Java 21
- Spigot/Paper 1.21 (API)
- Gradle
- Aucune dépendance externe obligatoire (FAWE optionnel)
- Branding Heneria, domaine [heneria.com](https://heneria.com)

## Installation & Build
1. Cloner le dépôt puis exécuter :
   ```bash
   ./gradlew build
   # ou sur Windows
   gradlew.bat build
   ```
2. Copier `build/libs/HikaBrain-*.jar` dans `plugins/`.
3. Mettre à jour la version à chaque release dans :
   - `build.gradle.kts`
   - `src/main/resources/plugin.yml`

## Configuration rapide
`config.yml` (extrait) :
```yml
allowed-worlds:
  - world_hika

world-allowed: "world_hika"

lobby:
  world: world_hika
  x: 0.5
  y: 80.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0

server:
  display-name: "Heneria"
  domain: "heneria.com"

compass:
  enabled: true
  material: CLOCK
  give-on-join: true
  slot: 8
  open-on-right-click: true
  cooldown-ms: 200
```

  Horloge (PDC `nav_compass`) : horloge donnée au join, ouvre le menu partout sans TP.

Dossier arènes `plugins/HikaBrain/arenas/*.yml` :
```yml
teamSize: 1|2|3|4
world: world_hika
spawns:
  red: { x: ~, y: ~, z: ~, yaw: ~, pitch: ~ }
  blue: { x: ~, y: ~, z: ~, yaw: ~, pitch: ~ }
beds:
  red: { x: ~, y: ~, z: ~ }
  blue: { x: ~, y: ~, z: ~ }
broke:
  pos1: { x: ~, y: ~, z: ~ }
  pos2: { x: ~, y: ~, z: ~ }
```

## Commandes & permissions
```
/hb help
/hb create <nom> <teamSize>
/hb setspawn <red|blue>
/hb setbed
/hb setbroke
/hb setlobby
/hb protect
/hb confirm <nom>
/hb start
/hb stop
/hb join [red|blue]
/hb leave
/hb admin [on|off|toggle]
```

Permissions :
- `hikabrain.admin` — commandes d’admin
- `hikabrain.play` (par défaut)

## Fonctionnalités clés
- Multi-arènes 1v1/2v2/3v3/4v4 (horloge → catégories → arènes)
- Lobby Hika (UI dédiée + horloge de navigation au join)
- Scoreboard & Tablist : titres Heneria/HikaBrain, mode NvN, timer, scores, joueurs dans l’arène, ligne adaptative sans espaces vides
- Lobby : monde, connectés, marque Heneria + heneria.com
- Horloge « menu only » : ouvre le GUI en air/void, jamais de TP, inventaires verrouillés
- Pont cassable (zone broke via SetBroke) + reset à la manche
- Lits non-interactifs en jeu (deny silencieux) + SetBed admin
- Anti-spam join : assignation atomique, re-check capacité

## Déploiement & exploitation
- Le monde `world_hika` doit exister
- Cycle :
  1. `/hb setlobby` dans `world_hika`
  2. `/hb create <nom> <teamSize>` puis config spawns/lits/broke
  3. Sauvegarde arène (si commande dédiée) & tests
- Outils d’édition : `/hb admin on` pour mapper (casser/poser, lits inclus)

## Performances & bonnes pratiques
- Tick-safe, batch pour resets/regen
- Pas d’allocation en boucle (PDC, DustOptions, Teams)
- Budgets : HUD ≤ 1 ms/tick, reset pont batché

## Débogage / erreurs courantes
- Monde manquant (`world_hika`) → logs d’avertissement, certaines fonctions inactives
- Arène « pleine » fantôme → vérifier assignation atomique (`joiningNow`), re-check capacité
- Horloge qui TP → vérifier listener `HIGHEST` + anti-TP (metadata)

## Roadmap courte
- **1.2.x** : polish UI (HUD/bossbar), recap fin de manche, spectateur avancé (compass GUI)
- **1.3.x** : gadgets équilibrés (grappin charges, smoke), events doux (resource pulse)
- **1.4.x** : FAWE/snapshot optionnels, API publique pour maps

## Contribution
- Lint/style Java 21, conventions de nommage
- Checklist PR :
  - build ok
  - bump versions
  - mise à jour README/CHANGELOG
  - tests manuels

## Licence
Distribué sous licence [MIT](LICENSE).
