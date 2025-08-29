package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway;

public final class Nexus extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        // 1. Initialiser le pool de connexions à la base de données
        this.dataSourceProvider = new HikariDataSourceProvider();
        this.dataSourceProvider.init(this);

        // 2. Exécuter les migrations Flyway
        Flyway.configure()
                .dataSource(this.dataSourceProvider.getDataSource())
                .load()
                .migrate();

        // 3. Initialiser le Repository
        ArenaRepository arenaRepository = new JdbcArenaRepository(this.dataSourceProvider.getDataSource());

        // 4. Initialiser le Manager
        this.arenaManager = new ArenaManager(arenaRepository);

        // 5. Charger les arènes existantes
        this.arenaManager.loadArenas();
        getLogger().info(this.arenaManager.getAllArenas().size() + " arène(s) chargée(s).");

        // 6. Enregistrer les commandes
        getCommand("nx").setExecutor(new ArenaCommand(this.arenaManager));

        getLogger().info("Le plugin Nexus a été activé avec succès !");
    }

    @Override
    public void onDisable() {
        // Fermer le pool de connexions
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.close();
        }
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
