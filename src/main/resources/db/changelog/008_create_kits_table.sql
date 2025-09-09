-- liquibase formatted sql
-- changeset gordox:11
CREATE TABLE kits (
    id INT AUTO_INCREMENT PRIMARY KEY,
    kit_name VARCHAR(50) NOT NULL UNIQUE,
    -- stocke l'inventaire complet (items + armure) sérialisé en Base64 ou JSON
    inventory_content TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
