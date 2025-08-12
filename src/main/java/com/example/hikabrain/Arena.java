package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Arena {
    private final String name;
    private String worldName;
    private Location spawnRed, spawnBlue;
    private Location bedRed, bedBlue;
    private Cuboid buildRegion;
    private int targetPoints = 5;
    private int timeLimitMinutes = 15;
    private int teamSize = 2;
    private boolean active = false;

    private final Map<Team, Set<java.util.UUID>> players = new EnumMap<>(Team.class);
    private final Set<Location> placedBlocks = new HashSet<>();
    private int redScore=0, blueScore=0;

    public Arena(String name) {
        this.name = name;
        players.put(Team.RED, new HashSet<>());
        players.put(Team.BLUE, new HashSet<>());
        players.put(Team.SPECTATOR, new HashSet<>());
    }

    public String name() { return name; }
    public String worldName() { return worldName; }
    public void worldName(String n) { worldName = n; }
    public World world() { return worldName != null ? Bukkit.getWorld(worldName) : null; }
    public Location spawnRed() { return spawnRed; }
    public void spawnRed(Location l) { spawnRed = l; }
    public Location spawnBlue() { return spawnBlue; }
    public void spawnBlue(Location l) { spawnBlue = l; }
    public Location bedRed() { return bedRed; }
    public void bedRed(Location l) { bedRed = l; }
    public Location bedBlue() { return bedBlue; }
    public void bedBlue(Location l) { bedBlue = l; }
    public Cuboid buildRegion() { return buildRegion; }
    public void buildRegion(Cuboid c) { buildRegion = c; }
    public int targetPoints() { return targetPoints; }
    public void targetPoints(int n) { targetPoints = n; }
    public int timeLimitMinutes() { return timeLimitMinutes; }
    public void timeLimitMinutes(int m) { timeLimitMinutes = m; }
    public int teamSize() { return teamSize; }
    public void teamSize(int s) { teamSize = s; }
    public boolean isConfigured() { return worldName!=null && spawnRed!=null && spawnBlue!=null && bedRed!=null && bedBlue!=null && buildRegion!=null; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { active=a; }
    public Map<Team, Set<java.util.UUID>> players() { return players; }
    public Set<Location> placedBlocks() { return placedBlocks; }
    public int redScore() { return redScore; } public int blueScore() { return blueScore; }
    public void redScore(int s){ redScore=s; } public void blueScore(int s){ blueScore=s; }

    public void saveTo(File file) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", name);
        cfg.set("world", worldName);
        saveLoc(cfg, "spawn.red", spawnRed);
        saveLoc(cfg, "spawn.blue", spawnBlue);
        saveLoc(cfg, "bed.red", bedRed);
        saveLoc(cfg, "bed.blue", bedBlue);
        saveCub(cfg, "build", buildRegion);
        cfg.set("targetPoints", targetPoints);
        cfg.set("timeLimitMinutes", timeLimitMinutes);
        cfg.set("teamSize", teamSize);
        cfg.save(file);
    }
    public static Arena loadFrom(File file) throws IOException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String name = cfg.getString("name");
        if (name == null) throw new IOException("Missing arena name");
        Arena a = new Arena(name);
        a.worldName = cfg.getString("world");
        a.spawnRed = loadLoc(cfg, "spawn.red");
        a.spawnBlue = loadLoc(cfg, "spawn.blue");
        a.bedRed = loadLoc(cfg, "bed.red");
        a.bedBlue = loadLoc(cfg, "bed.blue");
        a.buildRegion = loadCub(cfg, "build");
        a.targetPoints = cfg.getInt("targetPoints", 5);
        a.timeLimitMinutes = cfg.getInt("timeLimitMinutes", 15);
        a.teamSize = cfg.getInt("teamSize", 2);
        return a;
    }
    private static void saveLoc(YamlConfiguration cfg, String path, Location l) {
        if (l == null) return;
        cfg.set(path + ".world", l.getWorld().getName());
        cfg.set(path + ".x", l.getBlockX()); cfg.set(path + ".y", l.getBlockY()); cfg.set(path + ".z", l.getBlockZ());
        cfg.set(path + ".yaw", l.getYaw()); cfg.set(path + ".pitch", l.getPitch());
    }
    private static Location loadLoc(YamlConfiguration cfg, String path) {
        if (!cfg.contains(path + ".world")) return null;
        World w = Bukkit.getWorld(cfg.getString(path + ".world"));
        int x = cfg.getInt(path + ".x"), y = cfg.getInt(path + ".y"), z = cfg.getInt(path + ".z");
        float yaw = (float) cfg.getDouble(path + ".yaw"), pitch = (float) cfg.getDouble(path + ".pitch");
        return new Location(w, x, y, z, yaw, pitch);
    }
    private static void saveCub(YamlConfiguration cfg, String path, Cuboid c) {
        if (c == null) return;
        cfg.set(path + ".world", c.world().getName());
        cfg.set(path + ".x1", c.x1()); cfg.set(path + ".y1", c.y1()); cfg.set(path + ".z1", c.z1());
        cfg.set(path + ".x2", c.x2()); cfg.set(path + ".y2", c.y2()); cfg.set(path + ".z2", c.z2());
    }
    private static Cuboid loadCub(YamlConfiguration cfg, String path) {
        if (!cfg.contains(path + ".world")) return null;
        World w = Bukkit.getWorld(cfg.getString(path + ".world"));
        int x1 = cfg.getInt(path + ".x1"), y1 = cfg.getInt(path + ".y1"), z1 = cfg.getInt(path + ".z1");
        int x2 = cfg.getInt(path + ".x2"), y2 = cfg.getInt(path + ".y2"), z2 = cfg.getInt(path + ".z2");
        return new Cuboid(new Location(w,x1,y1,z1), new Location(w,x2,y2,z2));
    }
}
