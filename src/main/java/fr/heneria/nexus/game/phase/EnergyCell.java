package fr.heneria.nexus.game.phase;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente une cellule d'énergie active dans une partie.
 */
public class EnergyCell {
    private final Location location;
    private final Map<Integer, Double> captureProgress = new HashMap<>();

    public EnergyCell(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public Map<Integer, Double> getCaptureProgress() {
        return captureProgress;
    }

}
