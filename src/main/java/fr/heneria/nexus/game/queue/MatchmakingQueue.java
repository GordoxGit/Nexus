package fr.heneria.nexus.game.queue;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Représente une file d'attente pour un mode de jeu spécifique.
 */
public class MatchmakingQueue {

    private final GameMode gameMode;
    private final Queue<UUID> players = new ConcurrentLinkedQueue<>();

    public MatchmakingQueue(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public Queue<UUID> getPlayers() {
        return players;
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public boolean isFull() {
        return players.size() >= gameMode.getRequiredPlayers();
    }
}
