package fr.gordox.henebrain.utils;

import org.bukkit.configuration.file.FileConfiguration;

import fr.gordox.henebrain.Henebrain;

public class ConfigManager {
    private final Henebrain plugin;
    private FileConfiguration config;

    public ConfigManager(Henebrain plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }
}
