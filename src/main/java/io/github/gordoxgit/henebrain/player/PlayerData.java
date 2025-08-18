package io.github.gordoxgit.henebrain.player;

import java.util.UUID;

/**
 * Simple data container for player statistics.
 */
public class PlayerData {

    private final UUID uuid;
    private int elo;
    private int wins;
    private int losses;

    public PlayerData(UUID uuid, int elo, int wins, int losses) {
        this.uuid = uuid;
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getElo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = elo;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }
}
