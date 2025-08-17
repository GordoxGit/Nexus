package fr.gordox.henebrain.commands.subcommands;

import org.bukkit.command.CommandSender;

import fr.gordox.henebrain.Henebrain;
import fr.gordox.henebrain.commands.SubCommand;

public class VersionCommand extends SubCommand {
    private final Henebrain plugin;

    public VersionCommand(Henebrain plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public String getDescription() {
        return "Affiche la version du plugin";
    }

    @Override
    public String getSyntax() {
        return "/henebrain version";
    }

    @Override
    public String getPermission() {
        return "henebrain.command.version";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        String version = plugin.getDescription().getVersion();
        sender.sendMessage(plugin.getLanguageManager().getMessage("plugin-version", "%version%", version));
    }
}
