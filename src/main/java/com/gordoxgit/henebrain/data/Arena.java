package com.gordoxgit.henebrain.data;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;

import com.gordoxgit.henebrain.game.GameModeType;

public class Arena {
    private String name;
    private Location lobby;
    private Map<String, Location> teamSpawns;
    private Location point;
    private List<GameModeType> supportedModes;
    private List<Location> barrierLocations;
    private int pointsToWin = 10;

    public Arena(String name, Location lobby, Map<String, Location> teamSpawns, Location point,
                 List<GameModeType> supportedModes, List<Location> barrierLocations) {
        this.name = name;
        this.lobby = lobby;
        this.teamSpawns = teamSpawns;
        this.point = point;
        this.supportedModes = supportedModes;
        this.barrierLocations = barrierLocations;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public Map<String, Location> getTeamSpawns() {
        return teamSpawns;
    }

    public void setTeamSpawns(Map<String, Location> teamSpawns) {
        this.teamSpawns = teamSpawns;
    }

    public Location getPoint() {
        return point;
    }

    public void setPoint(Location point) {
        this.point = point;
    }

    public List<GameModeType> getSupportedModes() {
        return supportedModes;
    }

    public void setSupportedModes(List<GameModeType> supportedModes) {
        this.supportedModes = supportedModes;
    }

    public List<Location> getBarrierLocations() {
        return barrierLocations;
    }

    public void setBarrierLocations(List<Location> barrierLocations) {
        this.barrierLocations = barrierLocations;
    }

    public int getPointsToWin() {
        return pointsToWin;
    }

    public void setPointsToWin(int pointsToWin) {
        this.pointsToWin = pointsToWin;
    }
}

