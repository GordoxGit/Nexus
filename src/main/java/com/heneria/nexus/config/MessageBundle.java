package com.heneria.nexus.config;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/**
 * Immutable snapshot of the MiniMessage templates loaded from disk.
 */
public final class MessageBundle {

    private final Map<String, MessageEntry> messages;
    private final Map<String, List<MessageEntry>> listMessages;
    private final Locale locale;
    private final Instant loadedAt;
    private final MessageEntry prefix;

    private MessageBundle(Map<String, MessageEntry> messages,
                          Map<String, List<MessageEntry>> listMessages,
                          MessageEntry prefix,
                          Locale locale,
                          Instant loadedAt) {
        this.messages = Collections.unmodifiableMap(new LinkedHashMap<>(messages));
        this.listMessages = Collections.unmodifiableMap(new LinkedHashMap<>(listMessages));
        this.prefix = prefix;
        this.locale = Objects.requireNonNull(locale, "locale");
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
    }

    public Optional<Component> message(String key) {
        return Optional.ofNullable(messages.get(key)).map(MessageEntry::component);
    }

    public Optional<String> raw(String key) {
        return Optional.ofNullable(messages.get(key)).map(MessageEntry::raw);
    }

    public Optional<MessageEntry> entry(String key) {
        return Optional.ofNullable(messages.get(key));
    }

    public Optional<List<Component>> messageList(String key) {
        return Optional.ofNullable(listMessages.get(key))
                .map(entries -> entries.stream().map(MessageEntry::component).toList());
    }

    public Optional<List<String>> rawList(String key) {
        return Optional.ofNullable(listMessages.get(key))
                .map(entries -> entries.stream().map(MessageEntry::raw).toList());
    }

    public Optional<List<MessageEntry>> entryList(String key) {
        return Optional.ofNullable(listMessages.get(key))
                .map(List::copyOf);
    }

    public Optional<Component> prefix() {
        return Optional.ofNullable(prefix).map(MessageEntry::component);
    }

    public Optional<String> rawPrefix() {
        return Optional.ofNullable(prefix).map(MessageEntry::raw);
    }

    public Optional<MessageEntry> prefixEntry() {
        return Optional.ofNullable(prefix);
    }

    public Locale locale() {
        return locale;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public static Builder builder(Locale locale, Instant loadedAt) {
        return new Builder(locale, loadedAt);
    }

    public record MessageEntry(String key, String raw, Component component) {
        public MessageEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(raw, "raw");
            Objects.requireNonNull(component, "component");
        }
    }

    public static final class Builder {

        private final Locale locale;
        private final Instant loadedAt;
        private final Map<String, MessageEntry> singles = new LinkedHashMap<>();
        private final Map<String, List<MessageEntry>> lists = new LinkedHashMap<>();
        private MessageEntry prefix;

        private Builder(Locale locale, Instant loadedAt) {
            this.locale = Objects.requireNonNull(locale, "locale");
            this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
        }

        public void add(String key, String raw, Component component) {
            singles.put(key, new MessageEntry(key, raw, component));
        }

        public void addList(String key, List<String> raws, List<Component> components) {
            if (raws.size() != components.size()) {
                throw new IllegalArgumentException("raws and components size mismatch");
            }
            List<MessageEntry> entries = new java.util.ArrayList<>(raws.size());
            for (int i = 0; i < raws.size(); i++) {
                entries.add(new MessageEntry(key + "[" + i + "]", raws.get(i), components.get(i)));
            }
            lists.put(key, List.copyOf(entries));
        }

        public void prefix(String raw, Component component) {
            this.prefix = new MessageEntry("prefix", raw, component);
        }

        public boolean isEmpty() {
            return singles.isEmpty() && lists.isEmpty();
        }

        public MessageBundle build() {
            return new MessageBundle(singles, lists, prefix, locale, loadedAt);
        }
    }
}
