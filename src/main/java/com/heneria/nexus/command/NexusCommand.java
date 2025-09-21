package com.heneria.nexus.command;

import com.heneria.nexus.NexusPlugin;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Main command entry point.
 */
public final class NexusCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("help", "reload", "dump", "budget");

    private final NexusPlugin plugin;

    public NexusCommand(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> {
                plugin.sendHelp(sender);
                yield true;
            }
            case "reload" -> {
                plugin.handleReload(sender);
                yield true;
            }
            case "dump" -> {
                plugin.handleDump(sender);
                yield true;
            }
            case "budget" -> {
                plugin.handleBudget(sender, args);
                yield true;
            }
            default -> {
                plugin.sendHelp(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream()
                    .filter(sub -> {
                        if (sub.equals("reload") || sub.equals("dump") || sub.equals("budget")) {
                            return sender.hasPermission("nexus.admin." + sub);
                        }
                        return true;
                    })
                    .filter(sub -> sub.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("budget")) {
            if (!sender.hasPermission("nexus.admin.budget")) {
                return Collections.emptyList();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.suggestBudgetArenas(prefix);
        }
        return Collections.emptyList();
    }
}
