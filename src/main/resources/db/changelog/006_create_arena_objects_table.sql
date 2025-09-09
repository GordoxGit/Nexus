-- liquibase formatted sql
-- changeset gordox:8
CREATE TABLE arena_game_objects (
    id INT AUTO_INCREMENT PRIMARY KEY,
    arena_id INT NOT NULL,
    object_type VARCHAR(32) NOT NULL, -- Ex: 'ENERGY_CELL', 'NEXUS_CORE'
    object_index INT DEFAULT 1 NOT NULL, -- Pour les arènes avec plusieurs objets du même type
    world VARCHAR(255) NOT NULL,
    x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
    yaw FLOAT, pitch FLOAT,
    UNIQUE KEY uk_arena_object (arena_id, object_type, object_index),
    FOREIGN KEY (arena_id) REFERENCES arenas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
