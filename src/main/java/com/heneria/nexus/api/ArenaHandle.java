package com.heneria.nexus.api;

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

    /**
     * Creates a new handle backed by the supplied data.
     *
     * @param id unique identifier of the arena instance
     * @param mapId identifier of the map currently loaded
     * @param mode gameplay mode currently active
     * @param initialPhase initial phase recorded for the arena
     */
    public ArenaHandle(UUID id, String mapId, ArenaMode mode, ArenaPhase initialPhase) {
        this.id = Objects.requireNonNull(id, "id");
        this.mapId = Objects.requireNonNull(mapId, "mapId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.phase = new AtomicReference<>(Objects.requireNonNull(initialPhase, "initialPhase"));
    }

    /**
     * Returns the unique identifier of this arena instance.
     *
     * @return immutable UUID identifying the arena
     */
    public UUID id() {
        return id;
    }

    /**
     * Returns the identifier of the map currently bound to the arena.
     *
     * @return map identifier used to spawn the arena
     */
    public String mapId() {
        return mapId;
    }

    /**
     * Returns the gameplay mode currently enforced.
     *
     * @return gameplay mode configured for the arena
     */
    public ArenaMode mode() {
        return mode;
    }

    /**
     * Returns the current arena phase.
     *
     * @return phase stored in this handle
     */
    public ArenaPhase phase() {
        return phase.get();
    }

    /**
     * Atomically stores the supplied phase and returns the previous value.
     *
     * @param next phase to record
     * @return phase that was active before the update
     */
    public ArenaPhase setPhase(ArenaPhase next) {
        Objects.requireNonNull(next, "next");
        return phase.getAndSet(next);
    }

    /**
     * Returns the timestamp at which the handle was created.
     *
     * @return creation timestamp, in UTC
     */
    public Instant createdAt() {
        return createdAt;
    }
}
