CREATE TABLE IF NOT EXISTS nexus_rewards_claimed (
    claim_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    reward_key VARCHAR(255) NOT NULL,
    claimed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_player_reward (player_uuid, reward_key)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
