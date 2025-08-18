package com.gordoxgit.henebrain.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.data.Arena;
import com.gordoxgit.henebrain.game.Game;
import com.gordoxgit.henebrain.game.GameState;
import com.gordoxgit.henebrain.managers.ArenaManager;
import com.gordoxgit.henebrain.managers.GameManager;

/**
 * Main player command handling join logic and delegating admin subcommands.
 */
public class JoinCommand implements CommandExecutor, TabCompleter {
    private final Henebrain plugin;
    private final ArenaManager arenaManager;
    private final GameManager gameManager;
    private final AdminCommand adminCommand;

    public JoinCommand(Henebrain plugin, AdminCommand adminCommand) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.gameManager = plugin.getGameManager();
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <subcommand>");
            return true;
        }

        if (args[0].equalsIgnoreCase("join")) {
            handleJoin(sender, args, label);
            return true;
        }

        // Delegate admin commands to the existing AdminCommand
        if (args[0].equalsIgnoreCase("admin")) {
            return adminCommand.onCommand(sender, command, label, args);
        }

        sender.sendMessage("Commande inconnue.");
        return true;
    }

    private void handleJoin(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Cette commande doit être exécutée par un joueur.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " join <nom_arène>");
            return;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("henebrain.join")) {
            player.sendMessage("Vous n'avez pas la permission d'utiliser cette commande.");
            return;
        }
        String arenaName = args[1];
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage("Cette arène n'existe pas.");
            return;
        }

        Game game = gameManager.getGame(arenaName);
        if (game != null && game.getState() != GameState.WAITING) {
            player.sendMessage("Une partie est déjà en cours dans cette arène.");
            return;
        }

        gameManager.addPlayerToGame(player, arenaName);
        player.sendMessage("Vous avez rejoint l'arène " + arenaName + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("join", "admin");
            StringUtil.copyPartialMatches(args[0], subs, completions);
            return completions;
        }

        if (args[0].equalsIgnoreCase("join")) {
            if (args.length == 2) {
                List<String> arenaNames = arenaManager.getAllArenas().stream().map(Arena::getName).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], arenaNames, completions);
            }
            return completions;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            return adminCommand.onTabComplete(sender, command, alias, args);
        }

        return completions;
    }
}
