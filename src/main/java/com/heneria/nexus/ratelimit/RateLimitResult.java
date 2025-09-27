package com.heneria.nexus.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a rate limit check.
 */
public record RateLimitResult(boolean allowed, Optional<Duration> timeRemaining) {

    public RateLimitResult {
        Objects.requireNonNull(timeRemaining, "timeRemaining");
        if (allowed && timeRemaining.isPresent()) {
            throw new IllegalArgumentException("Allowed result cannot have remaining time");
        }
        timeRemaining.ifPresent(duration -> {
            if (duration.isNegative()) {
                throw new IllegalArgumentException("timeRemaining must be >= 0");
            }
        });
    }

    public static RateLimitResult blocked(Duration timeRemaining) {
        Objects.requireNonNull(timeRemaining, "timeRemaining");
        if (timeRemaining.isNegative()) {
            throw new IllegalArgumentException("timeRemaining must be >= 0");
        }
        return new RateLimitResult(false, Optional.of(timeRemaining));
    }
}
