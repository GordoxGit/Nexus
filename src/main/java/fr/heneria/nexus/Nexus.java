package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
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

    @Override
    public void onEnable() {
        try {
            // =====================================================================
            // CETTE LIGNE EST LA SOLUTION. ELLE DOIT ÊTRE LA TOUTE PREMIÈRE.
            // =====================================================================
            System.setProperty("liquibase.hub.logService", "liquibase.logging.core.JavaLogService");

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

    @Override
    public void onDisable() {
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.close();
        }
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
