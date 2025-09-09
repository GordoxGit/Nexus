package fr.heneria.nexus.command;

import fr.heneria.nexus.game.queue.QueueManager;
import fr.heneria.nexus.gui.player.GameModeSelectorGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Commande permettant d'ouvrir le sélecteur de mode de jeu pour rejoindre une file d'attente.
 */
public class PlayCommand implements CommandExecutor {

    private final QueueManager queueManager;
    private final JavaPlugin plugin;

    public PlayCommand(QueueManager queueManager, JavaPlugin plugin) {
        this.queueManager = queueManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Cette commande doit être exécutée par un joueur.");
            return true;
        }
        Player player = (Player) sender;
        new GameModeSelectorGui(queueManager, plugin).open(player);
        return true;
    }
}
