package com.heneria.nexus.api.region;

import com.heneria.nexus.api.MapDefinition;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Centralized service responsible for managing active region definitions
 * across arenas.
 */
public interface RegionService extends LifecycleAware {

    /**
     * Registers the regions associated with the provided arena instance.
     *
     * @param arenaId unique identifier of the arena
     * @param definition map definition used by the arena
     */
    void registerArena(UUID arenaId, MapDefinition definition);

    /**
     * Removes the regions associated with the provided arena instance.
     *
     * @param arenaId unique identifier of the arena being destroyed
     */
    void unregisterArena(UUID arenaId);

    /**
     * Returns the regions attached to the supplied arena.
     *
     * @param arenaId identifier of the arena
     * @return immutable collection of regions
     */
    Collection<Region> getRegionsForArena(UUID arenaId);

    /**
     * Resolves the effective flags at the provided location.
     *
     * @param location target location
     * @return immutable map of resolved flags
     */
    Map<RegionFlag, Object> getFlagsAt(Location location);

    /**
     * Notifies the service that a player moved, allowing cached state to be
     * refreshed.
     *
     * @param player player that moved inside an arena
     */
    void handlePlayerMove(Player player);

    /**
     * Clears any region side effects applied to the provided player.
     *
     * @param player player leaving the arena context
     */
    void handlePlayerLeave(Player player);
}
