package com.heneria.nexus.api.events;

import com.heneria.nexus.api.ArenaHandle;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a Nexus arena transitions to the {@code PLAYING} phase.
 */
public final class NexusArenaStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ArenaHandle arena;

    /**
     * Creates a new start event for the supplied arena.
     *
     * @param arena handle describing the arena that just started
     */
    public NexusArenaStartEvent(ArenaHandle arena) {
        this.arena = Objects.requireNonNull(arena, "arena");
    }

    /**
     * Returns the arena that has transitioned to the playing phase.
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
