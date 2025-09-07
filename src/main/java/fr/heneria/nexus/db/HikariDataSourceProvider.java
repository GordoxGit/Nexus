package fr.heneria.nexus.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class HikariDataSourceProvider {

    private static HikariDataSourceProvider instance;
    private HikariDataSource dataSource;

    public HikariDataSourceProvider() {
        if (instance != null) {
            throw new IllegalStateException("HikariDataSourceProvider already initialized");
        }
        instance = this;
    }

    public void init(JavaPlugin plugin) {
        if (this.dataSource != null) {
            return;
        }

        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();

        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "nexus");
        String username = config.getString("database.username", "nexus_user");
        String password = config.getString("database.password", "your_password_here");

        if (host == null || host.isBlank() || database == null || database.isBlank() || username == null || username.isBlank()) {
            plugin.getLogger().severe("❌ Configuration de base de données invalide. Veuillez vérifier config.yml");
            throw new IllegalArgumentException("Invalid database configuration");
        }
        
        // ======================= CORRECTION CI-DESSOUS =======================
        // Force le chargement de la classe du driver MariaDB pour l'enregistrer.
        // C'est la solution définitive au problème "No suitable driver".
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("❌ Le driver JDBC MariaDB est introuvable ! Le plugin ne peut pas démarrer.");
            throw new RuntimeException("Failed to load MariaDB JDBC driver", e);
        }
        // =====================================================================

        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
        plugin.getLogger().info("Connexion à la base de données: " + jdbcUrl);

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        hikariConfig.setMaximumPoolSize(config.getInt("database.hikari.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.hikari.minimum-idle", 5));
        hikariConfig.setConnectionTimeout(config.getLong("database.hikari.connection-timeout", 20000));
        hikariConfig.setIdleTimeout(config.getLong("database.hikari.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("database.hikari.max-lifetime", 1800000));
        hikariConfig.setLeakDetectionThreshold(config.getLong("database.hikari.leak-detection-threshold", 60000));

        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useUnicode", "true");
        hikariConfig.addDataSourceProperty("characterEncoding", "utf8mb4");
        
        hikariConfig.setPoolName("NexusHikariPool");

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            try (Connection connection = this.dataSource.getConnection()) {
                plugin.getLogger().info("✅ Connexion à la base de données établie et testée avec succès !");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Impossible de se connecter à la base de données !");
            plugin.getLogger().severe("Vérifiez votre configuration dans config.yml et que le serveur de base de données est accessible.");
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
