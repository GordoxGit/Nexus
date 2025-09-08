-- liquibase formatted sql

-- changeset gordox:4
CREATE TABLE economy_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE,
    INDEX idx_player_transactions (player_uuid, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
