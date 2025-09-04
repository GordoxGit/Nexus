# 🎮 Gameplay - Mécaniques de Jeu Nexus

## Vue d'Ensemble

[cite_start]Nexus réinvente le concept traditionnel d'Hikabrain en introduisant un gameplay **tactique par phases** où la stratégie d'équipe prime sur l'action individuelle. [cite: 1] [cite_start]Chaque partie est une bataille épique combinant capture territoriale, transport risqué et destruction coordonnée. [cite: 2]

## 🎯 Philosophie de Gameplay

### Principes Fondamentaux
- **Stratégie > Skill** : L'intelligence tactique l'emporte sur les réflexes
- **Équipe > Individu** : La coordination est essentielle pour la victoire
- **Risk/Reward** : Chaque décision implique des compromis tactiques
- **Progression** : L'adaptation constante selon l'évolution de la partie

### Différentiation vs Hikabrain Classique
| Aspect | Hikabrain Classique | Nexus Innovation |
|---|---|---|
| **Objectif** | Destruction directe du lit | [cite_start]Capture → Transport → Destruction [cite: 5] |
| **Respawn** | Illimité tant que le lit existe | [cite_start]Limité par la santé du Nexus [cite: 6] |
| **Stratégie** | Rush ou défense statique | Phases tactiques dynamiques |
| **Équilibrage** | Souvent déséquilibré | [cite_start]Économie et progression équilibrées [cite: 7] |
| **Durée** | Variable (5min-2h) | [cite_start]Contrôlée par phases (10-20min) [cite: 8] |

## 🔄 Structure de Partie

### Format de Match
```
MATCH (Best of 5 manches)
├── Manche 1 ──┐
├── Manche 2   ├─ Premier à 3 victoires gagne
├── Manche 3   │
├── Manche 4 ──┘
└── Manche 5
```

### Timeline d'une Manche (15-20 minutes)
```
Phase 1: PREPARATION (60s)
├─ Spawn des joueurs
├─ Distribution des kits
├─ Ouverture boutique (20s)
└─ Briefing tactique

Phase 2: CAPTURE (120s)
├─ Activation des Cellules d'Énergie
├─ Bataille pour le contrôle majoritaire
├─ Première couche de protection (30s)
└─ Seconde couche de protection (30s)

Phase 3: TRANSPORT (Variable)
├─ Attribution du rôle Porteur
├─ Protection/élimination du Porteur
├─ Livraison au Nexus adverse
[cite_start]└─ Surcharge (2 livraisons = destruction protection) [cite: 9]

Phase 4: DESTRUCTION (Variable)
├─ Nexus ennemi vulnérable
├─ Assaut coordonné
├─ Défense désespérée
└─ Destruction complète

Phase 5: ELIMINATION (Variable)
├─ Fin des respawns pour l'équipe détruite
├─ Survie des derniers défenseurs
├─ Élimination finale
└─ Victoire de manche
```

## ⚡ Phases de Jeu Détaillées

### Phase 1 : PREPARATION (60 secondes)

**Objectif** : Préparation stratégique et économique

**Mécaniques** :
- **Spawn Protégé** : Joueurs invulnérables dans leur zone de base
- **Boutique Obligatoire** : 20 secondes d'achat forcé pour éviter l'inaction
- **Chat d'Équipe** : Communication exclusive entre coéquipiers
- **Aperçu Carte** : Révélation temporaire des positions des Cellules

**Stratégies Typiques** :
- **Économique** : Achats conservateurs, focus sur la survie
- [cite_start]**Agressive** : Investissement maximal dans l'équipement offensif [cite: 10]
- **Équilibrée** : Mix attaque/défense selon la composition d'équipe

### Phase 2 : CAPTURE (120 secondes)

**Objectif** : Contrôle des Cellules d'Énergie au centre de l'arène

**Mécaniques de Capture** :
```
Cellule d'Énergie
├── Protection Niveau 1 (Barrière Magenta)
│   ├── Temps de Capture: 30 secondes
│   ├── Conditions: Présence majoritaire continue
│   └── Contestation: Timer pause si égalité/infériorité
├── Protection Niveau 2 (Barrière Rouge)
│   ├── Temps de Capture: 30 secondes
│   ├── Conditions: Maintien du contrôle après niveau 1
│   └── Récompense: Cellule déverrouillée pour l'équipe
└── État Final: Cellule prête pour le Transport
```

