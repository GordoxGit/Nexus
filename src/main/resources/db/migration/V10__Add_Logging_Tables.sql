-- #####################################################################
-- ## Migration V10: Ajout des tables de journalisation (logs)        ##
-- #####################################################################

-- Table pour logger chaque transaction de Nexus Coins
CREATE TABLE IF NOT EXISTS nexus_economy_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eco_log_player_uuid (player_uuid),
    INDEX idx_eco_log_created_at (created_at)
) ENGINE=InnoDB;

-- Table pour logger chaque gain d'XP du Battle Pass
CREATE TABLE IF NOT EXISTS nexus_battle_pass_xp_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    xp_delta INT NOT NULL,
    reason VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bp_xp_log_player_uuid (player_uuid),
    INDEX idx_bp_xp_log_created_at (created_at)
) ENGINE=InnoDB;
