package com.example.hikabrain.protection;

import com.example.hikabrain.Cuboid;
import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProtectionService {
    private final HikaBrainPlugin plugin;
    private final Map<String, Cuboid> regions = new HashMap<>();
    private final Set<UUID> protectMode = new HashSet<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final File file;

    public ProtectionService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "protection.yml");
        loadRegions();
    }

    public void loadRegions() {
        regions.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("regions");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(name);
                if (r == null) continue;
                World w = Bukkit.getWorld(r.getString("world", ""));
                if (w == null) continue;
                Location a = new Location(w, r.getInt("x1"), r.getInt("y1"), r.getInt("z1"));
                Location b = new Location(w, r.getInt("x2"), r.getInt("y2"), r.getInt("z2"));
                regions.put(name, new Cuboid(a, b));
            }
        }
    }

    public void saveRegions() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection sec = yml.createSection("regions");
        for (Map.Entry<String, Cuboid> en : regions.entrySet()) {
            Cuboid c = en.getValue();
            ConfigurationSection r = sec.createSection(en.getKey());
            r.set("world", c.world().getName());
            r.set("x1", c.x1()); r.set("y1", c.y1()); r.set("z1", c.z1());
            r.set("x2", c.x2()); r.set("y2", c.y2()); r.set("z2", c.z2());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save protection regions: " + e.getMessage());
        }
    }

    public boolean isProtected(Location location) {
        for (Cuboid c : regions.values()) {
            if (c.contains(location)) return true;
        }
        return false;
    }

    public void addRegion(String name, Cuboid cuboid) {
        regions.put(name, cuboid);
    }

    public Map<String, Cuboid> regions() { return regions; }

    public void removeRegion(String name) { regions.remove(name); }

    public boolean isInProtectMode(Player player) {
        return protectMode.contains(player.getUniqueId());
    }

    public void enableProtectMode(Player player) {
        protectMode.add(player.getUniqueId());
    }

    public void disableProtectMode(Player player) {
        UUID id = player.getUniqueId();
        protectMode.remove(id);
        pos1.remove(id);
        pos2.remove(id);
    }

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc);
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc);
    }

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }
}
