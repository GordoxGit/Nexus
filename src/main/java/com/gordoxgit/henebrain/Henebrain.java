package com.gordoxgit.henebrain;

import org.bukkit.plugin.java.JavaPlugin;

import com.gordoxgit.henebrain.commands.AdminCommand;
import com.gordoxgit.henebrain.commands.JoinCommand;
import com.gordoxgit.henebrain.listeners.PlayerConnectionListener;
import com.gordoxgit.henebrain.managers.ArenaManager;
import com.gordoxgit.henebrain.managers.ConfigManager;
import com.gordoxgit.henebrain.managers.GameManager;
import com.gordoxgit.henebrain.managers.LoadoutManager;
import com.gordoxgit.henebrain.managers.TeamManager;

public class Henebrain extends JavaPlugin {

    private static Henebrain instance;

    private GameManager gameManager;
    private ArenaManager arenaManager;
    private TeamManager teamManager;
    private LoadoutManager loadoutManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.arenaManager = new ArenaManager(this);
        this.teamManager = new TeamManager(this);
        this.loadoutManager = new LoadoutManager(this);
        this.gameManager = new GameManager(this);

        this.arenaManager.loadArenas();

        AdminCommand adminCommand = new AdminCommand(this);
        JoinCommand joinCommand = new JoinCommand(this, adminCommand);
        getCommand("hb").setExecutor(joinCommand);
        getCommand("hb").setTabCompleter(joinCommand);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
    }

    @Override
    public void onDisable() {
        this.arenaManager.saveArenas();
    }

    public static Henebrain getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public LoadoutManager getLoadoutManager() {
        return loadoutManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}

