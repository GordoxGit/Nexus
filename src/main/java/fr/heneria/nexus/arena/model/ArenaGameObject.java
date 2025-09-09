package fr.heneria.nexus.arena.model;

import org.bukkit.Location;

/**
 * Représente un objet de jeu positionnable dans une arène (ex: cellule d'énergie).
 */
public class ArenaGameObject {
    private final String objectType;
    private final int objectIndex;
    private Location location;

    public ArenaGameObject(String objectType, int objectIndex) {
        this.objectType = objectType;
        this.objectIndex = objectIndex;
    }

    public String getObjectType() {
        return objectType;
    }

    public int getObjectIndex() {
        return objectIndex;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
