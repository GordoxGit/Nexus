package com.heneria.nexus.config;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregates configuration objects that must be reloaded atomically.
 */
public final class ConfigBundle {

    private final NexusConfig config;
    private final MessageBundle messages;
    private final Instant loadedAt;

    public ConfigBundle(NexusConfig config, MessageBundle messages, Instant loadedAt) {
        this.config = Objects.requireNonNull(config, "config");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
    }

    public NexusConfig config() {
        return config;
    }

    public MessageBundle messages() {
        return messages;
    }

    public Instant loadedAt() {
        return loadedAt;
    }
}
