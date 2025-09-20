package com.heneria.nexus.service.api;

import java.util.Map;
import java.util.OptionalLong;

/**
 * Lightweight context exposed to UI resolvers when rendering arena bound
 * messages.
 */
public interface ArenaContext {

    /** Returns the handle of the arena emitting the message. */
    ArenaHandle arena();

    /** Returns the current gameplay phase. */
    ArenaPhase phase();

    /** Returns the remaining time in milliseconds if relevant. */
    OptionalLong remainingTimeMs();

    /** Additional key/value attributes describing the arena state. */
    Map<String, Object> attributes();
}
