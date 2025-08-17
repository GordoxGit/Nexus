package fr.gordox.henebrain.commands.subcommands;

import org.bukkit.command.CommandSender;

import fr.gordox.henebrain.Henebrain;
import fr.gordox.henebrain.commands.HenebrainCommand;
import fr.gordox.henebrain.commands.SubCommand;

public class HelpCommand extends SubCommand {
    private final Henebrain plugin;
    private final HenebrainCommand command;

    public HelpCommand(Henebrain plugin, HenebrainCommand command) {
        this.plugin = plugin;
        this.command = command;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Affiche la liste des commandes";
    }

    @Override
    public String getSyntax() {
        return "/henebrain help";
    }

    @Override
    public String getPermission() {
        return "henebrain.command.help";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        sender.sendMessage(plugin.getLanguageManager().getMessage("command-help-header"));
        for (SubCommand sub : command.getSubCommands()) {
            String perm = sub.getPermission();
            if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage(
                        "command-help-format",
                        "%command%", sub.getSyntax(),
                        "%description%", sub.getDescription()));
            }
        }
    }
}
