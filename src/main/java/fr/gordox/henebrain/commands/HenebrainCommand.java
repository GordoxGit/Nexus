package fr.gordox.henebrain.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import fr.gordox.henebrain.Henebrain;

public class HenebrainCommand implements CommandExecutor, TabCompleter {
    private final Henebrain plugin;
    private final List<SubCommand> subCommands = new ArrayList<>();

    public HenebrainCommand(Henebrain plugin) {
        this.plugin = plugin;
    }

    public void registerSubCommand(SubCommand subCommand) {
        subCommands.add(subCommand);
    }

    public List<SubCommand> getSubCommands() {
        return subCommands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            SubCommand version = getSubCommand("version");
            if (version != null) {
                version.perform(sender, new String[0]);
            }
            return true;
        }

        SubCommand target = getSubCommand(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("unknown-subcommand"));
            return true;
        }

        if (target.getPermission() != null && !target.getPermission().isEmpty() && !sender.hasPermission(target.getPermission())) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("no-permission"));
            return true;
        }

        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, newArgs.length);
        target.perform(sender, newArgs);
        return true;
    }

    private SubCommand getSubCommand(String name) {
        for (SubCommand sub : subCommands) {
            if (sub.getName().equalsIgnoreCase(name)) {
                return sub;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (SubCommand sub : subCommands) {
                String perm = sub.getPermission();
                if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                    if (sub.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(sub.getName());
                    }
                }
            }
            return completions;
        } else if (args.length > 1) {
            SubCommand sub = getSubCommand(args[0]);
            if (sub != null) {
                String perm = sub.getPermission();
                if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                    String[] newArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, newArgs, 0, newArgs.length);
                    return sub.onTabComplete(sender, newArgs);
                }
            }
        }
        return new ArrayList<>();
    }
}
