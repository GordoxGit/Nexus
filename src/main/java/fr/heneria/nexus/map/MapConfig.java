package fr.heneria.nexus.map;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.utils.LocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            ConfigurationSection section = mapsSection.getConfigurationSection(key);
            if (section == null) continue;

            String name = section.getString("name");
            String description = section.getString("description");
            String sourceFolder = section.getString("sourceFolder");

            // Load Teams Spawn
            Map<GameTeam, NexusMap.ConfigLocation> teamSpawns = new HashMap<>();
            ConfigurationSection teamsSec = section.getConfigurationSection("teams");
            if (teamsSec != null) {
                for (String teamKey : teamsSec.getKeys(false)) {
                    try {
                        GameTeam team = GameTeam.valueOf(teamKey.toUpperCase());
                        ConfigurationSection locSec = teamsSec.getConfigurationSection(teamKey + ".spawnLocation");
                        if (locSec != null) {
                            teamSpawns.put(team, loadLocation(locSec));
                        }
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid team in map config: " + teamKey);
                    }
                }
            }

            // Load Nexus
            Map<GameTeam, NexusMap.NexusConfig> nexusConfigs = new HashMap<>();
            ConfigurationSection nexusSec = section.getConfigurationSection("nexus");
            if (nexusSec != null) {
                for (String teamKey : nexusSec.getKeys(false)) {
                    try {
                        GameTeam team = GameTeam.valueOf(teamKey.toUpperCase());
                        ConfigurationSection nSec = nexusSec.getConfigurationSection(teamKey);
                        if (nSec != null) {
                            ConfigurationSection locSec = nSec.getConfigurationSection("location");
                            double hp = nSec.getDouble("maxHealth", 100);
                            if (locSec != null) {
                                nexusConfigs.put(team, new NexusMap.NexusConfig(loadLocation(locSec), hp));
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid team in nexus config: " + teamKey);
                    }
                }
            }

            // Load Captures
            List<NexusMap.CaptureConfig> captureConfigs = new ArrayList<>();
            ConfigurationSection capsSec = section.getConfigurationSection("captures");
            if (capsSec != null) {
                for (String capKey : capsSec.getKeys(false)) {
                    ConfigurationSection cSec = capsSec.getConfigurationSection(capKey);
                    if (cSec != null) {
                        ConfigurationSection locSec = cSec.getConfigurationSection("centerLocation");
                        double radius = cSec.getDouble("radius", 10);
                        if (locSec != null) {
                            captureConfigs.add(new NexusMap.CaptureConfig(capKey, loadLocation(locSec), radius));
                        }
                    }
                }
            }

            maps.put(key, new NexusMap(key, name, description, sourceFolder, teamSpawns, nexusConfigs, captureConfigs));
        }
    }

    private NexusMap.ConfigLocation loadLocation(ConfigurationSection sec) {
        return new NexusMap.ConfigLocation(
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw", 0),
                (float) sec.getDouble("pitch", 0)
        );
    }

    public NexusMap getMap(String id) {
        return maps.get(id);
    }

    public Map<String, NexusMap> getMaps() {
        return maps;
    }

    public void saveMapLocation(String mapId, String path, Location loc) {
        if (config == null) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }

        String fullPath = "maps." + mapId + "." + path;
        ConfigurationSection section = config.getConfigurationSection(fullPath);
        if (section == null) {
            section = config.createSection(fullPath);
        }

        // Ensure map entry exists with basic fields if creating new
        if (!config.contains("maps." + mapId + ".name")) {
            config.set("maps." + mapId + ".name", mapId);
            config.set("maps." + mapId + ".description", "Created via Setup Editor");
            config.set("maps." + mapId + ".sourceFolder", mapId); // Assumption
        }

        LocationUtils.saveLocation(section, loc);

        try {
            config.save(configFile);
            load(); // Reload maps to reflect changes
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save maps.yml: " + e.getMessage());
        }
    }
}
