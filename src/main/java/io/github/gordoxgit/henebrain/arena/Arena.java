package io.github.gordoxgit.henebrain.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a game arena with spawn locations.
 */
public class Arena {

    private int id;
    private String name;
    private int maxPlayers;
    private final List<Location> team1Spawns = new ArrayList<>();
    private final List<Location> team2Spawns = new ArrayList<>();
    private Location spectatorSpawn;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public List<Location> getTeam1Spawns() {
        return team1Spawns;
    }

    public List<Location> getTeam2Spawns() {
        return team2Spawns;
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }
}