**Système de Présence** :
- [cite_start]**Rayon de Capture** : 5 blocs autour de la Cellule [cite: 11]
- **Calcul Majoritaire** : `Équipe A > Équipe B + Équipe C` pour progression
- **Interruption** : Combat ou égalité stoppe immédiatement le timer
- **Indicateur Visuel** : Particules et barres de progression pour tous

**Stratégies de Capture** :
- **Rush Coordonné** : Toute l'équipe attaque simultanément
- **Contrôle de Zone** : Élimination des ennemis avant capture
- **Guérilla** : Interruptions constantes du timer ennemi

### Phase 3 : TRANSPORT (Durée Variable)

**Objectif** : Livraison des Cellules au Nexus ennemi

**Mécaniques du Porteur** :
```
Porteur de Cellule
├── Acquisition: Premier toucher après déverrouillage
├── Effets:
│   ├── Glow Effect (visible à travers les murs)
[cite_start]│   ├── Vitesse réduite (-20%) [cite: 12]
│   ├── Impossibilité de voler/courir
│   └── Particules de traçage
├── Vulnérabilité:
│   ├── Mort = Reset Cellule au centre
│   ├── Déconnexion = Reset Cellule
│   └── Sortie de l'arène = Reset Cellule
└── Livraison:
    ├── Proximité du Nexus ennemi (2 blocs)
    ├── Clic-droit sur le Nexus
    └── Animation de surcharge (3 secondes)
```

**Système de Surcharge** :
- **Nexus Health** : Chaque Nexus a 2 points de vie
- **Surcharge** : Chaque livraison inflige 1 dégât au Nexus
- [cite_start]**Protection Détruite** : Nexus à 0 PV = Vulnérable aux attaques physiques [cite: 13]
- **Régénération** : Aucune - Les dégâts sont permanents

**Stratégies de Transport** :
- **Escort Formation** : Protection rapprochée du Porteur
- **Decoy Runner** : Faux porteurs pour tromper l'ennemi
- **Speedrun** : Course directe avec sacrifice du Porteur

### Phase 4 : DESTRUCTION (Durée Variable)

**Objectif** : Destruction physique du Nexus ennemi vulnérable

**Mécaniques de Destruction** :
```
Nexus Vulnérable
├── Points de Vie: 100 HP
├── Résistance aux Dégâts: 50%
├── Dégâts par Arme:
│   ├── Épée Bois: 2 HP/coup
│   ├── Épée Pierre: 3 HP/coup
│   ├── Épée Fer: 4 HP/coup
│   ├── Épée Diamant: 6 HP/coup
[cite_start]│   └── Épée Netherite: 8 HP/coup [cite: 14]
├── Indicateur: Barre de vie visible
└── Effets: Particules d'explosion à la destruction
```

**Mécaniques Défensives** :
- **Réparation** : Impossible - Pas de régénération du Nexus
- **Protection Zone** : Aucune - Combat libre autour du Nexus
- **Dernier Stand** : Respawns normaux tant que le Nexus n'est pas détruit

**Stratégies de Destruction/Défense** :
- **Blitzkrieg** : Attaque massive et coordonnée
- **Siège Patient** : Contrôle de zone progressive
- **Défense Héroïque** : Sacrifice pour gagner du temps

### Phase 5 : ELIMINATION (Durée Variable)

**Objectif** : Élimination finale des joueurs sans Nexus

**Mécaniques d'Élimination** :
```
Équipe sans Nexus
├── Fin des Respawns: Immédiate après destruction
├── Joueurs Survivants: Deviennent des "Last Stand"
[cite_start]├── Objectif: Survie maximale pour l'honneur [cite: 15]
├── Condition de Victoire: Élimination de tous les survivants
└── Récompenses: Bonus de points pour la survie
```

**Mécaniques Spéciales** :
- **Ghost Mode** : Joueurs éliminés deviennent spectateurs
- **Overtime** : Maximum 5 minutes avant victoire automatique
- **Survival Bonus** : +10 points par 10 secondes de survie
- **Last Kill Bonus** : +50 points pour l'élimination finale

## 🏟️ Types d'Arènes et Adaptations

### Arènes Solo (1v1, 1v1v1, 1v1v1v1)

**Caractéristiques** :
- **Taille** : Compacte (50x50 blocs maximum)
- **Cellules** : 1 cellule centrale unique
- **Nexus** : 1 PV seulement (1 livraison = vulnérabilité)
- **Durée** : 5-10 minutes par manche

