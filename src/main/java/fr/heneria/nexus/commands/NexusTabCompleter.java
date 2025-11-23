package fr.heneria.nexus.commands;

import fr.heneria.nexus.NexusPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NexusTabCompleter implements TabCompleter {

    private final NexusPlugin plugin;

    public NexusTabCompleter(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("game", "map", "holo", "setup", "help"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("game")) {
                return filter(Arrays.asList("start", "stop", "setstate"), args[1]);
            }
            if (args[0].equalsIgnoreCase("map")) {
                List<String> sub = new ArrayList<>(Arrays.asList("load", "unload"));
                sub.addAll(plugin.getMapManager().getMapConfig().getMaps().keySet());
                return filter(sub, args[1]);
            }
            if (args[0].equalsIgnoreCase("setup")) {
                return filter(Arrays.asList("editor", "setspawn", "setnexus"), args[1]); // Added editor as per ticket
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("editor")) {
                // Suggest existing maps
                 return filter(new ArrayList<>(plugin.getMapManager().getMapConfig().getMaps().keySet()), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String arg) {
        if (arg == null || arg.isEmpty()) return list;
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(arg.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}
