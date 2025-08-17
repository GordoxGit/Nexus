package fr.gordox.henebrain;

import org.bukkit.plugin.java.JavaPlugin;

import fr.gordox.henebrain.commands.HenebrainCommand;
import fr.gordox.henebrain.commands.subcommands.HelpCommand;
import fr.gordox.henebrain.commands.subcommands.ReloadCommand;
import fr.gordox.henebrain.commands.subcommands.VersionCommand;
import fr.gordox.henebrain.utils.ConfigManager;
import fr.gordox.henebrain.utils.LanguageManager;

public class Henebrain extends JavaPlugin {
    private static Henebrain instance;

    private ConfigManager configManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);

        HenebrainCommand mainCommand = new HenebrainCommand(this);
        mainCommand.registerSubCommand(new VersionCommand(this));
        mainCommand.registerSubCommand(new HelpCommand(this, mainCommand));
        mainCommand.registerSubCommand(new ReloadCommand(this));

        getCommand("henebrain").setExecutor(mainCommand);
        getCommand("henebrain").setTabCompleter(mainCommand);
    }

    public static Henebrain getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
