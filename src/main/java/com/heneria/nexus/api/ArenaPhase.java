package com.heneria.nexus.api;

/**
 * Enumeration describing the lifecycle of an arena instance.
 */
public enum ArenaPhase {
    /**
     * Waiting lobby where players join before the match starts.
     */
    LOBBY,
    /**
     * Countdown phase preparing the arena before going live.
     */
    STARTING,
    /**
     * Active gameplay phase where objectives can be completed.
     */
    PLAYING,
    /**
     * Phase during which scores are tallied and rewards computed.
     */
    SCORED,
    /**
     * Internal reset phase used to restore the map for future matches.
     */
    RESET,
    /**
     * Terminal state reached once the arena is fully cleaned up.
     */
    END
}
