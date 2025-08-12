package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import com.example.hikabrain.ui.FeedbackService;
import com.example.hikabrain.ui.FeedbackServiceImpl;
import com.example.hikabrain.ui.ThemeService;
import com.example.hikabrain.ui.ThemeServiceImpl;
import com.example.hikabrain.ui.UiService;
import com.example.hikabrain.ui.UiServiceImpl;
import com.example.hikabrain.ui.scoreboard.ScoreboardService;
import com.example.hikabrain.ui.scoreboard.ScoreboardServiceV2;
import com.example.hikabrain.ui.tablist.TablistService;
import com.example.hikabrain.ui.tablist.TablistServiceV2;
import com.example.hikabrain.ui.compass.CompassGuiService;
import com.example.hikabrain.lobby.LobbyService;
import com.example.hikabrain.arena.ArenaRegistry;
import com.example.hikabrain.ui.style.UiStyle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.File;

public class HikaBrainPlugin extends JavaPlugin {

    private static HikaBrainPlugin instance;
    private GameManager gameManager;
    private UiService ui;
    private ThemeService theme;
    private FeedbackService fx;
    private ScoreboardService scoreboard;
    private TablistService tablist;
    private CompassGuiService compassGui;
    private LobbyService lobbyService;
    private ArenaRegistry arenaRegistry;
    private UiStyle uiStyle;
    private String serverDisplayName;
    private String serverDomain;
    private final Set<String> allowedWorlds = new HashSet<>(); // lower-case
    private org.bukkit.Location lobbyLocation;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateWorldIds();
        loadAllowedWorlds();
        loadLobby();
        loadServerInfo();
        loadUiStyle();
        if (getConfig().getBoolean("debug", false)) getLogger().setLevel(java.util.logging.Level.FINE);

        this.theme = new ThemeServiceImpl(this);
        this.fx = new FeedbackServiceImpl(this);
        this.ui = new UiServiceImpl(this, theme, fx);
        this.scoreboard = new ScoreboardServiceV2(this);
        this.tablist = new TablistServiceV2(this);
        this.compassGui = new CompassGuiService(this);
        this.lobbyService = new LobbyService(this);
        this.arenaRegistry = new ArenaRegistry(this);

        this.gameManager = new GameManager(this);
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        boolean registered = false;
        try {
            if (getCommand("hb") != null) {
                HBCommand cmd = new HBCommand(gameManager);
                getCommand("hb").setExecutor(cmd);
                getCommand("hb").setTabCompleter(cmd);
                registered = true;
                getLogger().info("Registered /hb from plugin.yml");
            }
        } catch (Exception ignored) {}

        if (!registered) {
            try {
                Method getCommandMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
                CommandMap map = (CommandMap) getCommandMap.invoke(Bukkit.getServer());
                Constructor<PluginCommand> cons = PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
                cons.setAccessible(true);
                PluginCommand pc = cons.newInstance("hb", this);
                HBCommand cmd = new HBCommand(gameManager);
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
                pc.setDescription("HikaBrain main command");
                map.register(getDescription().getName(), pc);
                getLogger().warning("Registered /hb programmatically (plugin.yml commands missing).");
            } catch (Throwable t) {
                getLogger().severe("Failed to register /hb command: " + t.getMessage());
            }
        }

