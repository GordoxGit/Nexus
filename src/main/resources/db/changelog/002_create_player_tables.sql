-- liquibase formatted sql

-- changeset gordox:3
CREATE TABLE player_profiles (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    elo_rating INT DEFAULT 1000 NOT NULL,
    current_rank VARCHAR(32) DEFAULT 'UNRANKED' NOT NULL,
    total_points BIGINT DEFAULT 0 NOT NULL,
    first_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_player_name (player_name),
    INDEX idx_elo_rating (elo_rating DESC),
    INDEX idx_total_points (total_points DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
