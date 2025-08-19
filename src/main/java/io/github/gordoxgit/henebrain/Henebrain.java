package io.github.gordoxgit.henebrain;

import io.github.gordoxgit.henebrain.commands.DbCommand;
import io.github.gordoxgit.henebrain.database.FlywayManager;
import io.github.gordoxgit.henebrain.database.HikariDataSourceProvider;
import io.github.gordoxgit.henebrain.repository.JdbcPlayerRepository;
import io.github.gordoxgit.henebrain.repository.PlayerRepository;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class.
 */
public final class Henebrain extends JavaPlugin {

    private HikariDataSourceProvider dataSourceProvider;
    private FlywayManager flywayManager;
    private PlayerRepository playerRepository;

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

        getCommand("hb").setExecutor(new DbCommand(dataSourceProvider, flywayManager));
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
