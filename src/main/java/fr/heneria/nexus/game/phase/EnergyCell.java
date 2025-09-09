package fr.heneria.nexus.game.phase;

import org.bukkit.Location;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente une cellule d'énergie active dans une partie.
 */
public class EnergyCell {
    private final Location location;
    private final Map<Integer, Double> captureProgress = new HashMap<>();
    private final BossBar bossBar;

    public EnergyCell(Location location, BossBar bossBar) {
        this.location = location;
        this.bossBar = bossBar;
    }

    public Location getLocation() {
        return location;
    }

    public Map<Integer, Double> getCaptureProgress() {
        return captureProgress;
    }

    public BossBar getBossBar() {
        return bossBar;
    }
}
