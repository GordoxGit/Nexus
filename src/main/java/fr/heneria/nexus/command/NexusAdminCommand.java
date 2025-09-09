package fr.heneria.nexus.command;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.gui.admin.AdminMenuGui;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.MatchType;
import fr.heneria.nexus.sanction.SanctionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.*;
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
    private final SanctionManager sanctionManager;

    public NexusAdminCommand(ArenaManager arenaManager, ShopManager shopManager, SanctionManager sanctionManager) {
        this.arenaManager = arenaManager;
        this.shopManager = shopManager;
        this.sanctionManager = sanctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "starttest".equalsIgnoreCase(args[0])) {
            Arena arena = arenaManager.getAllArenas().stream().findFirst().orElse(null);
            if (arena == null) {
                sender.sendMessage("Aucune arène disponible.");
                return true;
            }
            List<UUID> team1 = new ArrayList<>();
            List<UUID> team2 = new ArrayList<>();
            int i = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (i++ % 2 == 0) {
                    team1.add(p.getUniqueId());
                } else {
                    team2.add(p.getUniqueId());
                }
            }
            Match match = GameManager.getInstance().createMatch(arena, Arrays.asList(team1, team2), MatchType.NORMAL);
            GameManager.getInstance().startMatchCountdown(match);
            sender.sendMessage("Match de test lancé.");
            return true;
        }

        if (args.length >= 3 && "sanction".equalsIgnoreCase(args[0]) && "pardon".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("nexus.admin.sanction")) {
                sender.sendMessage("Vous n'avez pas la permission nécessaire.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("Joueur introuvable.");
                return true;
            }
            sanctionManager.pardonLastPenalty(target.getUniqueId());
            sender.sendMessage("Sanction levée pour " + target.getName());
            return true;
        }

        // Affiche l'aide si aucune sous-commande ou demande d'aide
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§6Commandes Nexus :");
            sender.sendMessage("§e/nx admin §7- ouvre le centre de contrôle Nexus");
            sender.sendMessage("§e/nx arena list §7- liste les arènes chargées");
            sender.sendMessage("§e/nx arena save <nom> §7- sauvegarde l'arène spécifiée");
            sender.sendMessage("§e/nx sanction pardon <joueur> §7- annule la dernière sanction du joueur");
            return true;
        }

        // Ouvre le GUI principal pour /nx admin
        if ("admin".equalsIgnoreCase(args[0])) {
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
