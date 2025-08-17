package fr.gordox.henebrain.commands.subcommands;

import org.bukkit.command.CommandSender;

import fr.gordox.henebrain.Henebrain;
import fr.gordox.henebrain.commands.SubCommand;

public class ReloadCommand extends SubCommand {
    private final Henebrain plugin;

    public ReloadCommand(Henebrain plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Recharge la configuration et les messages";
    }

    @Override
    public String getSyntax() {
        return "/henebrain reload";
    }

    @Override
    public String getPermission() {
        return "henebrain.admin.reload";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        plugin.getConfigManager().reloadConfig();
        plugin.getLanguageManager().reloadMessages();
        sender.sendMessage(plugin.getLanguageManager().getMessage("config-reloaded"));
    }
}
