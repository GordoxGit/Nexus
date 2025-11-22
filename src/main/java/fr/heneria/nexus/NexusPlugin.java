package fr.heneria.nexus;

import fr.heneria.nexus.classes.ClassManager;
import fr.heneria.nexus.commands.NexusCommand;
import fr.heneria.nexus.game.GameManager;
import fr.heneria.nexus.listeners.ClassListener;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusPlugin extends JavaPlugin {

    @Getter
    private static NexusPlugin instance;
    @Getter
    private GameManager gameManager;
    @Getter
    private ClassManager classManager;

    @Override
    public void onEnable() {
        instance = this;
        this.gameManager = new GameManager(this);
        this.classManager = new ClassManager();

        getCommand("nexus").setExecutor(new NexusCommand(this));
        getServer().getPluginManager().registerEvents(new ClassListener(this), this);

        getLogger().info("Nexus Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Nexus Plugin has been disabled.");
    }
}
