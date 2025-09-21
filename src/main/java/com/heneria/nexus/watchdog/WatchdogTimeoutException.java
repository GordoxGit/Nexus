package com.heneria.nexus.watchdog;

import java.time.Duration;
import java.util.Objects;

/**
 * Exception thrown when a watchdog monitored task exceeds its configured timeout.
 */
public final class WatchdogTimeoutException extends RuntimeException {

    private final String taskName;
    private final Duration timeout;

    public WatchdogTimeoutException(String taskName, Duration timeout) {
        super("La tâche '" + Objects.requireNonNull(taskName, "taskName")
                + "' a dépassé le délai de " + Objects.requireNonNull(timeout, "timeout").toMillis() + " ms");
        this.taskName = taskName;
        this.timeout = timeout;
    }

    public String taskName() {
        return taskName;
    }

    public Duration timeout() {
        return timeout;
    }
}
