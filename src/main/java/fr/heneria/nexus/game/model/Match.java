package fr.heneria.nexus.game.model;

import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.game.phase.GamePhase;
import fr.heneria.nexus.game.phase.PhaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Match {
    private final UUID matchId;
    private final Arena arena;
    private GameState state = GameState.WAITING;
    private final Map<Integer, Team> teams = new ConcurrentHashMap<>();
    private BukkitTask countdownTask;
    private BukkitTask shopPhaseTask;
    private Instant startTime;
    private Instant endTime;
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> deaths = new ConcurrentHashMap<>();
    private GamePhase currentPhase = GamePhase.PREPARATION;
    private PhaseManager phaseManager;
    private final Map<Integer, Integer> nexusSurcharges = new ConcurrentHashMap<>();

    public Match(UUID matchId, Arena arena) {
        this.matchId = matchId;
        this.arena = arena;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Arena getArena() {
        return arena;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Map<Integer, Team> getTeams() {
        return teams;
    }

    public void addTeam(Team team) {
        teams.put(team.getTeamId(), team);
    }

    public BukkitTask getCountdownTask() {
        return countdownTask;
    }

    public void setCountdownTask(BukkitTask countdownTask) {
        this.countdownTask = countdownTask;
    }

    public BukkitTask getShopPhaseTask() {
        return shopPhaseTask;
    }

    public void setShopPhaseTask(BukkitTask shopPhaseTask) {
        this.shopPhaseTask = shopPhaseTask;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Set<UUID> getPlayers() {
        Set<UUID> all = new HashSet<>();
        for (Team t : teams.values()) {
            all.addAll(t.getPlayers());
        }
        return all;
    }

    public Team getTeamOfPlayer(UUID playerId) {
        for (Team t : teams.values()) {
            if (t.getPlayers().contains(playerId)) {
                return t;
            }
        }
        return null;
    }

    public void broadcastMessage(String message) {
        for (UUID playerId : getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public void incrementKill(UUID playerId) {
        kills.merge(playerId, 1, Integer::sum);
    }

    public void incrementDeath(UUID playerId) {
        deaths.merge(playerId, 1, Integer::sum);
    }

    public int getKills(UUID playerId) {
        return kills.getOrDefault(playerId, 0);
    }

    public int getDeaths(UUID playerId) {
        return deaths.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> getKillsMap() {
        return kills;
    }

    public Map<UUID, Integer> getDeathsMap() {
        return deaths;
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(GamePhase currentPhase) {
        this.currentPhase = currentPhase;
    }

    public PhaseManager getPhaseManager() {
        return phaseManager;
    }

    public void setPhaseManager(PhaseManager phaseManager) {
        this.phaseManager = phaseManager;
    }

    public void addSurcharge(int teamId) {
        nexusSurcharges.merge(teamId, 1, Integer::sum);
    }

    public int getSurchargeCount(int teamId) {
        return nexusSurcharges.getOrDefault(teamId, 0);
    }
}
