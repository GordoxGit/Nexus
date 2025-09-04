# 🗄️ Database Schema - Nexus Plugin

## Vue d'Ensemble

[cite_start]Le plugin Nexus utilise MariaDB comme système de base de données principal avec HikariCP pour le pooling de connexions et Flyway pour la gestion des migrations. [cite: 721] [cite_start]Cette documentation décrit le schéma complet de la base de données. [cite: 722]

## 📊 Diagramme ERD

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     arenas      │    │  arena_spawns   │    │    matches      │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ id (PK)         │───┬│ arena_id (FK)   │    │ id (PK)         │
│ name (UNIQUE)   │   └│ team_id         │    │ arena_name      │
│ game_mode       │    │ spawn_number    │    │ game_mode       │
│ max_players     │    │ world           │    │ match_status    │
│ min_players     │    │ x, y, z         │    │ total_players   │
│ world_name      │    │ yaw, pitch      │    │ duration_secs   │
│ region_data     │    └─────────────────┘    │ winner_team_id  │
│ config_data     │                           │ match_data      │
│ created_at      │                           │ started_at      │
│ updated_at      │                           │ ended_at        │
│ is_active       │                           └─────────────────┘
└─────────────────┘                                   │
                                                      │
┌─────────────────┐    ┌─────────────────┐            │
│ player_profiles │    │match_participants│───────────┘
├─────────────────┤    ├─────────────────┤
│ player_uuid(PK) │───┬│ player_uuid(FK) │
│ player_name     │   └│ match_id (FK)   │
│ display_name    │    │ team_id         │
│ elo_rating      │    │ kills           │
│ current_rank    │    │ deaths          │
│ total_points    │    │ assists         │
│ settings_data   │    │ damage_dealt    │
│ created_at      │    │ points_earned   │
│ updated_at      │    │ elo_change      │
│ last_seen       │    │ left_early      │
│ is_active       │    └─────────────────┘
└─────────────────┘
         │
         │    ┌─────────────────┐    ┌─────────────────┐
         │    │player_statistics│    │economy_transactions
         │    ├─────────────────┤    ├─────────────────┤
         └───→│ player_uuid(FK) │    │ player_uuid(FK) │
              │ game_mode       │    │ transaction_type│
              │ total_matches   │    │ amount          │
              │ wins/losses     │    │ balance_before  │
              │ kills/deaths    │    │ balance_after   │
              │ damage_stats    │    │ reason          │
              │ objective_stats │    │ created_at      │
              └─────────────────┘    └─────────────────┘

