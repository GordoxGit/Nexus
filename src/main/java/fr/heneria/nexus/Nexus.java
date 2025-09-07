package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
import fr.heneria.nexus.listener.PlayerConnectionListener; // NOUVEL IMPORT
import fr.heneria.nexus.player.manager.PlayerManager;     // NOUVEL IMPORT
import fr.heneria.nexus.player.repository.JdbcPlayerRepository; // NOUVEL IMPORT
import fr.heneria.nexus.player.repository.PlayerRepository;   // NOUVEL IMPORT
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;

public final class Nexus extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private ArenaManager arenaManager;
    private PlayerManager playerManager; // NOUVEAU CHAMP

    @Override
    public void onEnable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClassLoader());

        try {
            // ... (Le code de connexion et de migration reste le même)
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            try (Connection connection = this.dataSourceProvider.getDataSource().getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(getClassLoader()), database);
                liquibase.update();
                getLogger().info("✅ Migrations de la base de données gérées par Liquibase.");
            }

            // Initialisation des Repositories
            ArenaRepository arenaRepository = new JdbcArenaRepository(this.dataSourceProvider.getDataSource());
            PlayerRepository playerRepository = new JdbcPlayerRepository(this.dataSourceProvider.getDataSource(), this); // NOUVELLE LIGNE

            // Initialisation des Managers
            this.arenaManager = new ArenaManager(arenaRepository);
            this.playerManager = new PlayerManager(playerRepository); // NOUVELLE LIGNE

            // Chargement des données
            this.arenaManager.loadArenas();
            getLogger().info(this.arenaManager.getAllArenas().size() + " arène(s) chargée(s).");

            // Enregistrement des commandes et des listeners
            getCommand("nx").setExecutor(new ArenaCommand(this.arenaManager));
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this.playerManager), this); // LIGNE CRUCIALE AJOUTÉE

            getLogger().info("✅ Le plugin Nexus a été activé avec succès !");

        } catch (Exception e) {
            getLogger().severe("❌ Erreur critique lors du démarrage de Nexus :");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void onDisable() {
        // Sauvegarder tous les profils en ligne avant de fermer la connexion
        if (this.playerManager != null) {
            this.playerManager.saveAllProfiles();
        }
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.close();
        }
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
