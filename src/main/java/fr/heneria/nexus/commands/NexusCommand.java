package fr.heneria.nexus.commands;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

        if (args[0].equalsIgnoreCase("debug") && args.length >= 3) {
            if (!sender.hasPermission("nexus.admin")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args[1].equalsIgnoreCase("setstate")) {
                try {
                    GameState state = GameState.valueOf(args[2].toUpperCase());
                    plugin.getGameManager().setState(state);
                    sender.sendMessage(Component.text("Game state set to " + state, NamedTextColor.GREEN));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("Invalid game state.", NamedTextColor.RED));
                }
                return true;
            }
        } else if (args[0].equalsIgnoreCase("class") && args.length >= 3) {
            if (args[1].equalsIgnoreCase("choose")) {
                if (sender instanceof Player player) {
                    plugin.getClassManager().equipClass(player, args[2]);
                } else {
                    sender.sendMessage(Component.text("Only players can choose a class.", NamedTextColor.RED));
                }
                return true;
            }
        }

        return false;
    }
}
