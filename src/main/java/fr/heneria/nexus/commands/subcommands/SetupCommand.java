package fr.heneria.nexus.commands.subcommands;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.gui.SetupGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetupCommand {

    private final NexusPlugin plugin;

    public SetupCommand(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexus.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // /nexus setup editor <map_id>
        if (args.length >= 2 && args[0].equalsIgnoreCase("editor")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use the setup editor.", NamedTextColor.RED));
                return true;
            }
            String mapId = args[1];
            new SetupGUI(plugin, mapId).open(player);
            return true;
        }

        sender.sendMessage(Component.text("Usage: /nexus setup editor <map_id>", NamedTextColor.RED));
        return true;
    }
}
