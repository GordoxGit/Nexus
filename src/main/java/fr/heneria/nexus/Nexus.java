package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.Statement;

public final class Nexus extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        try {
            // 1. Initialiser le pool de connexions
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            // 2. Créer les tables manuellement (plus simple que Liquibase relocalisé)
            createTablesIfNotExists();

            // 3. Initialiser le repository des arènes
            ArenaRepository arenaRepository = new JdbcArenaRepository(this.dataSourceProvider.getDataSource());

            // 4. Initialiser le manager
            this.arenaManager = new ArenaManager(arenaRepository);

            // 5. Charger les arènes
            this.arenaManager.loadArenas();
            getLogger().info(this.arenaManager.getAllArenas().size() + " arène(s) chargée(s).");

            // 6. Enregistrer les commandes
            getCommand("nx").setExecutor(new ArenaCommand(this.arenaManager));

            getLogger().info("✅ Le plugin Nexus a été activé avec succès !");

        } catch (Exception e) {
            getLogger().severe("❌ Erreur critique lors du démarrage de Nexus :");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void createTablesIfNotExists() {
        try (Connection connection = this.dataSourceProvider.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            
            // Créer la table des arènes
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS arenas (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    max_players INT NOT NULL,
                    creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Créer la table des spawns
            statement.executeUpdate("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            getLogger().info("✅ Tables de base de données créées/vérifiées avec succès !");

        } catch (Exception e) {
            getLogger().severe("❌ Erreur lors de la création des tables :");
            e.printStackTrace();
            throw new RuntimeException("Failed to create database tables", e);
        }
    }

    @Override
    public void onDisable() {
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.close();
        }
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
