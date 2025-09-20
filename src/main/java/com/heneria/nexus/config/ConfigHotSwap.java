package com.heneria.nexus.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains the currently active configuration bundle and performs
 * atomic swaps when a full reload succeeds.
 */
public final class ConfigHotSwap {

    private final AtomicReference<ConfigBundle> reference = new AtomicReference<>();
    private final AtomicLong version = new AtomicLong();

    public void initialize(ConfigBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        reference.set(bundle);
        version.set(bundle.version());
    }

    public ConfigBundle current() {
        ConfigBundle bundle = reference.get();
        if (bundle == null) {
            throw new IllegalStateException("Configuration not loaded yet");
        }
        return bundle;
    }

    public long currentVersion() {
        return version.get();
    }

    public long nextVersion() {
        return version.incrementAndGet();
    }

    public void commit(ConfigBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        reference.set(bundle);
        version.set(bundle.version());
    }
}
