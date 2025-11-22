package fr.heneria.nexus.commands;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NexusCommand implements CommandExecutor {

    private final NexusPlugin plugin;

    public NexusCommand(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }

        // /nexus debug setstate <state>
        if (args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("nexus.admin")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("setstate")) {
                try {
                    GameState state = GameState.valueOf(args[2].toUpperCase());
                    plugin.getGameManager().setState(state);
                    sender.sendMessage(Component.text("Game state set to " + state, NamedTextColor.GREEN));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid game state.", NamedTextColor.RED));
                }
                return true;
            }
        }
        // /nexus class choose <classname>
        else if (args[0].equalsIgnoreCase("class")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("choose")) {
                if (sender instanceof Player player) {
                    plugin.getClassManager().equipClass(player, args[2]);
                } else {
                    sender.sendMessage(Component.text("Only players can choose a class.", NamedTextColor.RED));
                }
                return true;
            }
        }
        // /nexus map load <template>
        // /nexus map unload
        else if (args[0].equalsIgnoreCase("map")) {
            if (!sender.hasPermission("nexus.admin")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("load")) {
                String template = args[2];
                sender.sendMessage(Component.text("Loading map " + template + "...", NamedTextColor.YELLOW));
                plugin.getMapManager().loadMap(template).thenAccept(world -> {
                    sender.sendMessage(Component.text("Map loaded: " + world.getName(), NamedTextColor.GREEN));
                    if (sender instanceof Player player) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                             player.teleport(world.getSpawnLocation());
                        });
                    }
                }).exceptionally(e -> {
                    sender.sendMessage(Component.text("Failed to load map: " + e.getMessage(), NamedTextColor.RED));
                    return null;
                });
                return true;
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("unload")) {
                sender.sendMessage(Component.text("Unloading map...", NamedTextColor.YELLOW));
                plugin.getMapManager().unloadWorld();
                sender.sendMessage(Component.text("Map unloaded.", NamedTextColor.GREEN));
                return true;
            }
        }
        // /nexus holo create <text>
        else if (args[0].equalsIgnoreCase("holo")) {
            if (!sender.hasPermission("nexus.admin")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("create")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can create holograms.", NamedTextColor.RED));
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                Component component = MiniMessage.miniMessage().deserialize(text);
                plugin.getHoloService().createHologram(player.getLocation(), Collections.singletonList(component));
                sender.sendMessage(Component.text("Hologram created.", NamedTextColor.GREEN));
                return true;
            }
        }

        return false;
    }
}
