package com.heneria.nexus.api.events;

import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.api.CapturePoint;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a player successfully secures a capture point in a Nexus arena.
 */
public final class NexusCapturePointEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CapturePoint capturePoint;
    private final ArenaHandle arena;

    /**
     * Creates a new capture point event.
     *
     * @param player player that completed the capture
     * @param capturePoint description of the capture point that changed ownership
     * @param arena arena where the capture happened
     */
    public NexusCapturePointEvent(Player player, CapturePoint capturePoint, ArenaHandle arena) {
        this.player = Objects.requireNonNull(player, "player");
        this.capturePoint = Objects.requireNonNull(capturePoint, "capturePoint");
        this.arena = Objects.requireNonNull(arena, "arena");
    }

    /**
     * Returns the player that captured the point.
     *
     * @return player credited for the capture
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the capture point that has been secured.
     *
     * @return immutable description of the capture point
     */
    public CapturePoint getCapturePoint() {
        return capturePoint;
    }

    /**
     * Returns the arena where the capture occurred.
     *
     * @return arena handle for the running arena
     */
    public ArenaHandle getArena() {
        return arena;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the global handler list for this event type.
     *
     * @return the static handler list instance
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
