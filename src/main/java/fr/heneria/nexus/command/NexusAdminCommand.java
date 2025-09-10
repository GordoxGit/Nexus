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
import fr.heneria.nexus.game.kit.manager.KitManager;
import fr.heneria.nexus.npc.NpcManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import fr.heneria.nexus.utils.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

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
    private final KitManager kitManager;
    private final NpcManager npcManager;

    public NexusAdminCommand(ArenaManager arenaManager, ShopManager shopManager, SanctionManager sanctionManager, KitManager kitManager, NpcManager npcManager) {
        this.arenaManager = arenaManager;
        this.shopManager = shopManager;
        this.sanctionManager = sanctionManager;
        this.kitManager = kitManager;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "starttest".equalsIgnoreCase(args[0])) {
            Arena arena = arenaManager.getAllArenas().stream().findFirst().orElse(null);
            if (arena == null) {
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Aucune arène disponible.", Theme.COLOR_ERROR)));
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
            sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Match de test lancé.", Theme.COLOR_SUCCESS)));
            return true;
        }

        if (args.length >= 3 && "sanction".equalsIgnoreCase(args[0]) && "pardon".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("nexus.admin.sanction")) {
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Vous n'avez pas la permission nécessaire.", Theme.COLOR_ERROR)));
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Joueur introuvable.", Theme.COLOR_ERROR)));
                return true;
            }
            sanctionManager.pardonLastPenalty(target.getUniqueId());
            sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Sanction levée pour ")
                    .append(Component.text(target.getName(), Theme.COLOR_SUCCESS))));
            return true;
        }

        // Affiche l'aide si aucune sous-commande ou demande d'aide
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            Component line = Component.text("---------------------------------------------", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH);
            sender.sendMessage(line);
            sender.sendMessage(Component.text("Nexus Admin Help", Theme.COLOR_PRIMARY, TextDecoration.BOLD));
            sender.sendMessage(Component.text(""));
            sender.sendMessage(Component.text("/nx admin ", NamedTextColor.WHITE)
                    .append(Component.text("- Ouvre le Centre de Contrôle principal.", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/nx sanction pardon <joueur> ", NamedTextColor.WHITE)
                    .append(Component.text("- Pardonne la dernière sanction.", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text(""));
            sender.sendMessage(line);
            return true;
        }

        // Ouvre le GUI principal pour /nx admin
        if ("admin".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Cette commande doit être exécutée par un joueur.", Theme.COLOR_ERROR)));
                return true;
            }
            if (!sender.hasPermission("nexus.admin")) {
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Vous n'avez pas la permission nécessaire.", Theme.COLOR_ERROR)));
                return true;
            }
            new AdminMenuGui(arenaManager, AdminPlacementManager.getInstance(), shopManager, kitManager, npcManager).open((Player) sender);
            return true;
        }

        // Anciennes sous-commandes pour la gestion des arènes
        if (!"arena".equalsIgnoreCase(args[0])) {
            sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Usage: /" + label + " arena <list|save>", Theme.COLOR_ERROR)));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Usage: /" + label + " arena <list|save>", Theme.COLOR_ERROR)));
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "list":
                String arenas = arenaManager.getAllArenas().stream()
                        .map(Arena::getName)
                        .collect(Collectors.joining(", "));
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Arènes: " + arenas, Theme.COLOR_PRIMARY)));
                return true;
            case "save":
                if (args.length < 3) {
                    sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Usage: /" + label + " arena save <nomArène>", Theme.COLOR_ERROR)));
                    return true;
                }
                Arena toSave = arenaManager.getArena(args[2]);
                if (toSave == null) {
                    sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Arène introuvable.", Theme.COLOR_ERROR)));
                    return true;
                }
                arenaManager.saveArena(toSave);
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Arène sauvegardée.", Theme.COLOR_SUCCESS)));
                return true;
            default:
                sender.sendMessage(Theme.PREFIX_MAIN.append(Component.text("Sous-commande inconnue.", Theme.COLOR_ERROR)));
                return true;
        }
    }
}
