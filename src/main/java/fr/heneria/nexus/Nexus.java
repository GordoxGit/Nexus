package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;

// Imports avec les noms de packages ORIGINAUX de Liquibase
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liqu.database.jvm.JdbcConnection;
import liquibase.logging.LogService;
import liquibase.logging.LogType;
import liquibase.logging.Logger;
import liquibase.logging.core.JavaLogService;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

public final class Nexus extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClassLoader());

        try {
            // 1. Initialiser le pool de connexions
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            // 2. Exécuter les migrations avec Liquibase en injectant manuellement le logger
            // C'est la solution définitive basée sur les recommandations des développeurs de Liquibase.
            Map<String, Object> scopeValues = new HashMap<>();
            scopeValues.put(Scope.Attr.logService.name(), new JavaLogService());
            
            Scope.child(scopeValues, () -> {
                try (Connection connection = this.dataSourceProvider.getDataSource().getConnection()) {
                    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                    Liquibase liquibase = new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(getClassLoader()), database);
                    liquibase.update();
                    getLogger().info("✅ Migrations de la base de données gérées par Liquibase.");
                } catch (Exception e) {
                    // Remonter l'exception pour qu'elle soit capturée par le bloc try/catch principal
                    throw new RuntimeException("Échec des migrations de base de données", e);
                }
            });

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
