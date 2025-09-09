-- liquibase formatted sql
-- changeset gordox:6
CREATE TABLE matches (
    id VARCHAR(36) PRIMARY KEY,
    arena_id INT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    winning_team_id INT,
    FOREIGN KEY (arena_id) REFERENCES arenas(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- changeset gordox:7
CREATE TABLE match_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id VARCHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    team_id INT NOT NULL,
    kills INT DEFAULT 0 NOT NULL,
    deaths INT DEFAULT 0 NOT NULL,
    assists INT DEFAULT 0 NOT NULL, -- Pour le futur
    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
