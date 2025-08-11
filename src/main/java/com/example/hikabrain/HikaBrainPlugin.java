package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HikaBrainPlugin extends JavaPlugin {

    private static HikaBrainPlugin instance;
    private GameManager gameManager;
    private final Set<String> allowedWorlds = new HashSet<>(); // lower-case

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadAllowedWorlds();

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
        if (gameManager != null) gameManager.shutdown();
        getLogger().info("HikaBrain disabled.");
    }

    private void loadAllowedWorlds() {
        List<String> list = getConfig().getStringList("allowed-worlds");
        allowedWorlds.clear();
        for (String w : list) if (w != null && !w.isEmpty()) allowedWorlds.add(w.toLowerCase(Locale.ROOT));
        if (allowedWorlds.isEmpty()) allowedWorlds.add("hika"); // default
    }

    public static HikaBrainPlugin get() { return instance; }
    public GameManager game() { return gameManager; }
    public boolean isWorldAllowed(World w) { return w != null && allowedWorlds.contains(w.getName().toLowerCase(Locale.ROOT)); }
    public String allowedWorldsPretty() { return String.join(", ", allowedWorlds); }
}
