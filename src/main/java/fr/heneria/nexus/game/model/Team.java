package fr.heneria.nexus.game.model;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Team {
    private final int teamId;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();

    public Team(int teamId) {
        this.teamId = teamId;
    }

    public int getTeamId() {
        return teamId;
    }

    public void addPlayer(UUID playerId) {
        players.add(playerId);
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    public Set<UUID> getPlayers() {
        return players;
    }
}
