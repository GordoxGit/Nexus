package com.heneria.nexus.service.api;

import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;

/**
 * Configuration backing a boss bar instance. Profiles are sourced from the
 * configuration and reused across arenas and players.
 */
public interface BossBarProfile {

    /** Identifier of the profile. */
    String id();

    /** Returns the Adventure colour applied to the bar. */
    BossBar.Color color();

    /** Returns the Adventure overlay applied to the bar. */
    BossBar.Overlay overlay();

    /** Returns the static flags that must be enabled on the bar. */
    Set<BossBar.Flag> flags();

    /**
     * Returns the recommended update cadence in ticks for dynamic bars.
     */
    int updateEveryTicks();

    /**
     * Defines how bar instances are cloned when attached to audiences.
     */
    ClonePolicy clonePolicy();

    enum ClonePolicy {
        /** Shared instance for all audiences. */
        GLOBAL,
        /** Dedicated instance per connected player. */
        PER_PLAYER,
        /** Dedicated instance per arena or logical group. */
        PER_ARENA
    }
}
