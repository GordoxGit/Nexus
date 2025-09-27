-- Nexus Database Schema Version 1
-- This script defines the initial schema for the Nexus plugin persistent storage.

CREATE TABLE IF NOT EXISTS nexus_schema_version (
    installed_rank INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_players (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    last_known_name VARCHAR(16) NOT NULL,
    first_join_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_join_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_nexus_players_last_known_name (last_known_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_profiles (
    profile_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL UNIQUE,
    elo_rating INT NOT NULL DEFAULT 1000,
    total_kills INT NOT NULL DEFAULT 0,
    total_deaths INT NOT NULL DEFAULT 0,
    total_wins INT NOT NULL DEFAULT 0,
    total_losses INT NOT NULL DEFAULT 0,
    matches_played INT NOT NULL DEFAULT 0,
    KEY idx_nexus_profiles_elo_rating (elo_rating),
    CONSTRAINT fk_nexus_profiles_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_economy (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    CONSTRAINT fk_nexus_economy_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_matches (
    match_id CHAR(36) NOT NULL PRIMARY KEY,
    map_id VARCHAR(64) NOT NULL,
    arena_mode VARCHAR(32) NOT NULL,
    start_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_timestamp TIMESTAMP,
    winning_team VARCHAR(32),
    KEY idx_nexus_matches_start_timestamp (start_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_match_participants (
    match_id CHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    team VARCHAR(32) NOT NULL,
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    elo_change INT NOT NULL DEFAULT 0,
    PRIMARY KEY (match_id, player_uuid),
    CONSTRAINT fk_nexus_match_participants_match FOREIGN KEY (match_id) REFERENCES nexus_matches (match_id) ON DELETE CASCADE,
    CONSTRAINT fk_nexus_match_participants_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_player_classes (
    player_uuid CHAR(36) NOT NULL,
    class_id VARCHAR(64) NOT NULL,
    class_xp BIGINT NOT NULL DEFAULT 0,
    is_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (player_uuid, class_id),
    CONSTRAINT fk_nexus_player_classes_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_player_cosmetics (
    player_uuid CHAR(36) NOT NULL,
    cosmetic_id VARCHAR(128) NOT NULL,
    cosmetic_type VARCHAR(64) NOT NULL,
    unlocked_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, cosmetic_id),
    CONSTRAINT fk_nexus_player_cosmetics_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_player_quests (
    player_uuid CHAR(36) NOT NULL,
    quest_id VARCHAR(128) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, quest_id),
    CONSTRAINT fk_nexus_player_quests_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_player_achievements (
    player_uuid CHAR(36) NOT NULL,
    achievement_id VARCHAR(128) NOT NULL,
    unlocked_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, achievement_id),
    CONSTRAINT fk_nexus_player_achievements_player FOREIGN KEY (player_uuid) REFERENCES nexus_players (player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
