package io.github.gordoxgit.henebrain;

import io.github.gordoxgit.henebrain.commands.ArenaCommand;
import io.github.gordoxgit.henebrain.commands.DbCommand;
import io.github.gordoxgit.henebrain.commands.HbCommand;
import io.github.gordoxgit.henebrain.arena.ArenaManager;
import io.github.gordoxgit.henebrain.database.FlywayManager;
import io.github.gordoxgit.henebrain.database.HikariDataSourceProvider;
import io.github.gordoxgit.henebrain.repository.JdbcPlayerRepository;
import io.github.gordoxgit.henebrain.repository.JdbcArenaRepository;
import io.github.gordoxgit.henebrain.repository.ArenaRepository;
import io.github.gordoxgit.henebrain.repository.PlayerRepository;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class.
 */
public final class Henebrain extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private FlywayManager flywayManager;
    private PlayerRepository playerRepository;
    private ArenaRepository arenaRepository;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataSourceProvider = new HikariDataSourceProvider(this);
        dataSourceProvider.initialize();

        String locations = getConfig().getString("database.migrations.locations", "classpath:db/migration");
        flywayManager = new FlywayManager(this, dataSourceProvider.getDataSource(), locations);
        if (getConfig().getBoolean("database.migrations.enabled", true)) {
            flywayManager.migrate();
        }

        playerRepository = new JdbcPlayerRepository(this, dataSourceProvider);
        arenaRepository = new JdbcArenaRepository(this, dataSourceProvider);
        arenaManager = new ArenaManager(arenaRepository);
        arenaManager.loadArenas();

        DbCommand dbCommand = new DbCommand(dataSourceProvider, flywayManager);
        ArenaCommand arenaCommand = new ArenaCommand(arenaRepository, arenaManager);
        getCommand("hb").setExecutor(new HbCommand(dbCommand, arenaCommand));
        getLogger().info("Henebrain a démarré avec succès !");
    }

    @Override
    public void onDisable() {
        if (dataSourceProvider != null) {
            dataSourceProvider.shutdown();
            getLogger().info("Hikari DataSource fermé.");
        }
        getLogger().info("Henebrain s'est arrêté.");
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }
}
