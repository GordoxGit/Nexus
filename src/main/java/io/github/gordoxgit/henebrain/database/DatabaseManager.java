package io.github.gordoxgit.henebrain.database;

import io.github.gordoxgit.henebrain.Henebrain;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Handles the connection to the MySQL database.
 */
public class DatabaseManager {

    private final Henebrain plugin;
    private Connection connection;

    public DatabaseManager(Henebrain plugin) {
        this.plugin = plugin;
    }

    /**
     * Connects to the database using the credentials provided in the config.yml.
     */
    public void connect() {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String database = plugin.getConfig().getString("database.database");
        String username = plugin.getConfig().getString("database.username");
        String password = plugin.getConfig().getString("database.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;

        try {
            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("Connexion à la base de données réussie.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Échec de la connexion à la base de données : " + e.getMessage());
        }
    }

    /**
     * Closes the database connection.
     */
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Connexion à la base de données fermée.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Erreur lors de la fermeture de la connexion : " + e.getMessage());
            }
        }
    }

    /**
     * Returns the current SQL connection.
     *
     * @return Connection or null if not connected
     */
    public Connection getConnection() {
        return connection;
    }
}
