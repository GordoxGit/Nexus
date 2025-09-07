package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
import fr.heneria.nexus.listener.PlayerConnectionListener;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.repository.JdbcPlayerRepository;
import fr.heneria.nexus.player.repository.PlayerRepository;

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
    private PlayerManager playerManager;

    @Override
    public void onEnable() {
        // Définir le ClassLoader du thread est une bonne pratique pour assurer la compatibilité
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClassLoader());

        try {
            // 1. Initialiser le pool de connexions
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            // 2. Exécuter les migrations avec Liquibase
            try (Connection connection = this.dataSourceProvider.getDataSource().getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(getClassLoader()), database);
                liquibase.update();
                getLogger().info("✅ Migrations de la base de données gérées par Liquibase.");
            }

            // 3. Initialiser les repositories
            ArenaRepository arenaRepository = new JdbcArenaRepository(this.dataSourceProvider.getDataSource());
            PlayerRepository playerRepository = new JdbcPlayerRepository(this.dataSourceProvider.getDataSource());

            // 4. Initialiser les managers
            this.arenaManager = new ArenaManager(arenaRepository);
            this.playerManager = new PlayerManager(playerRepository);

            // 5. Charger les arènes
            this.arenaManager.loadArenas();
            getLogger().info(this.arenaManager.getAllArenas().size() + " arène(s) chargée(s).");

            // 6. Enregistrer les commandes
            getCommand("nx").setExecutor(new ArenaCommand(this.arenaManager));

            // 7. Enregistrer les listeners
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this.playerManager, this), this);

            getLogger().info("✅ Le plugin Nexus a été activé avec succès !");

        } catch (Exception e) {
            getLogger().severe("❌ Erreur critique lors du démarrage de Nexus :");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        } finally {
            // Restaurer le ClassLoader original
            Thread.currentThread().setContextClassLoader(originalClassLoader);
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