┌─────────────────┐    ┌─────────────────┐
│    seasons      │    │ season_rewards  │
├─────────────────┤    ├─────────────────┤
│ id (PK)         │───┬│ season_id (FK)  │
│ season_name     │   └│ player_uuid(FK) │
│ season_number   │    │ final_rank      │
│ start_date      │    │ final_elo       │
│ end_date        │    │ matches_played  │
│ is_active       │    │ rewards_claimed │
│ rewards_data    │    │ rewards_data    │
└─────────────────┘    └─────────────────┘
```

## 📋 Tables Détaillées

### Table `arenas`
[cite_start]Stocke toutes les informations des arènes de jeu. [cite: 734]
```sql
CREATE TABLE arenas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    game_mode ENUM(
        'SOLO_1V1', 
        'SOLO_1V1V1', 
        'SOLO_1V1V1V1',
        'TEAM_2V2', 
        'TEAM_3V3',
        'TEAM_4V4', 
        'BATTLE_6V6V6V6'
    ) NOT NULL,
    max_players INT NOT NULL,
    min_players INT NOT NULL,
    world_name VARCHAR(255) NOT NULL,
    region_data JSON COMMENT 'Données de région WorldGuard (optionnel)',
    config_data JSON COMMENT 'Configuration spécifique à l''arène',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_game_mode (game_mode),
    INDEX idx_active (is_active),
    INDEX idx_world (world_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Champs JSON** :
- `region_data` : `{"min_x": -50, "min_y": 0, "min_z": -50, "max_x": 50, "max_y": 100, "max_z": 50}`
- `config_data` : `{"match_timeout": 1200, "respawn_delay": 5, "custom_rules": {...}}`

### Table `arena_spawns`
Points de spawn par équipe pour chaque arène.
```sql
CREATE TABLE arena_spawns (
    id INT AUTO_INCREMENT PRIMARY KEY,
    arena_id INT NOT NULL,
    team_id INT NOT NULL COMMENT 'ID de l''équipe (1, 2, 3, 4)',
    spawn_number INT NOT NULL COMMENT 'Numéro du spawn dans l''équipe',
    world VARCHAR(255) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    
    CONSTRAINT fk_arena_spawns_arena
        FOREIGN KEY (arena_id) REFERENCES arenas(id) 
        ON DELETE CASCADE,
    
    UNIQUE KEY unique_spawn (arena_id, team_id, spawn_number),
    INDEX idx_arena_team (arena_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Table `player_profiles`
[cite_start]Profils des joueurs avec informations de base. [cite: 740]

```sql
CREATE TABLE player_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL UNIQUE,
    player_name VARCHAR(16) NOT NULL,
    display_name VARCHAR(32),
    elo_rating INT DEFAULT 1000,
    current_rank ENUM(
        'UNRANKED', 'BRONZE_I', 'BRONZE_II', 'BRONZE_III',
        'SILVER_I', 'SILVER_II', 'SILVER_III',
        'GOLD_I', 'GOLD_II', 'GOLD_III',
        'PLATINUM_I', 'PLATINUM_II', 'PLATINUM_III',
        'DIAMOND_I', 'DIAMOND_II', 'DIAMOND_III',
        'MASTER', 'GRANDMASTER', 'CHAMPION'
    ) DEFAULT 'UNRANKED',
    total_points INT DEFAULT 0,
    settings_data JSON COMMENT 'Préférences utilisateur',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_elo_rating (elo_rating DESC),
    INDEX idx_rank (current_rank),
    INDEX idx_points (total_points DESC),
    INDEX idx_last_seen (last_seen DESC),
    INDEX idx_name (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Structure `settings_data`** :
```json
{
    "auto_respawn": true,
    "team_chat_enabled": true,
    "death_messages": true,
    "statistics_tracking": true,
    "preferred_kit": "balanced",
    "ui_scale": "normal",
    "sound_enabled": true,
    "particle_effects": true
}
```

### Table `player_statistics`
[cite_start]Statistiques détaillées par mode de jeu. [cite: 744]
```sql
CREATE TABLE player_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    game_mode ENUM(
        'SOLO_1V1', 'SOLO_1V1V1', 'SOLO_1V1V1V1',
        'TEAM_2V2', 'TEAM_3V3', 'TEAM_4V4', 'BATTLE_6V6V6V6'
    ) NOT NULL,
    
    -- Statistiques de match
    total_matches INT DEFAULT 0,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    draws INT DEFAULT 0,
    
    -- Statistiques de combat
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    assists INT DEFAULT 0,
    damage_dealt DOUBLE DEFAULT 0.0,
    damage_received DOUBLE DEFAULT 0.0,
    best_killstreak INT DEFAULT 0,
    
    -- Statistiques d'objectif
    cells_captured INT DEFAULT 0,
    cells_delivered INT DEFAULT 0,
    nexus_destroyed INT DEFAULT 0,
    nexus_defended INT DEFAULT 0,
    
    -- Statistiques de temps
    total_playtime_seconds BIGINT DEFAULT 0,
    average_match_duration_seconds INT DEFAULT 0,
    
    -- Statistiques économiques
    total_points_earned INT DEFAULT 0,
    total_points_spent INT DEFAULT 0,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_player_stats_profile 
        FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) 
        ON DELETE CASCADE,
    
    UNIQUE KEY unique_player_mode (player_uuid, game_mode),
    INDEX idx_wins (wins DESC),
    INDEX idx_kdr ((kills / GREATEST(deaths, 1)) DESC),
    INDEX idx_winrate ((wins / GREATEST(total_matches, 1)) DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Table `matches`
[cite_start]Historique des matches joués. [cite: 748]

```sql
CREATE TABLE matches (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID du match',
    arena_name VARCHAR(255) NOT NULL,
    game_mode ENUM(
        'SOLO_1V1', 'SOLO_1V1V1', 'SOLO_1V1V1V1',
        'TEAM_2V2', 'TEAM_3V3', 'TEAM_4V4', 'BATTLE_6V6V6V6'
    ) NOT NULL,
    match_status ENUM(
        'WAITING', 'STARTING', 'IN_PROGRESS', 'PAUSED', 
        'FINISHED', 'CANCELLED', 'ABANDONED'
    ) NOT NULL DEFAULT 'WAITING',
    total_players INT NOT NULL,
    duration_seconds INT DEFAULT 0,
    winner_team_id INT COMMENT 'ID de l''équipe gagnante, NULL si draw',
    match_data JSON COMMENT 'Données détaillées du match',
    server_name VARCHAR(100) COMMENT 'Serveur où s''est déroulé le match',
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_status (match_status),
    INDEX idx_arena (arena_name),
    INDEX idx_game_mode (game_mode),
    INDEX idx_started_at (started_at DESC),
    INDEX idx_duration (duration_seconds),
    CONSTRAINT fk_matches_arena 
        FOREIGN KEY (arena_name) REFERENCES arenas(name)
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Structure `match_data`** :
```json
{
    "teams": [
        {
            "team_id": 1,
            "team_name": "Red Team",
            "final_score": 3,
            "cells_captured": 5,
            "nexus_health": 0
        }
    ],
    "phases": [
        {
            "phase": "CAPTURE",
            "duration_seconds": 120,
            "events": []
        }
    ],
    "final_stats": {
        "total_kills": 25,
        "total_damage": 1250.5,
        "mvp_player": "uuid-here"
    }
}
```

### Table `match_participants`
[cite_start]Participation et statistiques individuelles par match. [cite: 753]

```sql
CREATE TABLE match_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id VARCHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    team_id INT NOT NULL,
    
    -- Statistiques de combat
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    assists INT DEFAULT 0,
    damage_dealt DOUBLE DEFAULT 0.0,
    damage_received DOUBLE DEFAULT 0.0,
    best_killstreak INT DEFAULT 0,
    
    -- Statistiques d'objectif
    cells_captured INT DEFAULT 0,
    cells_delivered INT DEFAULT 0,
    nexus_damage_dealt INT DEFAULT 0,
    
    -- Économie et progression
    points_earned INT DEFAULT 0,
    elo_before INT DEFAULT 0,
    elo_after INT DEFAULT 0,
    elo_change INT DEFAULT 0,
    
    -- Méta-données
    duration_seconds INT DEFAULT 0,
    left_early BOOLEAN DEFAULT FALSE,
    disconnect_reason VARCHAR(100),
    
    -- Détails de performance
    accuracy_percentage DECIMAL(5,2) COMMENT 'Précision des attaques',
    items_purchased JSON COMMENT 'Items achetés pendant le match',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_match_participants_match 
        FOREIGN KEY (match_id) REFERENCES matches(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_match_participants_player 
        FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) 
        ON DELETE CASCADE,
    
    UNIQUE KEY unique_match_player (match_id, player_uuid),
    INDEX idx_player_matches (player_uuid, created_at DESC),
    INDEX idx_match_team (match_id, team_id),
    INDEX idx_performance (kills DESC, deaths ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Table `economy_transactions`
[cite_start]Historique des transactions économiques. [cite: 757]

```sql
CREATE TABLE economy_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    transaction_type ENUM(
        'EARN_KILL', 'EARN_ASSIST', 'EARN_VICTORY', 'EARN_PARTICIPATION',
        'EARN_STREAK', 'EARN_OBJECTIVE', 'EARN_MVP', 'EARN_SEASONAL',
        'SPEND_SHOP', 'SPEND_UPGRADE', 'SPEND_COSMETIC',
        'ADMIN_ADD', 'ADMIN_REMOVE', 'ADMIN_RESET',
        'TRANSFER_SEND', 'TRANSFER_RECEIVE'
    ) NOT NULL,
    amount INT NOT NULL COMMENT 'Positif pour gains, négatif pour dépenses',
    balance_before INT NOT NULL,
    balance_after INT NOT NULL,
    reason VARCHAR(255),
    related_match_id VARCHAR(36) COMMENT 'Match associé si applicable',
    metadata JSON COMMENT 'Données supplémentaires de la transaction',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_economy_player 
        FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) 
        ON DELETE CASCADE,
    CONSTRAINT fk_economy_match
        FOREIGN KEY (related_match_id) REFERENCES matches(id)
        ON DELETE SET NULL,
    
    INDEX idx_player_transactions (player_uuid, created_at DESC),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_amount (amount DESC),
    INDEX idx_date (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Table `seasons`
[cite_start]Gestion des saisons compétitives. [cite: 760]

```sql
CREATE TABLE seasons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    season_name VARCHAR(100) NOT NULL,
    season_number INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN DEFAULT FALSE,
    rewards_data JSON COMMENT 'Configuration des récompenses de saison',
    rules_data JSON COMMENT 'Règles spécifiques de la saison',
    statistics JSON COMMENT 'Statistiques de la saison',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_active_season (is_active, start_date),
    INDEX idx_season_number (season_number DESC),
    INDEX idx_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Structure `rewards_data`** :
```json
{
    "rank_rewards": {
        "CHAMPION": {"cosmetics": ["golden_crown"], "points": 5000},
        "GRANDMASTER": {"cosmetics": ["silver_crown"], "points": 3000},
        "MASTER": {"cosmetics": ["bronze_crown"], "points": 2000}
    },
    "participation_rewards": {
        "min_matches": 10,
        "rewards": {"cosmetics": ["season_badge"], "points": 500}
    },
    "milestone_rewards": [
        {"matches_played": 50, "reward": {"points": 1000}},
        {"wins": 25, "reward": {"cosmetics": ["victory_banner"]}}
    ]
}
```

### Table `season_rewards`
[cite_start]Récompenses distribuées par saison. [cite: 764]
```sql
CREATE TABLE season_rewards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    season_id INT NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    final_rank ENUM(
        'UNRANKED', 'BRONZE_I', 'BRONZE_II', 'BRONZE_III',
        'SILVER_I', 'SILVER_II', 'SILVER_III',
        'GOLD_I', 'GOLD_II', 'GOLD_III',
        'PLATINUM_I', 'PLATINUM_II', 'PLATINUM_III',
        'DIAMOND_I', 'DIAMOND_II', 'DIAMOND_III',
        'MASTER', 'GRANDMASTER', 'CHAMPION'
    ) NOT NULL,
    final_elo INT NOT NULL,
    matches_played INT NOT NULL,
    peak_elo INT COMMENT 'ELO le plus élevé atteint pendant la saison',
    rewards_claimed BOOLEAN DEFAULT FALSE,
    rewards_data JSON COMMENT 'Récompenses accordées',
    claimed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_season_rewards_season 
        FOREIGN KEY (season_id) REFERENCES seasons(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_season_rewards_player 
        FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) 
        ON DELETE CASCADE,
    
    UNIQUE KEY unique_season_player (season_id, player_uuid),
    INDEX idx_season_rank (season_id, final_rank),
    INDEX idx_unclaimed_rewards (rewards_claimed, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 🔧 Migrations Flyway

### V1__initial_schema.sql
```sql
-- Migration initiale avec les tables de base
CREATE TABLE arenas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    max_players INT NOT NULL,
    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Schema de base pour le système d'arènes
-- (Version simplifiée pour le démarrage)
```

### V2__create_arena_tables.sql
```sql
-- Extension du système d'arènes avec spawns
CREATE TABLE arena_spawns (
    id INT AUTO_INCREMENT PRIMARY KEY,
    arena_id INT NOT NULL,
    team_id INT NOT NULL,
    spawn_number INT NOT NULL,
    world VARCHAR(255) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    CONSTRAINT fk_arena
        FOREIGN KEY (arena_id) REFERENCES arenas(id)
        ON DELETE CASCADE,
    UNIQUE KEY unique_spawn (arena_id, team_id, spawn_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### V3__add_game_modes.sql
```sql
-- Ajout du support des modes de jeu
ALTER TABLE arenas 
ADD COLUMN game_mode ENUM(
    'SOLO_1V1', 'SOLO_1V1V1', 'SOLO_1V1V1V1',
    'TEAM_2V2', 'TEAM_3V3', 'TEAM_4V4', 'BATTLE_6V6V6V6'
[cite_start]) NOT NULL DEFAULT 'TEAM_2V2' AFTER name; [cite: 771]
ALTER TABLE arenas 
[cite_start]ADD COLUMN min_players INT NOT NULL DEFAULT 2 AFTER max_players; [cite: 772]
ALTER TABLE arenas 
[cite_start]ADD COLUMN world_name VARCHAR(255) NOT NULL DEFAULT 'world' AFTER min_players; [cite: 773]
```

### V4__create_player_system.sql
```sql
-- Création du système de joueurs complet
CREATE TABLE player_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL UNIQUE,
    player_name VARCHAR(16) NOT NULL,
    display_name VARCHAR(32),
    elo_rating INT DEFAULT 1000,
    current_rank ENUM(
        'UNRANKED', 'BRONZE_I', 'BRONZE_II', 'BRONZE_III',
        'SILVER_I', 'SILVER_II', 'SILVER_III',
        'GOLD_I', 'GOLD_II', 'GOLD_III',
        'PLATINUM_I', 'PLATINUM_II', 'PLATINUM_III',
        'DIAMOND_I', 'DIAMOND_II', 'DIAMOND_III',
        'MASTER', 'GRANDMASTER', 'CHAMPION'
    ) DEFAULT 'UNRANKED',
    total_points INT DEFAULT 0,
    settings_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Index pour optimiser les requêtes
[cite_start]CREATE INDEX idx_elo_rating ON player_profiles(elo_rating DESC); [cite: 775]
[cite_start]CREATE INDEX idx_rank ON player_profiles(current_rank); [cite: 775]
[cite_start]CREATE INDEX idx_points ON player_profiles(total_points DESC); [cite: 776]
```

### V5__create_match_system.sql
```sql
-- Système de matches et statistiques
CREATE TABLE matches (
    id VARCHAR(36) PRIMARY KEY,
    arena_name VARCHAR(255) NOT NULL,
    game_mode ENUM(
        'SOLO_1V1', 'SOLO_1V1V1', 'SOLO_1V1V1V1',
        'TEAM_2V2', 'TEAM_3V3', 'TEAM_4V4', 'BATTLE_6V6V6V6'
    ) NOT NULL,
    match_status ENUM('WAITING', 'STARTING', 'IN_PROGRESS', 'FINISHED', 'CANCELLED') NOT NULL,
    total_players INT NOT NULL,
    duration_seconds INT DEFAULT 0,
    winner_team_id INT,
    match_data JSON,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
[cite_start]) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; [cite: 777]
CREATE TABLE match_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id VARCHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    team_id INT NOT NULL,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    assists INT DEFAULT 0,
    damage_dealt DOUBLE DEFAULT 0.0,
    damage_received DOUBLE DEFAULT 0.0,
    points_earned INT DEFAULT 0,
    elo_change INT DEFAULT 0,
    left_early BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE
[cite_start]) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; [cite: 779]
```

## 📊 Requêtes d'Exemple

### Classement Global
```sql
-- Top 10 des joueurs par ELO
SELECT 
    player_name,
    display_name,
    current_rank,
    elo_rating,
    total_points
FROM player_profiles 
WHERE is_active = TRUE
ORDER BY elo_rating DESC 
LIMIT 10;
```

### Statistiques de Joueur
```sql
-- Statistiques complètes d'un joueur
SELECT 
    pp.player_name,
    pp.elo_rating,
    pp.current_rank,
    ps.total_matches,
    ps.wins,
    ps.losses,
    ROUND(ps.wins / GREATEST(ps.total_matches, 1) * 100, 2) as win_rate,
    ps.kills,
    ps.deaths,
    ROUND(ps.kills / GREATEST(ps.deaths, 1), 2) as kdr,
    ps.cells_delivered,
    ps.nexus_destroyed
FROM player_profiles pp
JOIN player_statistics ps ON pp.player_uuid = ps.player_uuid
WHERE pp.player_uuid = ?
[cite_start]AND ps.game_mode = 'TEAM_2V2'; [cite: 782]
```

### Historique des Matches
```sql
-- Derniers matches d'un joueur
SELECT 
    m.id,
    m.arena_name,
    m.game_mode,
    m.duration_seconds,
    m.winner_team_id,
    mp.team_id,
    mp.kills,
    mp.deaths,
    mp.assists,
    mp.points_earned,
    mp.elo_change,
    m.started_at
FROM matches m
JOIN match_participants mp ON m.id = mp.match_id
WHERE mp.player_uuid = ?
ORDER BY m.started_at DESC
[cite_start]LIMIT 20; [cite: 783]
```

### Performance d'Arène
```sql
-- Statistiques d'utilisation des arènes
SELECT 
    a.name,
    a.game_mode,
    COUNT(m.id) as total_matches,
    AVG(m.duration_seconds) as avg_duration,
    COUNT(DISTINCT mp.player_uuid) as unique_players
FROM arenas a
LEFT JOIN matches m ON a.name = m.arena_name
LEFT JOIN match_participants mp ON m.id = mp.match_id
WHERE m.match_status = 'FINISHED'
GROUP BY a.id, a.name, a.game_mode
ORDER BY total_matches DESC;
```

## 🔧 Configuration HikariCP

### Paramètres Optimisés pour Production
```java
public class DatabaseConfig {
    
    private static HikariConfig createConfig() {
        HikariConfig config = new HikariConfig();
        // Connexion de base
        config.setJdbcUrl("jdbc:mariadb://localhost:3306/nexus");
        config.setUsername("nexus_user");
        config.setPassword("strong_password_here");
        // Pool settings
        config.setMaximumPoolSize(15);
        // Max connexions
        config.setMinimumIdle(5);
        // Connexions toujours prêtes
        config.setConnectionTimeout(20000);
        // 20s timeout
        config.setIdleTimeout(600000);
        // 10min idle
        config.setMaxLifetime(1800000);
        // 30min max life
        config.setLeakDetectionThreshold(60000);
        // 1min leak detection
        
        // Optimisations MariaDB
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "utf8mb4");
        config.addDataSourceProperty("serverTimezone", "UTC");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        // Pool naming
        config.setPoolName("NexusHikariPool");
        
        return config;
    }
}
```

## 🚀 Performance et Optimisation

### Index Recommandés
```sql
-- Index composites pour requêtes fréquentes
CREATE INDEX idx_player_stats_composite ON player_statistics(player_uuid, game_mode, wins DESC);
[cite_start]CREATE INDEX idx_match_participants_performance ON match_participants(player_uuid, kills DESC, deaths ASC); [cite: 796]
CREATE INDEX idx_transactions_recent ON economy_transactions(player_uuid, created_at DESC);
[cite_start]CREATE INDEX idx_matches_recent_by_arena ON matches(arena_name, started_at DESC); [cite: 797]

-- Index pour les classements
CREATE INDEX idx_leaderboard_global ON player_profiles(elo_rating DESC, is_active);
[cite_start]CREATE INDEX idx_leaderboard_by_rank ON player_profiles(current_rank, elo_rating DESC); [cite: 798]
```

### Partitioning Strategy (Pour Grande Échelle)
```sql
-- Partitioning de la table des matches par mois
ALTER TABLE matches PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202501 VALUES LESS THAN (202502),
    PARTITION p202502 VALUES LESS THAN (202503),
    PARTITION p202503 VALUES LESS THAN (202504),
    -- etc...
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
-- Partitioning des transactions par mois
ALTER TABLE economy_transactions PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202501 VALUES LESS THAN (202502),
    PARTITION p202502 VALUES LESS THAN (202503),
    -- etc...
);
```

### Maintenance et Nettoyage
```sql
-- Procédure de nettoyage mensuel
DELIMITER //
CREATE PROCEDURE CleanupOldData()
BEGIN
    -- Supprimer les matches anciens (> 6 mois)
    DELETE FROM matches 
    WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
    AND match_status IN ('FINISHED', 'CANCELLED');
    -- Supprimer les transactions anciennes (> 1 an)
    DELETE FROM economy_transactions 
    WHERE created_at < DATE_SUB(NOW(), INTERVAL 1 YEAR);
    -- Marquer comme inactifs les joueurs non vus depuis 3 mois
    UPDATE player_profiles 
    SET is_active = FALSE 
    WHERE last_seen < DATE_SUB(NOW(), INTERVAL 3 MONTH);
END //
DELIMITER ;

-- Planifier le nettoyage (à exécuter via cron)
-- 0 2 1 * * /usr/bin/mysql -u nexus_user -p nexus -e "CALL CleanupOldData();"
```

Ce schéma de base de données est conçu pour supporter des milliers de joueurs et des dizaines de milliers de matches, tout en maintenant des performances optimales grâce aux index appropriés et aux stratégies de partitioning.
