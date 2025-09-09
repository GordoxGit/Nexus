package fr.heneria.nexus.game.queue;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.model.Match;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les différentes files d'attente de matchmaking.
 */
public class QueueManager {

    private static QueueManager instance;

    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    private final Map<GameMode, MatchmakingQueue> queues = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> playerQueues = new ConcurrentHashMap<>();

    private QueueManager(GameManager gameManager, ArenaManager arenaManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
        for (GameMode mode : GameMode.values()) {
            queues.put(mode, new MatchmakingQueue(mode));
        }
    }

    public static void init(GameManager gameManager, ArenaManager arenaManager) {
        instance = new QueueManager(gameManager, arenaManager);
    }

    public static QueueManager getInstance() {
        return instance;
    }

    public void joinQueue(Player player, GameMode mode) {
        GameMode current = playerQueues.get(player.getUniqueId());
        if (current == mode) {
            // déjà dans cette file, quitter
            leaveQueue(player);
            return;
        }
        if (current != null) {
            queues.get(current).removePlayer(player.getUniqueId());
        }
        MatchmakingQueue queue = queues.get(mode);
        queue.addPlayer(player.getUniqueId());
        playerQueues.put(player.getUniqueId(), mode);
        player.sendMessage("\u00a7aVous avez rejoint la file " + mode.name());
        if (queue.isFull()) {
            tryStartMatch(queue);
        }
    }

    public void leaveQueue(Player player) {
        GameMode mode = playerQueues.remove(player.getUniqueId());
        if (mode != null) {
            queues.get(mode).removePlayer(player.getUniqueId());
            player.sendMessage("\u00a7cVous avez quitté la file " + mode.name());
        }
    }

    public GameMode getPlayerQueue(UUID playerId) {
        return playerQueues.get(playerId);
    }

    public int getQueueSize(GameMode mode) {
        MatchmakingQueue queue = queues.get(mode);
        return queue == null ? 0 : queue.getPlayers().size();
    }

    private void tryStartMatch(MatchmakingQueue queue) {
        // Cherche une arène libre et compatible
        Arena arena = arenaManager.getAllArenas().stream()
                .filter(a -> a.getMaxPlayers() >= queue.getGameMode().getRequiredPlayers())
                .filter(a -> !gameManager.isArenaInUse(a))
                .findFirst().orElse(null);
        if (arena == null) {
            return;
        }
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < queue.getGameMode().getRequiredPlayers(); i++) {
            UUID uuid = queue.getPlayers().poll();
            if (uuid != null) {
                players.add(uuid);
                playerQueues.remove(uuid);
            }
        }
        if (players.size() < queue.getGameMode().getRequiredPlayers()) {
            // pas assez de joueurs (devrait pas arriver)
            players.forEach(uuid -> queue.addPlayer(uuid));
            return;
        }
        int teamSize = players.size() / 2;
        List<List<UUID>> teams = new ArrayList<>();
        teams.add(new ArrayList<>(players.subList(0, teamSize)));
        teams.add(new ArrayList<>(players.subList(teamSize, players.size())));
        Match match = gameManager.createMatch(arena, teams);
        gameManager.startMatchCountdown(match);
    }
}
