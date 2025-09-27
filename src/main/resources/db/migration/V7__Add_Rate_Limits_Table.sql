CREATE TABLE IF NOT EXISTS nexus_rate_limits (
    player_uuid CHAR(36) NOT NULL,
    action_key VARCHAR(128) NOT NULL,
    last_executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, action_key)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
