package com.gordoxgit.henebrain.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.block.BlockState;

import com.gordoxgit.henebrain.data.Team;

public class Game {
    private String arenaName;
    private GameState state;
    private GameModeType mode;
    private List<Team> teams;
    private Map<UUID, Object> players;
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

    public Map<UUID, Object> getPlayers() {
        return players;
    }

    public void setPlayers(Map<UUID, Object> players) {
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
}

