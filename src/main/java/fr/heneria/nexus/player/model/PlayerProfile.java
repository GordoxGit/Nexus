package fr.heneria.nexus.player.model;

import fr.heneria.nexus.player.rank.PlayerRank;

import java.time.Instant;
import java.util.UUID;

public class PlayerProfile {
    private final UUID playerId;
    private String playerName;
    private int eloRating;
    private PlayerRank rank;
    private long points;
    private final Instant firstLogin;
    private Instant lastLogin;
    private int leaverLevel;

    public PlayerProfile(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.eloRating = 1000;
        this.rank = PlayerRank.UNRANKED;
        this.points = 0;
        this.firstLogin = Instant.now();
        this.lastLogin = this.firstLogin;
        this.leaverLevel = 0;
    }

    public PlayerProfile(UUID playerId, String playerName, int eloRating, PlayerRank rank, long points, Instant firstLogin, Instant lastLogin, int leaverLevel) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.eloRating = eloRating;
        this.rank = rank;
        this.points = points;
        this.firstLogin = firstLogin;
        this.lastLogin = lastLogin;
        this.leaverLevel = leaverLevel;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getEloRating() {
        return eloRating;
    }

    public void setEloRating(int eloRating) {
        this.eloRating = eloRating;
    }

    public PlayerRank getRank() {
        return rank;
    }

    public void setRank(PlayerRank rank) {
        this.rank = rank;
    }

    public long getPoints() {
        return points;
    }

    public void setPoints(long points) {
        this.points = points;
    }

    public Instant getFirstLogin() {
        return firstLogin;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public int getLeaverLevel() {
        return leaverLevel;
    }

    public void setLeaverLevel(int leaverLevel) {
        this.leaverLevel = leaverLevel;
    }
}
