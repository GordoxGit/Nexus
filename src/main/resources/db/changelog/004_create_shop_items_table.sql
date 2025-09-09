-- liquibase formatted sql
-- changeset gordox:5
CREATE TABLE shop_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_category VARCHAR(32) NOT NULL,
    material VARCHAR(64) NOT NULL,
    price INT DEFAULT 0 NOT NULL,
    is_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    display_name VARCHAR(128),
    lore_lines TEXT,
    UNIQUE KEY uk_category_material (item_category, material)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
