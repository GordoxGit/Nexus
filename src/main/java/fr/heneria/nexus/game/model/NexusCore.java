package fr.heneria.nexus.game.model;

import org.bukkit.Location;

/**
 * Représente l'état d'un Cœur Nexus dans une partie.
 */
public class NexusCore {
    private final Team team;
    private final Location location;
    private final double maxHealth = 100.0;
    private double health = maxHealth;
    private boolean vulnerable = false;
    private int surcharges = 0;

    public NexusCore(Team team, Location location) {
        this.team = team;
        this.location = location;
    }

    public Team getTeam() {
        return team;
    }

    public Location getLocation() {
        return location;
    }

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public boolean isVulnerable() {
        return vulnerable;
    }

    public void addSurcharge() {
        surcharges++;
        if (surcharges >= 2) {
            vulnerable = true;
        }
    }

    public void damage(double amount) {
        if (!vulnerable) {
            return;
        }
        health = Math.max(0, health - amount);
    }

    public boolean isDestroyed() {
        return health <= 0;
    }
}

