package fr.heneria.nexus;

import fr.heneria.nexus.classes.ClassManager;
import fr.heneria.nexus.commands.NexusCommand;
import fr.heneria.nexus.commands.NexusTabCompleter;
import fr.heneria.nexus.game.GameManager;
import fr.heneria.nexus.game.objective.ObjectiveManager;
import fr.heneria.nexus.game.team.TeamManager;
import fr.heneria.nexus.holo.HoloService;
import fr.heneria.nexus.listeners.ClassListener;
import fr.heneria.nexus.listeners.GuiListener;
import fr.heneria.nexus.listeners.ObjectiveListener;
import fr.heneria.nexus.map.MapManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusPlugin extends JavaPlugin {

    @Getter
    private static NexusPlugin instance;
    @Getter
    private GameManager gameManager;
    @Getter
    private ClassManager classManager;
    @Getter
    private MapManager mapManager;
    @Getter
    private HoloService holoService;
    @Getter
    private TeamManager teamManager;
    @Getter
    private ObjectiveManager objectiveManager;

    @Override
    public void onEnable() {
        instance = this;
        this.holoService = new HoloService(this); // Init HoloService first?
        this.mapManager = new MapManager(this);
        this.teamManager = new TeamManager(this);
        this.objectiveManager = new ObjectiveManager(this);
        this.classManager = new ClassManager();
        this.gameManager = new GameManager(this); // Depends on others

        getCommand("nexus").setExecutor(new NexusCommand(this));
        getCommand("nexus").setTabCompleter(new NexusTabCompleter(this));
        getServer().getPluginManager().registerEvents(new ClassListener(this), this);
        getServer().getPluginManager().registerEvents(new ObjectiveListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        getLogger().info("Nexus Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            // cleanup handled by gameManager logic usually
        }
        getLogger().info("Nexus Plugin has been disabled.");
    }
}