**Adaptations Gameplay** :
- [cite_start]**Capture** : 15 secondes par niveau (accéléré) [cite: 16]
- **Transport** : Effet Glow plus intense
- **Combat** : Focus sur le skill individuel
- **Économie** : Budget réduit mais items moins chers

### Arènes Équipes (2v2, 3v3, 4v4)

**Caractéristiques** :
- **Taille** : Moyenne (100x100 blocs)
- **Cellules** : 2-3 cellules simultanées
- **Nexus** : 2 PV standard
- **Durée** : 10-20 minutes par manche

**Adaptations Gameplay** :
- **Capture Multiple** : Plusieurs cellules actives
- **Coordination** : Communication d'équipe cruciale
- **Rôles** : Spécialisation des joueurs (Tank, DPS, Support)
- **Tactiques Avancées** : Pincer movements, distractions

### Arènes Bataille (6v6v6v6 = 24 joueurs)

**Caractéristiques** :
- **Taille** : Massive (200x200 blocs)
- **Cellules** : 4 cellules dans formation diamant
- [cite_start]**Nexus** : 3 PV renforcés [cite: 17]
- **Durée** : 20-30 minutes par manche

**Adaptations Gameplay** :
- **Alliances Temporaires** : Équipes peuvent temporairement s'allier
- **Multi-front** : Combats simultanés sur plusieurs zones
- **Ressources Limitées** : Économie plus restrictive
- **Chaos Contrôlé** : Système de respawn en vagues

## 💰 Système Économique Intégré

### Sources de Points

**Actions de Combat** :
```
Points de Combat
├── Kill: +100 points
├── Assist: +50 points (dégâts >25% dans les 10s)
├── Streak Bonus:
│   ├── 3 kills: +50 points
│   ├── 5 kills: +100 points
│   └── 7+ kills: +200 points
└── Revenge Kill: +25 points (tuer son meurtrier)
```

**Actions Objectives** :
```
Points d'Objectif
├── Capture Contribution: +25 points par niveau
[cite_start]├── Cell Delivery: +150 points [cite: 18]
├── Nexus Damage: +1 point par HP de dégât
├── Last Stand Survival: +10 points par 10s
└── Match Victory: +200 points
```

**Bonus Économiques** :
```
Bonus de Match
├── MVP (Most Valuable Player): +100 points
├── Best KDA: +50 points
├── Team Play (max assists): +50 points
├── Clutch Factor (1vX win): +100 points
└── Defeat Consolation: +100-150 points (streak dependent)
```

### Boutique Dynamique

**Catégories d'Items** :

**Armes** :
```yaml
Épées (Sharpness II):
  Pierre: 150 points
  Fer: 350 points
  Diamant: 700 points
  Netherite: 1200 points

Arcs:
  Arc Normal: 200 points
  Arc Puissance I: 600 points
  Arc Puissance II: 900 points
  [cite_start]Arc Puissance III: 1200 points [cite: 19]

Projectiles:
  Flèches x16: 100 points
  Boule de Feu: 150 points
  Perle d'Ender: 300 points
```

**Armures** :
```yaml
Améliorations (Protection II):
  Maille (Plastron+Jambières): 300 points
  Fer (Plastron+Jambières): 500 points
  Diamant (Plastron+Jambières): 800 points
  Netherite (Plastron+Jambières): 1500 points

Complet:
  Set Maille: 600 points
  Set Fer: 1000 points
  Set Diamant: 1600 points
  Set Netherite: 3000 points
```

**Utilitaires** :
```yaml
Potions:
  Vitesse I (8s): 300 points
  Force I (30s): 800 points
  Résistance I (20s): 600 points
  Régénération II (5s): 400 points

Outils:
  Cisaille Efficacité I: 400 points
  Cisaille Efficacité II: 1200 points
  [cite_start]Pioche Efficacité III: 800 points [cite: 20]

Nourriture:
  Pommes Dorées x3: 200 points
  Steaks x8: 100 points
  Chorus Fruit x4: 150 points
```

### Profils d'Achat

**Système de Quick-Buy** :
```yaml
Profil Agressif:
  Slot 1: Épée Fer (350 pts)
  Slot 2: Arc Puissance I (600 pts)
  Slot 3: Flèches x16 (100 pts)
  Slot 4: Potion Force I (800 pts)
  Total: 1850 points

Profil Défensif:
  Slot 1: Set Fer (1000 pts)
  Slot 2: Potion Résistance (600 pts)
  Slot 3: Pommes Dorées x3 (200 pts)
  Slot 4: Cisaille Efficacité I (400 pts)
  Total: 2200 points

Profil Équilibré:
  [cite_start]Slot 1: Épée Pierre (150 pts) [cite: 21]
  Slot 2: Armure Maille (300 pts)
  Slot 3: Arc Normal (200 pts)
  Slot 4: Potion Vitesse (300 pts)
  Slot 5: Flèches + Nourriture (200 pts)
  Total: 1150 points
```

