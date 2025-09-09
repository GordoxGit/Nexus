package fr.heneria.nexus.command;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.gui.admin.AdminMenuGui;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.shop.manager.ShopManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

/**
 * Commande principale d'administration de Nexus.
 * <p>
 * /nx ou /nx admin ouvre le centre de contrôle si le joueur a la permission
 * "nexus.admin". Les anciennes sous-commandes /nx arena sont conservées pour
 * le moment.
 */
public class NexusAdminCommand implements CommandExecutor {

    private final ArenaManager arenaManager;
    private final ShopManager shopManager;

    public NexusAdminCommand(ArenaManager arenaManager, ShopManager shopManager) {
        this.arenaManager = arenaManager;
        this.shopManager = shopManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ouvre le GUI principal pour /nx ou /nx admin
        if (args.length == 0 || "admin".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Cette commande doit être exécutée par un joueur.");
                return true;
            }
            if (!sender.hasPermission("nexus.admin")) {
                sender.sendMessage("Vous n'avez pas la permission nécessaire.");
                return true;
            }
            new AdminMenuGui(arenaManager, AdminPlacementManager.getInstance(), shopManager).open((Player) sender);
            return true;
        }

        // Anciennes sous-commandes pour la gestion des arènes
        if (!"arena".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Usage: /" + label + " arena <list|save>");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " arena <list|save>");
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "list":
                String arenas = arenaManager.getAllArenas().stream()
                        .map(Arena::getName)
                        .collect(Collectors.joining(", "));
                sender.sendMessage("Arènes: " + arenas);
                return true;
            case "save":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /" + label + " arena save <nomArène>");
                    return true;
                }
                Arena toSave = arenaManager.getArena(args[2]);
                if (toSave == null) {
                    sender.sendMessage("Arène introuvable.");
                    return true;
                }
                arenaManager.saveArena(toSave);
                sender.sendMessage("Arène sauvegardée.");
                return true;
            default:
                sender.sendMessage("Sous-commande inconnue.");
                return true;
        }
    }
}
