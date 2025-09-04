package fr.heneria.nexus.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
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
     * Initialise le pool de connexions à partir du config.yml du plugin.
     *
     * @param plugin instance du plugin
     */
    public void init(JavaPlugin plugin) {
        if (this.dataSource != null) {
            return;
        }

        // Créer le config.yml par défaut si inexistant
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();

        // Configuration de base de données depuis config.yml
        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "nexus");
        String username = config.getString("database.username", "nexus_user");
        String password = config.getString("database.password", "your_password_here");

        // Construction de l'URL JDBC
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        // IMPORTANT : Laisser HikariCP détecter automatiquement le driver MariaDB
        // Ne pas spécifier explicitement le driver - HikariCP le détectera depuis l'URL JDBC
        // hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver"); // Pas nécessaire

        // Configuration du pool HikariCP
        hikariConfig.setMaximumPoolSize(config.getInt("database.hikari.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.hikari.minimum-idle", 5));
        hikariConfig.setConnectionTimeout(config.getLong("database.hikari.connection-timeout", 20000));
        hikariConfig.setIdleTimeout(config.getLong("database.hikari.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("database.hikari.max-lifetime", 1800000));
        hikariConfig.setLeakDetectionThreshold(config.getLong("database.hikari.leak-detection-threshold", 60000));

        // Propriétés spécifiques MariaDB
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useUnicode", "true");
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8mb4");
        hikariConfig.addDataSourceProperty("serverTimezone", "UTC");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");

        hikariConfig.setPoolName("NexusHikariPool");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            plugin.getLogger().info("✅ Connexion à la base de données établie avec succès !");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Impossible de se connecter à la base de données !");
            plugin.getLogger().severe("Vérifiez votre configuration dans config.yml");
            plugin.getLogger().severe("Erreur: " + e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
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
