package com.heneria.nexus.config;

import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of the MiniMessage templates loaded from disk.
 */
public final class MessageBundle {

    private final Map<String, String> messages;
    private final Locale locale;
    private final Instant loadedAt;

    public MessageBundle(Map<String, String> messages, Locale locale, Instant loadedAt) {
        this.messages = Collections.unmodifiableMap(messages);
        this.locale = Objects.requireNonNull(locale, "locale");
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
    }

    public Optional<String> message(String key) {
        return Optional.ofNullable(messages.get(key));
    }

    public Map<String, String> messages() {
        return messages;
    }

    public Locale locale() {
        return locale;
    }

    public Instant loadedAt() {
        return loadedAt;
    }
}
