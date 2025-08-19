package io.github.gordoxgit.henebrain.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides a pooled {@link DataSource} using HikariCP.
 */
public class HikariDataSourceProvider {

    private final JavaPlugin plugin;
    private final ConfigurationSection dbConfig;
    private HikariDataSource dataSource;

    public HikariDataSourceProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbConfig = plugin.getConfig().getConfigurationSection("database");
    }

    /**
     * Initializes the HikariCP data source using configuration values.
     */
    public synchronized void initialize() {
        if (dataSource != null) {
            return;
        }

        HikariConfig config = new HikariConfig();
        String host = dbConfig.getString("host");
        int port = dbConfig.getInt("port");
        String database = dbConfig.getString("database");
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true";
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbConfig.getString("username"));
        config.setPassword(dbConfig.getString("password"));

        ConfigurationSection pool = dbConfig.getConfigurationSection("pool");
        if (pool != null) {
            config.setMaximumPoolSize(pool.getInt("maximumPoolSize", 10));
            config.setMinimumIdle(pool.getInt("minimumIdle", 2));
            config.setConnectionTimeout(pool.getLong("connectionTimeout", 30000));
            config.setIdleTimeout(pool.getLong("idleTimeout", 600000));
            config.setMaxLifetime(pool.getLong("maxLifetime", 1800000));
            ConfigurationSection dsp = pool.getConfigurationSection("dataSourceProperties");
            if (dsp != null) {
                for (String key : dsp.getKeys(false)) {
                    config.addDataSourceProperty(key, dsp.get(key));
                }
            }
        }

        dataSource = new HikariDataSource(config);

        // Test connection with retry policy
        ConfigurationSection retry = dbConfig.getConfigurationSection("retryPolicy");
        int retries = retry != null ? retry.getInt("retries", 3) : 3;
        long backoff = retry != null ? retry.getLong("backoffMs", 250) : 250L;
        for (int attempt = 1; attempt <= retries; attempt++) {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("SELECT 1");
                plugin.getLogger().info("Hikari pool initialized (max=" + config.getMaximumPoolSize() + ")");
                return;
            } catch (SQLException e) {
                plugin.getLogger().warning("DB connection failed (attempt " + attempt + "/" + retries + ")");
                if (attempt == retries) {
                    plugin.getLogger().severe("Cannot establish database connection: " + e.getMessage());
                } else {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    public DataSource getDataSource() {
        if (dataSource == null) {
            initialize();
        }
        return dataSource;
    }

    /**
     * Closes the underlying data source.
     */
    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
