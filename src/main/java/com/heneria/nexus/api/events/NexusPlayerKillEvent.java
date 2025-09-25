package com.heneria.nexus.api.events;

import com.heneria.nexus.api.ArenaHandle;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired whenever a player kills another inside a Nexus arena.
 */
public final class NexusPlayerKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final Player killer;
    private final ArenaHandle arena;

    /**
     * Creates a new kill event.
     *
     * @param victim player that died
     * @param killer player credited for the kill, may be {@code null} when not known
     * @param arena arena where the combat happened
     */
    public NexusPlayerKillEvent(Player victim, Player killer, ArenaHandle arena) {
        this.victim = Objects.requireNonNull(victim, "victim");
        this.killer = killer;
        this.arena = Objects.requireNonNull(arena, "arena");
    }

    /**
     * Returns the player that died.
     *
     * @return player that was killed
     */
    public Player getVictim() {
        return victim;
    }

    /**
     * Returns the player credited for the kill.
     *
     * @return killer player or {@code null} if the kill had no attacker
     */
    public Player getKiller() {
        return killer;
    }

    /**
     * Returns the arena where the kill occurred.
     *
     * @return handle describing the active arena
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
