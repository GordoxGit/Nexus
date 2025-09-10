-- changeset gordox:12
CREATE TABLE npcs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    npc_name VARCHAR(255) NOT NULL,
    world VARCHAR(255) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    click_command VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
