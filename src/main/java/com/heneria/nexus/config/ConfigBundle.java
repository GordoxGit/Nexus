package com.heneria.nexus.config;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregates configuration objects that must be reloaded atomically.
 */
public final class ConfigBundle {

    private final long version;
    private final CoreConfig core;
    private final MessageBundle messages;
    private final MapsCatalogConfig maps;
    private final EconomyConfig economy;
    private final Instant loadedAt;

    public ConfigBundle(long version,
                        CoreConfig core,
                        MessageBundle messages,
                        MapsCatalogConfig maps,
                        EconomyConfig economy,
                        Instant loadedAt) {
        this.version = version;
        this.core = Objects.requireNonNull(core, "core");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.maps = Objects.requireNonNull(maps, "maps");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
    }

    public long version() {
        return version;
    }

    public CoreConfig core() {
        return core;
    }

    public MessageBundle messages() {
        return messages;
    }

    public MapsCatalogConfig maps() {
        return maps;
    }

    public EconomyConfig economy() {
        return economy;
    }

    public Instant loadedAt() {
        return loadedAt;
    }
}
