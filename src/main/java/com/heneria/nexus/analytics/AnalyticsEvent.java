package com.heneria.nexus.analytics;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Base contract for analytics events captured by the outbox system.
 */
public interface AnalyticsEvent {

    /**
     * Timestamp at which the event occurred.
     *
     * @return immutable timestamp in UTC
     */
    Instant timestamp();

    /**
     * Short machine friendly identifier describing the event type.
     *
     * @return non-null event type
     */
    String eventType();

    /**
     * Optional player identifier related to the event when available.
     *
     * @return optional player UUID
     */
    Optional<UUID> playerId();

    /**
     * Structured payload describing the event.
     *
     * @return immutable map of attributes
     */
    Map<String, Object> data();
}
