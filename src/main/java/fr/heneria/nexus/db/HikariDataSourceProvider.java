package fr.heneria.nexus.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;

/**
 * Fournit une instance unique de {@link DataSource} basée sur HikariCP.
 */
public class HikariDataSourceProvider {

    private static HikariDataSourceProvider instance;
    private HikariDataSource dataSource;

    public HikariDataSourceProvider() {
        if (instance != null) {
            throw new IllegalStateException("HikariDataSourceProvider already initialized");
        }
        instance = this;
    }

    /**
     * Initialise le pool de connexions avec des valeurs codées en dur pour le moment.
     *
     * @param plugin instance du plugin
     */
    public void init(JavaPlugin plugin) {
        if (this.dataSource != null) {
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://localhost:3306/nexus");
        config.setUsername("root");
        config.setPassword("password");
        config.setMaximumPoolSize(10);
        config.setPoolName("NexusHikariPool");
        this.dataSource = new HikariDataSource(config);
    }

    public static HikariDataSourceProvider getInstance() {
        return instance;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }
}
