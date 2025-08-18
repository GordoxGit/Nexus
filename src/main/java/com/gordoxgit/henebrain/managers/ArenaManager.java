package com.gordoxgit.henebrain.managers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.data.Arena;
import com.gordoxgit.henebrain.game.GameModeType;

/**
 * Manages all arena persistence and CRUD operations.
 */
public class ArenaManager {
    private final Henebrain plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private File configFile;

    public ArenaManager(Henebrain plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all arenas from the arenas.yml file.
     */
    public void loadArenas() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!configFile.exists()) {
            return; // Nothing to load yet
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        for (String key : config.getKeys(false)) {
            String path = key + ".";

            Location lobby = config.getLocation(path + "lobby");

            Map<String, Location> spawns = new HashMap<>();
            ConfigurationSection spawnSection = config.getConfigurationSection(path + "spawns");
            if (spawnSection != null) {
                for (String team : spawnSection.getKeys(false)) {
                    spawns.put(team, spawnSection.getLocation(team));
                }
            }

            Location point = config.getLocation(path + "point");

            List<GameModeType> modes = new ArrayList<>();
            for (String modeName : config.getStringList(path + "modes")) {
                try {
                    modes.add(GameModeType.valueOf(modeName));
                } catch (IllegalArgumentException ignored) {
                }
            }

            List<Location> barriers = new ArrayList<>();
            List<?> barrierList = config.getList(path + "barrier");
            if (barrierList != null) {
                for (Object obj : barrierList) {
                    if (obj instanceof Location) {
                        barriers.add((Location) obj);
                    }
                }
            }

            Arena arena = new Arena(key, lobby, spawns, point, modes, barriers);
            arenas.put(key, arena);
        }
    }

    /**
     * Saves all arenas to the arenas.yml file.
     */
    public void saveArenas() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "arenas.yml");
        }

        FileConfiguration config = new YamlConfiguration();

        for (Arena arena : arenas.values()) {
            String path = arena.getName() + ".";
            config.set(path + "lobby", arena.getLobby());
            for (Map.Entry<String, Location> entry : arena.getTeamSpawns().entrySet()) {
                config.set(path + "spawns." + entry.getKey(), entry.getValue());
            }
            config.set(path + "point", arena.getPoint());

            List<String> modeNames = new ArrayList<>();
            for (GameModeType mode : arena.getSupportedModes()) {
                modeNames.add(mode.name());
            }
            config.set(path + "modes", modeNames);
            config.set(path + "barrier", arena.getBarrierLocations());
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createArena(String name) {
        arenas.put(name, new Arena(name, null, new HashMap<>(), null, new ArrayList<>(), new ArrayList<>()));
    }

    public void deleteArena(String name) {
        arenas.remove(name);
    }

    public Arena getArena(String name) {
        return arenas.get(name);
    }

    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }
}


