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

    private static final List<String> SUB_COMMANDS = List.of("help", "reload", "dump", "budget", "holo");
    private static final List<String> HOLO_SUB = List.of("create", "remove", "move", "list", "reload");

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
            case "holo" -> {
                plugin.handleHologram(sender, args);
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
                        if (sub.equals("holo")) {
                            return sender.hasPermission("nexus.holo.manage");
                        }
                        return true;
                    })
                    .filter(sub -> sub.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("holo")) {
            if (!sender.hasPermission("nexus.holo.manage")) {
                return Collections.emptyList();
            }
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return HOLO_SUB.stream()
                        .filter(sub -> sub.startsWith(prefix))
                        .toList();
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("move") || args[1].equalsIgnoreCase("remove"))) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.suggestHolograms(prefix);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return "default".startsWith(prefix) ? List.of("default") : Collections.emptyList();
            }
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