## 🎭 Rôles et Stratégies d'Équipe

### Rôles Émergents

**Tank/Initiateur** :
- **Équipement** : Armure maximale, épée, potions de résistance
- **Rôle** : Initier les engagements, protéger le Porteur
- **Tactiques** : Body block, zone denial, sacrifice tactique

**DPS/Assassin** :
- **Équipement** : Épée puissante, potions de force/vitesse, arc
- **Rôle** : Éliminations rapides, hunt du Porteur ennemi
- **Tactiques** : Flanking, burst damage, target prioritization

**Support/Archer** :
- [cite_start]**Équipement** : Arc enchanté, flèches multiples, potions d'équipe [cite: 22]
- **Rôle** : Couverture à distance, support du Porteur allié
- **Tactiques** : Zone control, poke damage, emergency heals

**Courier/Runner** :
- **Équipement** : Équipement léger, potions de vitesse, nourriture
- **Rôle** : Transport des Cellules, mobilité maximale
- **Tactiques** : Path optimization, escape routes, stealth movement

### Formations Tactiques

**2v2 Formations** :
```
Formation Tank+DPS:
├── Tank: Initiation et contrôle de zone
├── DPS: Cleanup et elimination
├── Avantage: Puissance de frappe maximale
└── Faiblesse: Vulnérable aux picks

Formation Dual-DPS:
├── DPS1: Flanking et pressure
├── DPS2: Direct engagement
├── Avantage: Flexibilité et adaptabilité
└── Faiblesse: Manque de sustain

Formation Support+Carry:
├── Support: Protection et setup
├── Carry: Damage dealing principal
├── Avantage: Synergie maximale
└── Faiblesse: Dépendance au skill du Carry
```

[cite_start]**3v3 Formations** : [cite: 23]
```
Formation Triangle:
├── Point: Tank initiateur
├── Wings: DPS flankers
├── Tactique: Pression multi-directionnelle
└── Counter: Focus sur le Point

Formation Line:
├── Front: Tank absorber
├── Mid: DPS dealer
├── Back: Support archer
├── Tactique: Engagement contrôlé
└── Counter: Mobilité et flanking

Formation Flex:
├── Flex1: Tank/DPS hybrid
├── Flex2: DPS/Support hybrid  
├── Flex3: Support/Tank hybrid
├── Tactique: Adaptation constante
└── Counter: Spécialisation pure
```

## 🏆 Conditions de Victoire et Méta-jeu

### Conditions de Victoire par Phase

**Victoire Standard** :
1. Capturer une Cellule (Phase 2)
2. Livrer 2 Cellules au Nexus ennemi (Phase 3)
3. Détruire le Nexus vulnérable (Phase 4)
4. Éliminer tous les survivants (Phase 5)

**Victoires Alternatives** :
- [cite_start]**Timeout Victory** : Plus de PV sur les Nexus après 20min [cite: 24]
- **Forfait Victory** : Déconnexion de tous les adversaires
- **Technical Victory** : Violation des règles par l'équipe adverse

### Méta-jeu Évolutif

**Early Game Meta** (Minutes 0-5) :
- Focus économie et positionnement
- Éviter les trades défavorables
- Information gathering sur l'équipe adverse

**Mid Game Meta** (Minutes 5-15) :
- Contrôle des Cellules crucial
- Teamfights décisifs
- Adaptation des builds selon l'évolution

**Late Game Meta** (Minutes 15+) :
- Exécution parfaite requise
- Coordination d'équipe maximale
- Clutch plays et hero moments

### Facteurs d'Équilibrage

**Nerfs/Buffs Dynamiques** :
```yaml
Balancing System:
  Win Rate Tracking:
    - Par mode de jeu
    - Par composition d'équipe
    - [cite_start]Par items utilisés [cite: 25]
  
  Auto-Adjustments:
    - Prix boutique (+/-10%)
    - Temps de capture (+/-5s)
    - Dégâts des armes (+/-10%)
    
  Manual Overrides:
    - Nouveaux items
    - Changements de map
    - Events spéciaux
```

Cette approche garantit un méta-jeu sain et évolutif, où aucune stratégie ne devient dominante de façon permanente.
