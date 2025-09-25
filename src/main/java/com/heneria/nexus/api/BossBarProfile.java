package com.heneria.nexus.api;

import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;

/**
 * Configuration backing a boss bar instance. Profiles are sourced from the
 * configuration and reused across arenas and players.
 */
public interface BossBarProfile {

    /**
     * Identifier of the profile.
     *
     * @return unique identifier of the profile
     */
    String id();

    /**
     * Returns the Adventure colour applied to the bar.
     *
     * @return colour configured for the bar
     */
    BossBar.Color color();

    /**
     * Returns the Adventure overlay applied to the bar.
     *
     * @return overlay style applied to the bar
     */
    BossBar.Overlay overlay();

    /**
     * Returns the static flags that must be enabled on the bar.
     *
     * @return immutable set of Adventure flags
     */
    Set<BossBar.Flag> flags();

    /**
     * Returns the recommended update cadence in ticks for dynamic bars.
     *
     * @return tick interval between refreshes
     */
    int updateEveryTicks();

    /**
     * Defines how bar instances are cloned when attached to audiences.
     *
     * @return clone policy describing how handles are shared
     */
    ClonePolicy clonePolicy();

    enum ClonePolicy {
        /**
         * Shared instance for all audiences.
         */
        GLOBAL,
        /**
         * Dedicated instance per connected player.
         */
        PER_PLAYER,
        /**
         * Dedicated instance per arena or logical group.
         */
        PER_ARENA
    }
}
