package com.example.hikabrain.arena;

import com.example.hikabrain.Arena;
import com.example.hikabrain.GameManager;
import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Simple registry reading arena files grouped by team size. */
public class ArenaRegistry {
    private final HikaBrainPlugin plugin;

    public ArenaRegistry(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    /** List arena names for a given team size. */
    public List<String> list(int teamSize) {
        File dir = new File(plugin.getDataFolder(), "arenas");
        if (!dir.exists()) return Collections.emptyList();
        String[] ls = dir.list((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (ls == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String n : ls) {
            File f = new File(dir, n);
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                if (cfg.getInt("teamSize", 2) == teamSize) {
                    out.add(n.substring(0, n.length() - 4));
                }
            } catch (Exception ignored) { }
        }
        return out;
    }

    public enum State { WAITING, STARTING, RUNNING }

    /** Very small state check relying on current loaded arena. */
    public State state(String name) {
        GameManager gm = plugin.game();
        Arena a = gm.arena();
        if (a != null && a.name().equalsIgnoreCase(name) && a.isActive()) {
            return State.RUNNING;
        }
        return State.WAITING;
    }
}