        getLogger().info("HikaBrain enabled. Allowed worlds: " + String.join(", ", allowedWorlds));
    }

    @Override
    public void onDisable() {
        if (scoreboard != null) scoreboard.clear();
        if (gameManager != null) gameManager.shutdown();
        getLogger().info("HikaBrain disabled.");
    }

    private void loadAllowedWorlds() {
        List<String> list = getConfig().getStringList("allowed-worlds");
        allowedWorlds.clear();
        for (String w : list) if (w != null && !w.isEmpty()) allowedWorlds.add(w.toLowerCase(Locale.ROOT));
        if (allowedWorlds.isEmpty()) allowedWorlds.add("world_hika"); // default
        allowedWorlds.removeIf(w -> {
            if (Bukkit.getWorld(w) == null) {
                getLogger().severe("Allowed world not found: " + w);
                return true;
            }
            return false;
        });
    }

    private void loadServerInfo() {
        serverDisplayName = getConfig().getString("server.display-name", "Heneria");
        serverDomain = getConfig().getString("server.domain", "heneria.com");
    }

    private void loadLobby() {
        org.bukkit.configuration.ConfigurationSection sec = getConfig().getConfigurationSection("lobby");
        if (sec == null) { lobbyLocation = null; return; }
        World w = Bukkit.getWorld(sec.getString("world", ""));
        if (w == null) { lobbyLocation = null; return; }
        double x = sec.getDouble("x", 0.5);
        double y = sec.getDouble("y", 80.0);
        double z = sec.getDouble("z", 0.5);
        float yaw = (float) sec.getDouble("yaw", 0.0);
        float pitch = (float) sec.getDouble("pitch", 0.0);
        lobbyLocation = new org.bukkit.Location(w, x, y, z, yaw, pitch);
    }

    private void loadUiStyle() {
        org.bukkit.configuration.ConfigurationSection sec = getConfig().getConfigurationSection("ui.style");
        if (sec == null) sec = getConfig().createSection("ui.style");
        uiStyle = new UiStyle(sec);
    }

    public static HikaBrainPlugin get() { return instance; }
    public GameManager game() { return gameManager; }
    public UiService ui() { return ui; }
    public ThemeService theme() { return theme; }
    public FeedbackService fx() { return fx; }
    public ScoreboardService scoreboard() { return scoreboard; }
    public TablistService tablist() { return tablist; }
    public CompassGuiService compassGui() { return compassGui; }
    public LobbyService lobbyService() { return lobbyService; }
    public ArenaRegistry arenaRegistry() { return arenaRegistry; }
    public UiStyle style() { return uiStyle; }
    public String serverDisplayName() { return serverDisplayName; }
    public String serverDomain() { return serverDomain; }
    public void reloadServerInfo() { loadServerInfo(); }
    public void reloadUiStyle() { loadUiStyle(); }
    public boolean isWorldAllowed(World w) { return w != null && allowedWorlds.contains(w.getName().toLowerCase(Locale.ROOT)); }
    public String allowedWorldsPretty() { return String.join(", ", allowedWorlds); }
    public org.bukkit.Location lobby() { return lobbyLocation; }
    public void setLobby(org.bukkit.Location l) {
        lobbyLocation = l;
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        cfg.set("lobby.world", l.getWorld().getName());
        cfg.set("lobby.x", l.getX());
        cfg.set("lobby.y", l.getY());
        cfg.set("lobby.z", l.getZ());
        cfg.set("lobby.yaw", l.getYaw());
        cfg.set("lobby.pitch", l.getPitch());
        saveConfig();
    }

    private void migrateWorldIds() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        boolean changed = false;
        java.util.List<String> aw = cfg.getStringList("allowed-worlds");
        boolean listChanged = false;
        for (int i = 0; i < aw.size(); i++) {
            String w = aw.get(i);
            if ("hika".equalsIgnoreCase(w)) { aw.set(i, "world_hika"); listChanged = true; }
        }
        if (listChanged) { cfg.set("allowed-worlds", aw); changed = true; }

        String lw = cfg.getString("lobby.world");
        if ("hika".equalsIgnoreCase(lw)) { cfg.set("lobby.world", "world_hika"); changed = true; }

        int migrated = 0;
        File dir = new File(getDataFolder(), "arenas");
        if (dir.exists()) {
            File[] files = dir.listFiles((d,n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                        String w = yml.getString("world");
                        if ("hika".equalsIgnoreCase(w)) {
                            yml.set("world", "world_hika");
                            yml.save(f);
                            migrated++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        if (changed) saveConfig();
        if (migrated > 0) getLogger().info("Migrated world ids to world_hika: " + migrated + " arenas updated.");
    }
}
