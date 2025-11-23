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

            // Load Teams Spawn and Nexus
            Map<GameTeam, NexusMap.ConfigLocation> teamSpawns = new HashMap<>();
            Map<GameTeam, NexusMap.ConfigLocation> teamNexusLocations = new HashMap<>();
            if (section.isConfigurationSection("teams")) {
                ConfigurationSection teamsSec = section.getConfigurationSection("teams");
                for (String teamKey : teamsSec.getKeys(false)) {
                    try {
                        GameTeam team = GameTeam.valueOf(teamKey.toUpperCase());
                        ConfigurationSection teamSec = teamsSec.getConfigurationSection(teamKey);
                        if (teamSec != null) {
                            NexusMap.ConfigLocation spawnLoc = parseConfigLocation(teamSec, "spawn");
                            if (spawnLoc != null) {
                                teamSpawns.put(team, spawnLoc);
                            }
                            NexusMap.ConfigLocation nexusLoc = parseConfigLocation(teamSec, "nexusLocation");
                            if (nexusLoc != null) {
                                teamNexusLocations.put(team, nexusLoc);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid team in map config: " + teamKey);
                    }
                }
            }

            // Load Captures
            List<NexusMap.CaptureConfig> captureConfigs = new ArrayList<>();
            if (section.isConfigurationSection("captures")) {
                ConfigurationSection capsSec = section.getConfigurationSection("captures");
                for (String capKey : capsSec.getKeys(false)) {
                    ConfigurationSection cSec = capsSec.getConfigurationSection(capKey);
                    if (cSec != null) {
                        NexusMap.ConfigLocation loc = parseConfigLocation(cSec, "location");
                        double radius = cSec.getDouble("radius", 6.0);
                        int respawnTime = cSec.getInt("respawnTime", 10);
                        if (loc != null) {
                            captureConfigs.add(new NexusMap.CaptureConfig(capKey, loc, radius, respawnTime));
                        }
                    }
                }
            }

            maps.put(key, new NexusMap(key, name, description, sourceFolder, teamSpawns, teamNexusLocations, captureConfigs));
        }
    }

    private NexusMap.ConfigLocation parseConfigLocation(ConfigurationSection section, String path) {
        if (section.isList(path)) {
            List<Double> coords = section.getDoubleList(path);
            if (coords.size() >= 3) {
                 double x = coords.get(0);
                 double y = coords.get(1);
                 double z = coords.get(2);
                 float yaw = (float) section.getDouble("yaw", 0.0);
                 float pitch = (float) section.getDouble("pitch", 0.0);
                 return new NexusMap.ConfigLocation(x, y, z, yaw, pitch);
            }
        }
        return null;
    }

    public NexusMap getMap(String id) {
        return maps.get(id);
    }

    public Map<String, NexusMap> getMaps() {
        return maps;
    }

    public void saveMapLocation(String mapId, String path, Location loc, boolean reload) {
        if (config == null) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }

        String fullPath = "maps." + mapId + "." + path;

        if (!config.contains("maps." + mapId + ".name")) {
            config.set("maps." + mapId + ".name", mapId);
            config.set("maps." + mapId + ".description", "Created via Setup Editor");
            config.set("maps." + mapId + ".sourceFolder", mapId);
        }

        boolean useListFormat = path.equals("location") || path.endsWith(".location") || path.endsWith("nexusLocation");

        if (useListFormat) {
            // Use list format [x, y, z]
            List<Double> coords = new ArrayList<>();
            coords.add(loc.getX());
            coords.add(loc.getY());
            coords.add(loc.getZ());
            config.set(fullPath, coords);

            // Special case for capture radius
            if (path.contains("captures.")) {
                 String radiusPath = fullPath.replace(".location", ".radius");
                 if (!config.contains(radiusPath)) {
                     config.set(radiusPath, 5);
                 }
                 String respawnPath = fullPath.replace(".location", ".respawnTime");
                 if (!config.contains(respawnPath)) {
                     config.set(respawnPath, 10);
                 }
            }
        } else {
             // Fallback to old Section based saving (supports Yaw/Pitch)
             ConfigurationSection section = config.getConfigurationSection(fullPath);
             if (section == null) {
                 section = config.createSection(fullPath);
             }
             LocationUtils.saveLocation(section, loc);
        }

        try {
            config.save(configFile);
            if (reload) {
                load();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save maps.yml: " + e.getMessage());
        }
    }

    public void saveMapLocation(String mapId, String path, Location loc) {
        saveMapLocation(mapId, path, loc, true);
    }
}
