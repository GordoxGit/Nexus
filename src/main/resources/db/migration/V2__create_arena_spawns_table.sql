-- Migration pour ajouter la table des points de spawn des arènes
CREATE TABLE arena_spawns (
    id INT AUTO_INCREMENT PRIMARY KEY,
    arena_id INT NOT NULL,
    spawn_type VARCHAR(20) NOT NULL, -- ex: 'TEAM_1', 'TEAM_2', 'SPECTATOR'
    world VARCHAR(100) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    FOREIGN KEY (arena_id) REFERENCES arenas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
