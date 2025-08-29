package fr.heneria.nexus.arena.model;

import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Représente une arène en mémoire.
 */
public class Arena {
    private int id;
    private final String name;
    private final int maxPlayers;
    // Map<TeamID, Map<SpawnNumber, Location>>
    private final Map<Integer, Map<Integer, Location>> spawns;

    public Arena(String name, int maxPlayers) {
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.spawns = new ConcurrentHashMap<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public Map<Integer, Map<Integer, Location>> getSpawns() {
        return spawns;
    }

    // Méthode pour ajouter/modifier un spawn
    public void setSpawn(int teamId, int spawnNumber, Location location) {
        spawns.computeIfAbsent(teamId, k -> new ConcurrentHashMap<>()).put(spawnNumber, location);
    }
}
