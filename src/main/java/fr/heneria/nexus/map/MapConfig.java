package fr.heneria.nexus.map;

import fr.heneria.nexus.NexusPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MapConfig {

    private final NexusPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private final Map<String, NexusMap> maps = new HashMap<>();

    public MapConfig(NexusPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "maps.yml");
    }

    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("maps.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        maps.clear();

        ConfigurationSection mapsSection = config.getConfigurationSection("maps");
        if (mapsSection == null) return;

        for (String key : mapsSection.getKeys(false)) {
            String name = mapsSection.getString(key + ".name");
            String description = mapsSection.getString(key + ".description");
            String sourceFolder = mapsSection.getString(key + ".sourceFolder");

            maps.put(key, new NexusMap(key, name, description, sourceFolder));
        }
    }

    public NexusMap getMap(String id) {
        return maps.get(id);
    }

    public Map<String, NexusMap> getMaps() {
        return maps;
    }
}
