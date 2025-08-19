package io.github.gordoxgit.henebrain.commands;

import io.github.gordoxgit.henebrain.arena.Arena;
import io.github.gordoxgit.henebrain.arena.ArenaManager;
import io.github.gordoxgit.henebrain.repository.ArenaRepository;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

/**
 * Handles /hb arena administrative commands.
 */
public class ArenaCommand implements CommandExecutor {

    private final ArenaRepository repository;
    private final ArenaManager manager;

    public ArenaCommand(ArenaRepository repository, ArenaManager manager) {
        this.repository = repository;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("arena")) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb arena <create|setspawn|setspecspawn|save|list>...");
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "create" -> createArena(sender, args);
            case "setspawn" -> setSpawn(sender, args);
            case "setspecspawn" -> setSpecSpawn(sender, args);
            case "save" -> saveSpawns(sender, args);
            case "list" -> listArenas(sender);
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        }
        return true;
    }

    private void createArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("henebrain.admin.arena")) {
            sender.sendMessage(ChatColor.RED + "Permission required.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb arena create <name> <maxPlayers>");
            return;
        }
        String name = args[2];
        int maxPlayers;
        try {
            maxPlayers = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "maxPlayers must be a number.");
            return;
        }
        repository.createArena(name, maxPlayers);
        repository.findByName(name).ifPresent(manager::addArena);
        sender.sendMessage(ChatColor.GREEN + "Arena created.");
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("henebrain.admin.arena")) {
            sender.sendMessage(ChatColor.RED + "Permission required.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Player only.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb arena setspawn <name> <1|2>");
            return;
        }
        Arena arena = manager.getArena(args[2]);
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena not found.");
            return;
        }
        Location loc = player.getLocation();
        if (args[3].equals("1")) {
            arena.getTeam1Spawns().add(loc);
            sender.sendMessage(ChatColor.GREEN + "Spawn added for team 1.");
        } else if (args[3].equals("2")) {
            arena.getTeam2Spawns().add(loc);
            sender.sendMessage(ChatColor.GREEN + "Spawn added for team 2.");
        } else {
            sender.sendMessage(ChatColor.RED + "Team must be 1 or 2.");
        }
    }

    private void setSpecSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("henebrain.admin.arena")) {
            sender.sendMessage(ChatColor.RED + "Permission required.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Player only.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb arena setspecspawn <name>");
            return;
        }
        Arena arena = manager.getArena(args[2]);
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena not found.");
            return;
        }
        arena.setSpectatorSpawn(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Spectator spawn set.");
    }

    private void saveSpawns(CommandSender sender, String[] args) {
        if (!sender.hasPermission("henebrain.admin.arena")) {
            sender.sendMessage(ChatColor.RED + "Permission required.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /hb arena save <name>");
            return;
        }
        Arena arena = manager.getArena(args[2]);
        if (arena == null) {
            sender.sendMessage(ChatColor.RED + "Arena not found.");
            return;
        }
        repository.saveSpawns(arena);
        sender.sendMessage(ChatColor.GREEN + "Spawns saved.");
    }

    private void listArenas(CommandSender sender) {
        if (!sender.hasPermission("henebrain.admin.arena")) {
            sender.sendMessage(ChatColor.RED + "Permission required.");
            return;
        }
        String names = manager.getAllArenas().stream()
                .map(Arena::getName)
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.GRAY + "Arenas: " + names);
    }
}
