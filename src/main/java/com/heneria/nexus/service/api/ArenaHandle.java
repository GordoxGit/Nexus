package com.heneria.nexus.service.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable view over an arena instance managed by {@link ArenaService}.
 */
public final class ArenaHandle {

    private final UUID id;
    private final String mapId;
    private final ArenaMode mode;
    private final AtomicReference<ArenaPhase> phase;
    private final Instant createdAt = Instant.now();

    public ArenaHandle(UUID id, String mapId, ArenaMode mode, ArenaPhase initialPhase) {
        this.id = Objects.requireNonNull(id, "id");
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.phase = new AtomicReference<>(Objects.requireNonNull(initialPhase, "initialPhase"));
    }

    public UUID id() {
        return id;
    }

    public String mapId() {
        return mapId;
    }

    public ArenaMode mode() {
        return mode;
    }

    public ArenaPhase phase() {
        return phase.get();
    }

    public ArenaPhase setPhase(ArenaPhase next) {
        Objects.requireNonNull(next, "next");
        return phase.getAndSet(next);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
