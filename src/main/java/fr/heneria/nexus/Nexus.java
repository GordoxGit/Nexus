package fr.heneria.nexus;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.repository.ArenaRepository;
import fr.heneria.nexus.arena.repository.JdbcArenaRepository;
import fr.heneria.nexus.command.ArenaCommand;
import fr.heneria.nexus.db.HikariDataSourceProvider;
import fr.heneria.nexus.libs.liquibase.Liquibase;
import fr.heneria.nexus.libs.liquibase.database.Database;
import fr.heneria.nexus.libs.liquibase.database.DatabaseFactory;
import fr.heneria.nexus.libs.liquibase.database.jvm.JdbcConnection;
import fr.heneria.nexus.libs.liquibase.resource.ClassLoaderResourceAccessor;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

public final class Nexus extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        // ======================= SOLUTION DÉFINITIVE =======================
        // On sauvegarde le ClassLoader actuel du thread.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        // On le remplace par le ClassLoader de notre plugin.
        Thread.currentThread().setContextClassLoader(this.getClassLoader());
        // =====================================================================

        try {
            // 1. Initialiser le pool de connexions
            this.dataSourceProvider = new HikariDataSourceProvider();
            this.dataSourceProvider.init(this);

            // 2. Initialiser Liquibase avec un LogService explicite
            try {
                // Initialiser le système de logging de Liquibase manuellement
                fr.heneria.nexus.libs.liquibase.logging.core.JavaLogService logService = new fr.heneria.nexus.libs.liquibase.logging.core.JavaLogService();
                logService.setPriority(fr.heneria.nexus.libs.liquibase.logging.core.JavaLogService.PRIORITY_DEFAULT);
                
                // Créer un nouveau Scope avec le LogService configuré
                Map<String, Object> scopeValues = new HashMap<>();
                scopeValues.put(fr.heneria.nexus.libs.liquibase.Scope.Attr.logService.name(), logService);
                
                // Exécuter les migrations dans le scope configuré
                fr.heneria.nexus.libs.liquibase.Scope.child(scopeValues, () -> {
                    try (Connection connection = this.dataSourceProvider.getDataSource().getConnection()) {
                        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                        Liquibase liquibase = new Liquibase("db/changelog/master.xml", new ClassLoaderResourceAccessor(getClassLoader()), database);
                        liquibase.update();
                        getLogger().info("✅ Migrations de la base de données gérées par Liquibase.");
                    }
                });
            } catch (Exception e) {
                getLogger().severe("❌ Erreur lors des migrations Liquibase :");
                e.printStackTrace();
                throw new RuntimeException("Échec des migrations de base de données", e);
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
        } finally {
            // =====================================================================
            // Il est CRUCIAL de restaurer le ClassLoader original après nos opérations
            // pour ne pas causer de problèmes à d'autres plugins.
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            // =====================================================================
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
