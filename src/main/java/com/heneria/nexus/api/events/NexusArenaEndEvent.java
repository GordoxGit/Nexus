package com.heneria.nexus.api.events;

import com.heneria.nexus.api.ArenaHandle;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.scoreboard.Team;

/**
 * Bukkit event fired when a Nexus arena finishes and enters the {@code END} phase.
 */
public final class NexusArenaEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ArenaHandle arena;
    private final Team winner;

    /**
     * Creates a new end event for the supplied arena.
     *
     * @param arena handle describing the arena that has ended
     * @param winner scoreboard team declared as the winner, or {@code null} when unavailable
     */
    public NexusArenaEndEvent(ArenaHandle arena, Team winner) {
        this.arena = Objects.requireNonNull(arena, "arena");
        this.winner = winner;
    }

    /**
     * Returns the arena that has entered the end phase.
     *
     * @return handle for the arena that just completed
     */
    public ArenaHandle getArena() {
        return arena;
    }

    /**
     * Returns the winning team if the match reported one.
     *
     * @return scoreboard team representing the winners, or {@code null} if not known
     */
    public Team getWinner() {
        return winner;
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
