package fr.heneria.nexus.game.queue;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.game.manager.GameManager;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.MatchType;
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
    private final Map<MatchType, Map<GameMode, MatchmakingQueue>> queues = new EnumMap<>(MatchType.class);
    private final Map<UUID, QueueEntry> playerQueues = new ConcurrentHashMap<>();

    private QueueManager(GameManager gameManager, ArenaManager arenaManager) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
        for (MatchType type : MatchType.values()) {
            Map<GameMode, MatchmakingQueue> map = new EnumMap<>(GameMode.class);
            for (GameMode mode : GameMode.values()) {
                map.put(mode, new MatchmakingQueue(mode));
            }
            queues.put(type, map);
        }
    }

    public static void init(GameManager gameManager, ArenaManager arenaManager) {
        instance = new QueueManager(gameManager, arenaManager);
    }

    public static QueueManager getInstance() {
        return instance;
    }

    public void joinQueue(Player player, MatchType type, GameMode mode) {
        QueueEntry current = playerQueues.get(player.getUniqueId());
        if (current != null && current.type == type && current.mode == mode) {
            leaveQueue(player);
            return;
        }
        if (current != null) {
            queues.get(current.type).get(current.mode).removePlayer(player.getUniqueId());
        }
        MatchmakingQueue queue = queues.get(type).get(mode);
        queue.addPlayer(player.getUniqueId());
        playerQueues.put(player.getUniqueId(), new QueueEntry(type, mode));
        player.sendMessage("\u00a7aVous avez rejoint la file " + type.name() + " " + mode.name());
        if (queue.isFull()) {
            tryStartMatch(queue);
        }
    }

    public void leaveQueue(Player player) {
        QueueEntry entry = playerQueues.remove(player.getUniqueId());
        if (entry != null) {
            queues.get(entry.type).get(entry.mode).removePlayer(player.getUniqueId());
            player.sendMessage("\u00a7cVous avez quitté la file " + entry.type.name() + " " + entry.mode.name());
        }
    }

    public QueueEntry getPlayerQueue(UUID playerId) {
        return playerQueues.get(playerId);
    }

    public int getQueueSize(MatchType type, GameMode mode) {
        Map<GameMode, MatchmakingQueue> map = queues.get(type);
        MatchmakingQueue queue = map == null ? null : map.get(mode);
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
        MatchType type = queues.entrySet().stream()
                .filter(e -> e.getValue().containsValue(queue))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(MatchType.NORMAL);
        Match match = gameManager.createMatch(arena, teams, type);
        gameManager.startMatchCountdown(match);
    }

    public record QueueEntry(MatchType type, GameMode mode) {}
}
