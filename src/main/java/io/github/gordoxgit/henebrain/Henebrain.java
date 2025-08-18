package io.github.gordoxgit.henebrain;

import org.bukkit.plugin.java.JavaPlugin;
import io.github.gordoxgit.henebrain.database.DatabaseManager;

public final class Henebrain extends JavaPlugin {

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // Save default configuration if not present
        saveDefaultConfig();

        // Initialize and connect to the database
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        getLogger().info("Henebrain a démarré avec succès !");
    }

    @Override
    public void onDisable() {
        // Disconnect from database if connected
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("Henebrain s'est arrêté.");
    }
}
