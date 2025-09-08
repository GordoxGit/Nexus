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
import fr.heneria.nexus.economy.manager.EconomyManager;
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
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClassLoader());

        try {
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            try (Connection connection = this.dataSourceProvider.getDataSource().getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(getClassLoader()), database);
                liquibase.update();
                getLogger().info("✅ Migrations de la base de données gérées par Liquibase.");
            }

            // CORRECTION: L'instance du plugin n'est plus passée ici
            PlayerRepository playerRepository = new JdbcPlayerRepository(this.dataSourceProvider.getDataSource());

            this.arenaManager = new ArenaManager(new JdbcArenaRepository(this.dataSourceProvider.getDataSource()));
            this.playerManager = new PlayerManager(playerRepository);
            this.economyManager = new EconomyManager(this.playerManager);

            this.arenaManager.loadArenas();
            getLogger().info(this.arenaManager.getAllArenas().size() + " arène(s) chargée(s).");

            getCommand("nx").setExecutor(new ArenaCommand(this.arenaManager));
            // CORRECTION: L'instance du plugin (this) est maintenant passée au listener
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this.playerManager, this), this);

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
        if (this.playerManager != null) {
            // CORRECTION: Appel de la méthode qui existe réellement
            this.playerManager.unloadAllProfiles(); 
        }
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.close();
        }
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
