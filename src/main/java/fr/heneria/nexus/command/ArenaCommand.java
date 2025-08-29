package fr.heneria.nexus.command;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class ArenaCommand implements CommandExecutor {

    private final ArenaManager arenaManager;

    public ArenaCommand(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !"arena".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Usage: /" + label + " arena <create|list|setspawn|save>");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " arena <create|list|setspawn|save>");
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create":
                if (args.length < 4) {
                    sender.sendMessage("Usage: /" + label + " arena create <nom> <maxJoueurs>");
                    return true;
                }
                String name = args[2];
                int maxPlayers;
                try {
                    maxPlayers = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("maxJoueurs doit être un nombre");
                    return true;
                }
                arenaManager.createArena(name, maxPlayers);
                sender.sendMessage("Arène " + name + " créée en mémoire. N'oubliez pas de la sauvegarder.");
                return true;
            case "list":
                String arenas = arenaManager.getAllArenas().stream()
                        .map(Arena::getName)
                        .collect(Collectors.joining(", "));
                sender.sendMessage("Arènes: " + arenas);
                return true;
            case "setspawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Cette commande doit être exécutée par un joueur.");
                    return true;
                }
                if (args.length < 5) {
                    sender.sendMessage("Usage: /" + label + " arena setspawn <nomArène> <équipe> <numéroSpawn>");
                    return true;
                }
                Arena arena = arenaManager.getArena(args[2]);
                if (arena == null) {
                    sender.sendMessage("Arène introuvable.");
                    return true;
                }
                int teamId;
                int spawnNumber;
                try {
                    teamId = Integer.parseInt(args[3]);
                    spawnNumber = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Équipe et numéro de spawn doivent être des nombres.");
                    return true;
                }
                Location loc = ((Player) sender).getLocation();
                arena.setSpawn(teamId, spawnNumber, loc);
                sender.sendMessage("Spawn défini pour l'arène " + arena.getName() + ".");
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
