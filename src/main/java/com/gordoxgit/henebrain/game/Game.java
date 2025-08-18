package com.gordoxgit.henebrain.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.data.Arena;
import com.gordoxgit.henebrain.data.Team;
import com.gordoxgit.henebrain.managers.LoadoutManager;
import com.gordoxgit.henebrain.managers.TeamManager;

public class Game {
    private String arenaName;
    private GameState state;
    private GameModeType mode;
    private List<Team> teams = new ArrayList<>();
    private Map<UUID, Player> players = new HashMap<>();
    private Map<Team, Integer> scores;
    private List<BlockState> placedBlocks;

    public Game(String arenaName, GameModeType mode) {
        this.arenaName = arenaName;
        this.mode = mode;
        this.state = GameState.WAITING;
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public GameModeType getMode() {
        return mode;
    }

    public void setMode(GameModeType mode) {
        this.mode = mode;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public void setTeams(List<Team> teams) {
        this.teams = teams;
    }

    public Map<UUID, Player> getPlayers() {
        return players;
    }

    public void setPlayers(Map<UUID, Player> players) {
        this.players = players;
    }

    public Map<Team, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<Team, Integer> scores) {
        this.scores = scores;
    }

    public List<BlockState> getPlacedBlocks() {
        return placedBlocks;
    }

    public void setPlacedBlocks(List<BlockState> placedBlocks) {
        this.placedBlocks = placedBlocks;
    }

    /**
     * Starts a 10 second countdown before the game actually begins.
     */
    public void startCountdown() {
        if (state != GameState.WAITING) {
            return;
        }
        setState(GameState.STARTING);
        new BukkitRunnable() {
            int counter = 10;

            @Override
            public void run() {
                if (counter <= 0) {
                    cancel();
                    start();
                    return;
                }

                for (Player p : players.values()) {
                    p.sendMessage("La partie commence dans " + counter + " secondes !");
                }
                counter--;
            }
        }.runTaskTimer(Henebrain.getInstance(), 0L, 20L);
    }

    /**
     * Starts the game after the countdown.
     */
    public void start() {
        TeamManager teamManager = Henebrain.getInstance().getTeamManager();
        LoadoutManager loadoutManager = Henebrain.getInstance().getLoadoutManager();
        Arena arena = Henebrain.getInstance().getArenaManager().getArena(arenaName);

        teamManager.balanceTeams(this);
        setState(GameState.PLAYING);

        for (Team team : teams) {
            Location spawn = arena.getTeamSpawns().get(team.getTeamName());
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    if (spawn != null) {
                        p.teleport(spawn);
                    }
                    loadoutManager.applyLoadout(p);
                }
            }
        }
    }

    /**
     * Ends the game and cleans up after 10 seconds.
     *
     * @param winner the winning team
     */
    public void end(Team winner) {
        if (state == GameState.ENDING) {
            return;
        }
        setState(GameState.ENDING);
        for (Player p : players.values()) {
            if (winner != null) {
                p.sendMessage("L'équipe " + winner.getTeamName() + " a gagné !");
            } else {
                p.sendMessage("La partie est terminée.");
            }
        }

        new BukkitRunnable() {
            int counter = 10;

            @Override
            public void run() {
                if (counter <= 0) {
                    cancel();
                    Arena arena = Henebrain.getInstance().getArenaManager().getArena(arenaName);
                    Location lobby = arena != null ? arena.getLobby() : null;
                    for (Player p : players.values()) {
                        if (lobby != null) {
                            p.teleport(lobby);
                        }
                    }
                    Henebrain.getInstance().getGameManager().cleanupGame(Game.this);
                    return;
                }
                counter--;
            }
        }.runTaskTimer(Henebrain.getInstance(), 0L, 20L);
    }
}

