package com.example.hikabrain;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks players with temporary admin editing rights. */
public class AdminModeService {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    /** Enable admin mode for given player. */
    public void enable(Player p) { enabled.add(p.getUniqueId()); }

    /** Disable admin mode for given player. */
    public void disable(Player p) { enabled.remove(p.getUniqueId()); }

    /** Toggle admin mode and return new state. */
    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (enabled.contains(id)) { enabled.remove(id); return false; }
        enabled.add(id); return true;
    }

    /** Check if player currently has admin mode enabled. */
    public boolean isEnabled(Player p) { return enabled.contains(p.getUniqueId()); }
}
