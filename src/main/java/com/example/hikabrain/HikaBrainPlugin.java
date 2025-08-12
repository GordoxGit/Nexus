package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

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
import com.example.hikabrain.ui.style.UiStyle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HikaBrainPlugin extends JavaPlugin {

    private static HikaBrainPlugin instance;
    private GameManager gameManager;
    private UiService ui;
    private ThemeService theme;
    private FeedbackService fx;
    private ScoreboardService scoreboard;
    private TablistService tablist;
    private UiStyle uiStyle;
    private String serverDisplayName;
    private String serverDomain;
    private final Set<String> allowedWorlds = new HashSet<>(); // lower-case

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadAllowedWorlds();
        loadServerInfo();
        loadUiStyle();
        if (getConfig().getBoolean("debug", false)) getLogger().setLevel(java.util.logging.Level.FINE);

        this.theme = new ThemeServiceImpl(this);
        this.fx = new FeedbackServiceImpl(this);
        this.ui = new UiServiceImpl(this, theme, fx);
        this.scoreboard = new ScoreboardServiceV2(this);
        this.tablist = new TablistServiceV2(this);

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
        if (allowedWorlds.isEmpty()) allowedWorlds.add("hika"); // default
    }

    private void loadServerInfo() {
        serverDisplayName = getConfig().getString("server.display-name", "Heneria");
        serverDomain = getConfig().getString("server.domain", "heneria.com");
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
    public UiStyle style() { return uiStyle; }
    public String serverDisplayName() { return serverDisplayName; }
    public String serverDomain() { return serverDomain; }
    public void reloadServerInfo() { loadServerInfo(); }
    public void reloadUiStyle() { loadUiStyle(); }
    public boolean isWorldAllowed(World w) { return w != null && allowedWorlds.contains(w.getName().toLowerCase(Locale.ROOT)); }
    public String allowedWorldsPretty() { return String.join(", ", allowedWorlds); }
}
