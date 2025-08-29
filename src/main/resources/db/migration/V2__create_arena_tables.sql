-- Table pour stocker les informations de base de chaque arène
CREATE TABLE IF NOT EXISTS arenas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    max_players INT NOT NULL,
    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table pour stocker les points de spawn associés à chaque arène
CREATE TABLE IF NOT EXISTS arena_spawns (
    id INT AUTO_INCREMENT PRIMARY KEY,
    arena_id INT NOT NULL,
    team_id INT NOT NULL,
    spawn_number INT NOT NULL,
    world VARCHAR(255) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    CONSTRAINT fk_arena
        FOREIGN KEY (arena_id)
        REFERENCES arenas(id)
        ON DELETE CASCADE,
    UNIQUE KEY unique_spawn (arena_id, team_id, spawn_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
