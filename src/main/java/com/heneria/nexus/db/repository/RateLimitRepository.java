package com.heneria.nexus.db.repository;

import com.heneria.nexus.ratelimit.RateLimitResult;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository responsible for persisting rate limiter state in MariaDB.
 */
public interface RateLimitRepository {

    /**
     * Checks whether the rate limited action can be performed and records the new timestamp when allowed.
     *
     * @param playerUuid player identifier
     * @param actionKey unique action key
     * @param cooldown cooldown duration
     * @return asynchronous result describing whether the action is allowed and remaining time otherwise
     */
    CompletableFuture<RateLimitResult> checkAndRecord(UUID playerUuid, String actionKey, Duration cooldown);

    /**
     * Deletes entries older than the provided cutoff.
     *
     * @param cutoff cutoff instant
     * @return future resolving to the number of deleted rows
     */
    CompletableFuture<Integer> purgeOlderThan(Instant cutoff);
}
