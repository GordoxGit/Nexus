package io.github.gordoxgit.henebrain.database;

import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.MigrationInfoService;

import javax.sql.DataSource;

/**
 * Wraps Flyway to handle database migrations.
 */
public class FlywayManager {

    private final JavaPlugin plugin;
    private final Flyway flyway;

    public FlywayManager(JavaPlugin plugin, DataSource dataSource, String locations) {
        this.plugin = plugin;
        this.flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(true)
                .load();
    }

    public MigrateResult migrate() {
        try {
            MigrateResult result = flyway.migrate();
            plugin.getLogger().info("Flyway a appliqué " + result.migrationsExecuted
                    + " migration(s). Version actuelle: " + result.targetSchemaVersion);
            return result;
        } catch (FlywayException e) {
            plugin.getLogger().severe("Flyway migration failed: " + e.getMessage());
            return null;
        }
    }

    public MigrationInfoService info() {
        return flyway.info();
    }
}
