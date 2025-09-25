package com.heneria.nexus.api;

import java.util.Map;
import java.util.OptionalLong;

/**
 * Lightweight context exposed to UI resolvers when rendering arena bound
 * messages.
 */
public interface ArenaContext {

    /**
     * Returns the handle of the arena emitting the message.
     *
     * @return arena handle bound to the current context
     */
    ArenaHandle arena();

    /**
     * Returns the current gameplay phase.
     *
     * @return current arena phase
     */
    ArenaPhase phase();

    /**
     * Returns the remaining time in milliseconds if relevant.
     *
     * @return optional remaining time in milliseconds
     */
    OptionalLong remainingTimeMs();

    /**
     * Additional key/value attributes describing the arena state.
     *
     * @return immutable snapshot of contextual attributes
     */
    Map<String, Object> attributes();
}
