package io.github.gordoxgit.henebrain.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Routes /hb subcommands to specific handlers.
 */
public class HbCommand implements CommandExecutor {

    private final DbCommand dbCommand;
    private final ArenaCommand arenaCommand;

    public HbCommand(DbCommand dbCommand, ArenaCommand arenaCommand) {
        this.dbCommand = dbCommand;
        this.arenaCommand = arenaCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb <db|arena> ...");
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "db" -> dbCommand.onCommand(sender, command, label, args);
            case "arena" -> arenaCommand.onCommand(sender, command, label, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                yield true;
            }
        };
    }
}
