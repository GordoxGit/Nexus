package com.heneria.nexus.config;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detailed report emitted after a configuration reload attempt.
 */
public final class ReloadReport {

    private final boolean success;
    private final long version;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration duration;
    private final List<ValidationMessage> warnings;
    private final List<ValidationMessage> errors;

    private ReloadReport(boolean success,
                         long version,
                         Instant startedAt,
                         Instant completedAt,
                         Duration duration,
                         List<ValidationMessage> warnings,
                         List<ValidationMessage> errors) {
        this.success = success;
        this.version = version;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
        this.duration = Objects.requireNonNull(duration, "duration");
        this.warnings = List.copyOf(warnings);
        this.errors = List.copyOf(errors);
    }

    public boolean success() {
        return success;
    }

    public long version() {
        return version;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Duration duration() {
        return duration;
    }

    public List<ValidationMessage> warnings() {
        return warnings;
    }

    public List<ValidationMessage> errors() {
        return errors;
    }

    public static Builder builder(Instant startedAt) {
        return new Builder(startedAt);
    }

    /**
     * Helper used to accumulate warnings and errors while keeping
     * track of the reload timings.
     */
    public static final class Builder {

        private final Instant startedAt;
        private final List<ValidationMessage> warnings = new ArrayList<>();
        private final List<ValidationMessage> errors = new ArrayList<>();
        private long version;

        private Builder(Instant startedAt) {
            this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        }

        public void warn(String file, String path, String message) {
            warnings.add(new ValidationMessage(file, path, message));
        }

        public void error(String file, String path, String message) {
            errors.add(new ValidationMessage(file, path, message));
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public void version(long version) {
            this.version = version;
        }

        public ReloadReport buildSuccess() {
            Instant completed = Instant.now();
            return new ReloadReport(true, version, startedAt, completed,
                    Duration.between(startedAt, completed), warnings, errors);
        }

        public ReloadReport buildFailure() {
            Instant completed = Instant.now();
            return new ReloadReport(false, version, startedAt, completed,
                    Duration.between(startedAt, completed), warnings, errors);
        }
    }

    public record ValidationMessage(String file, String path, String message) {
        public ValidationMessage {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(message, "message");
        }
    }
}
