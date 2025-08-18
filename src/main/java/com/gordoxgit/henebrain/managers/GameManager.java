package com.gordoxgit.henebrain.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.data.Arena;
import com.gordoxgit.henebrain.game.Game;
import com.gordoxgit.henebrain.game.GameModeType;

/**
 * Manages all running games.
 */
public class GameManager {
    private final Henebrain plugin;
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> playerGames = new HashMap<>();

    public GameManager(Henebrain plugin) {
        this.plugin = plugin;
    }

    public Game getGame(String arenaName) {
        return games.get(arenaName);
    }

    public Game getGame(Player player) {
        return playerGames.get(player.getUniqueId());
    }

    /**
     * Adds a player to the specified arena game. Creates the game if necessary
     * and starts the countdown when enough players have joined.
     */
    public void addPlayerToGame(Player player, String arenaName) {
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            return;
        }

        Game game = games.get(arenaName);
        if (game == null) {
            game = new Game(arenaName, GameModeType.CLASSIC);
            games.put(arenaName, game);
        }

        game.getPlayers().put(player.getUniqueId(), player);
        playerGames.put(player.getUniqueId(), game);

        if (arena.getLobby() != null) {
            player.teleport(arena.getLobby());
        }

        int minPlayers = 2; // default minimum players
        if (game.getPlayers().size() >= minPlayers) {
            game.startCountdown();
        }
    }

    /**
     * Removes a player from its game.
     */
    public void removePlayerFromGame(Player player) {
        Game game = playerGames.remove(player.getUniqueId());
        if (game != null) {
            game.getPlayers().remove(player.getUniqueId());
            if (game.getPlayers().isEmpty()) {
                cleanupGame(game);
            }
        }
    }

    /**
     * Cleans up a finished game.
     */
    public void cleanupGame(Game game) {
        games.remove(game.getArenaName());
        for (UUID uuid : new ArrayList<>(game.getPlayers().keySet())) {
            playerGames.remove(uuid);
        }
    }
}

