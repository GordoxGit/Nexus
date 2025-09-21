package com.heneria.nexus.watchdog;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable diagnostic entry describing the outcome of a monitored task.
 */
public record WatchdogReport(String taskName,
                             Instant timestamp,
                             Duration duration,
                             Status status,
                             Optional<Throwable> error) {

    public WatchdogReport {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(status, "status");
        error = Objects.requireNonNullElseGet(error, Optional::empty);
    }

    public enum Status {
        COMPLETED,
        TIMED_OUT,
        FAILED
    }
}
