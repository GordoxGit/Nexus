-- liquibase formatted sql
-- changeset gordox:9
CREATE TABLE player_sanctions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    sanction_type VARCHAR(32) NOT NULL, -- Ex: 'LEAVE_PENALTY'
    expiration_time TIMESTAMP NULL, -- NULL pour une sanction permanente
    sanction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    reason VARCHAR(255),

    INDEX idx_active_sanctions (player_uuid, is_active),
    FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- changeset gordox:10
-- Ajoute une colonne pour suivre le niveau d'infraction du joueur
ALTER TABLE player_profiles ADD COLUMN leaver_level INT DEFAULT 0 NOT NULL;
