CREATE TABLE IF NOT EXISTS nexus_audit_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_uuid CHAR(36) NULL,
    actor_name VARCHAR(16) NULL,
    action_type VARCHAR(64) NOT NULL,
    target_uuid CHAR(36) NULL,
    target_name VARCHAR(16) NULL,
    details TEXT NULL,
    INDEX idx_audit_actor_uuid (actor_uuid),
    INDEX idx_audit_action_type (action_type),
    INDEX idx_audit_timestamp (timestamp)
) ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
