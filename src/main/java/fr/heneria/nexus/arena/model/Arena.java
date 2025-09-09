package fr.heneria.nexus.arena.model;

import org.bukkit.Location;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

/**
 * Représente une arène en mémoire.
 */
public class Arena {
    private int id;
    private final String name;
    private final int maxPlayers;
    // Map<TeamID, Map<SpawnNumber, Location>>
    private final Map<Integer, Map<Integer, Location>> spawns;
    private final List<ArenaGameObject> gameObjects;

    public Arena(String name, int maxPlayers) {
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.spawns = new ConcurrentHashMap<>();
        this.gameObjects = new CopyOnWriteArrayList<>();
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

    public List<ArenaGameObject> getGameObjects() {
        return gameObjects;
    }

    public Optional<ArenaGameObject> getGameObject(String type, int index) {
        return gameObjects.stream()
                .filter(obj -> obj.getObjectType().equalsIgnoreCase(type) && obj.getObjectIndex() == index)
                .findFirst();
    }

    /**
     * Récupère le Cœur Nexus d'une équipe si configuré.
     *
     * @param teamId identifiant de l'équipe
     * @return l'objet de jeu correspondant ou un {@link Optional} vide
     */
    public Optional<ArenaGameObject> getNexusCore(int teamId) {
        return getGameObject("NEXUS_CORE", teamId);
    }

    // Méthode pour ajouter/modifier un spawn
    public void setSpawn(int teamId, int spawnNumber, Location location) {
        spawns.computeIfAbsent(teamId, k -> new ConcurrentHashMap<>()).put(spawnNumber, location);
    }

    public void setGameObjectLocation(String type, int index, Location location) {
        ArenaGameObject obj = getGameObject(type, index).orElseGet(() -> {
            ArenaGameObject newObj = new ArenaGameObject(type, index);
            gameObjects.add(newObj);
            return newObj;
        });
        obj.setLocation(location);
    }

    /**
     * Définit la position du Cœur Nexus d'une équipe.
     *
     * @param teamId   identifiant de l'équipe
     * @param location position du Cœur Nexus
     */
    public void setNexusCoreLocation(int teamId, Location location) {
        setGameObjectLocation("NEXUS_CORE", teamId, location);
    }
}
