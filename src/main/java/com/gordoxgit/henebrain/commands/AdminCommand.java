package com.gordoxgit.henebrain.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.data.Arena;
import com.gordoxgit.henebrain.game.GameModeType;
import com.gordoxgit.henebrain.managers.ArenaManager;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final Henebrain plugin;
    private final ArenaManager arenaManager;

    public AdminCommand(Henebrain plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " admin <subcommand>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "admin":
                if (!sender.hasPermission("henebrain.admin")) {
                    sender.sendMessage("Vous n'avez pas la permission d'exécuter cette commande.");
                    return true;
                }
                handleAdminCommand(sender, label, Arrays.copyOfRange(args, 1, args.length));
                return true;
            default:
                sender.sendMessage("Commande inconnue.");
                return true;
        }
    }

    private void handleAdminCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " admin <subcommand>");
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " admin create <nom>");
                    return;
                }
                String createName = args[1];
                if (arenaManager.getArena(createName) != null) {
                    sender.sendMessage("Cette arène existe déjà.");
                    return;
                }
                arenaManager.createArena(createName);
                sender.sendMessage("Arène " + createName + " créée.");
                break;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " admin delete <nom>");
                    return;
                }
                String deleteName = args[1];
                if (arenaManager.getArena(deleteName) == null) {
                    sender.sendMessage("Cette arène n'existe pas.");
                    return;
                }
                arenaManager.deleteArena(deleteName);
                sender.sendMessage("Arène " + deleteName + " supprimée.");
                break;
            case "setlobby":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Cette commande doit être exécutée par un joueur.");
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " admin setlobby <nom_arène>");
                    return;
                }
                Player lobbyPlayer = (Player) sender;
                Arena lobbyArena = arenaManager.getArena(args[1]);
                if (lobbyArena == null) {
                    sender.sendMessage("Cette arène n'existe pas.");
                    return;
                }
                lobbyArena.setLobby(lobbyPlayer.getLocation());
                sender.sendMessage("Lobby défini pour l'arène " + lobbyArena.getName() + ".");
                break;
            case "setspawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Cette commande doit être exécutée par un joueur.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /" + label + " admin setspawn <nom_arène> <nom_équipe>");
                    return;
                }
                Player spawnPlayer = (Player) sender;
                Arena spawnArena = arenaManager.getArena(args[1]);
                if (spawnArena == null) {
                    sender.sendMessage("Cette arène n'existe pas.");
                    return;
                }
                Map<String, Location> spawns = spawnArena.getTeamSpawns();
                spawns.put(args[2], spawnPlayer.getLocation());
                spawnArena.setTeamSpawns(spawns);
                sender.sendMessage("Spawn défini pour l'équipe " + args[2] + " dans l'arène " + spawnArena.getName() + ".");
                break;
            case "setpoint":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Cette commande doit être exécutée par un joueur.");
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " admin setpoint <nom_arène>");
                    return;
                }
                Player pointPlayer = (Player) sender;
                Arena pointArena = arenaManager.getArena(args[1]);
                if (pointArena == null) {
                    sender.sendMessage("Cette arène n'existe pas.");
                    return;
                }
                pointArena.setPoint(pointPlayer.getLocation());
                sender.sendMessage("Point défini pour l'arène " + pointArena.getName() + ".");
                break;
            case "addmode":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /" + label + " admin addmode <nom_arène> <mode>");
                    return;
                }
                Arena addArena = arenaManager.getArena(args[1]);
                if (addArena == null) {
                    sender.sendMessage("Cette arène n'existe pas.");
                    return;
                }
                try {
                    GameModeType mode = GameModeType.valueOf(args[2].toUpperCase());
                    if (!addArena.getSupportedModes().contains(mode)) {
                        addArena.getSupportedModes().add(mode);
                        sender.sendMessage("Mode " + mode.name() + " ajouté à l'arène " + addArena.getName() + ".");
                    } else {
                        sender.sendMessage("Ce mode est déjà supporté par l'arène.");
                    }
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("Mode inconnu.");
                }
                break;
            case "removemode":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /" + label + " admin removemode <nom_arène> <mode>");
                    return;
                }
                Arena removeArena = arenaManager.getArena(args[1]);
                if (removeArena == null) {
                    sender.sendMessage("Cette arène n'existe pas.");
                    return;
                }
                try {
                    GameModeType mode = GameModeType.valueOf(args[2].toUpperCase());
                    if (removeArena.getSupportedModes().remove(mode)) {
                        sender.sendMessage("Mode " + mode.name() + " retiré de l'arène " + removeArena.getName() + ".");
                    } else {
                        sender.sendMessage("Ce mode n'est pas supporté par l'arène.");
                    }
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage("Mode inconnu.");
                }
                break;
            default:
                sender.sendMessage("Sous-commande inconnue.");
                break;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Collections.singletonList("admin"), completions);
            return completions;
        }

        if (!args[0].equalsIgnoreCase("admin")) {
            return completions;
        }

        if (args.length == 2) {
            List<String> subs = Arrays.asList("create", "delete", "setlobby", "setspawn", "setpoint", "addmode", "removemode");
            StringUtil.copyPartialMatches(args[1], subs, completions);
            return completions;
        }

        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            if (Arrays.asList("delete", "setlobby", "setspawn", "setpoint", "addmode", "removemode").contains(sub)) {
                List<String> arenaNames = arenaManager.getAllArenas().stream().map(Arena::getName).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], arenaNames, completions);
            }
            return completions;
        }

        if (args.length == 4) {
            String sub = args[1].toLowerCase();
            if (sub.equals("addmode") || sub.equals("removemode")) {
                List<String> modes = Arrays.stream(GameModeType.values()).map(Enum::name).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[3], modes, completions);
            }
            return completions;
        }

        return completions;
    }
}
