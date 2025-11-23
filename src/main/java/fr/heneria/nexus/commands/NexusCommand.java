package fr.heneria.nexus.commands;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.commands.subcommands.SetupCommand;
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
    private final SetupCommand setupCommand;

    public NexusCommand(NexusPlugin plugin) {
        this.plugin = plugin;
        this.setupCommand = new SetupCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Default to help if no args? Or return false to show usage
            // The ticket says "Si arg 0 : Proposer game, map, holo, setup, help." in tab completion.
            // But if empty, we can just return false.
            return false;
        }

        if (args[0].equalsIgnoreCase("help")) {
            MiniMessage mm = MiniMessage.miniMessage();
            sender.sendMessage(mm.deserialize("<gradient:#00E7FF:#7A00FF><bold>NEXUS HELP</bold></gradient>"));
            sender.sendMessage(mm.deserialize("<gray>/nexus game <start|stop></gray> - <white>Gérer la partie</white>"));
            sender.sendMessage(mm.deserialize("<gray>/nexus map <load|unload></gray> - <white>Charger un monde</white>"));
            sender.sendMessage(mm.deserialize("<gray>/nexus setup editor <map_id></gray> - <white>Ouvrir le GUI de config</white>"));
            return true;
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
        // /nexus setup editor <map_id>
        else if (args[0].equalsIgnoreCase("setup")) {
            // Forward relevant arguments to SetupCommand
            // args[0] is "setup", so we pass the subarray from index 1
            if (args.length > 1) {
                return setupCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            } else {
                 sender.sendMessage(Component.text("Usage: /nexus setup editor <map_id>", NamedTextColor.RED));
                 return true;
            }
        }

        return false;
    }
}
