package fr.heneria.nexus;

import fr.heneria.nexus.classes.ClassManager;
import fr.heneria.nexus.commands.NexusCommand;
import fr.heneria.nexus.game.GameManager;
import fr.heneria.nexus.holo.HoloService;
import fr.heneria.nexus.listeners.ClassListener;
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

    @Override
    public void onEnable() {
        instance = this;
        this.gameManager = new GameManager(this);
        this.classManager = new ClassManager();
        this.mapManager = new MapManager(this);
        this.holoService = new HoloService(this);

        getCommand("nexus").setExecutor(new NexusCommand(this));
        getServer().getPluginManager().registerEvents(new ClassListener(this), this);

        getLogger().info("Nexus Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Nexus Plugin has been disabled.");
    }
}
