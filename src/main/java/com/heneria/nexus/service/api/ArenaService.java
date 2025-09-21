package com.heneria.nexus.service.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Core gameplay service managing arena instances and their lifecycle.
 */
public interface ArenaService extends LifecycleAware {

    /**
     * Creates a new arena instance bound to the provided map identifier.
     *
     * @param mapId identifier of the map to load
     * @param mode gameplay mode requested by the queue
     * @param seed optional deterministic seed for world generation related tasks
     * @return handle representing the created arena
     * @throws ArenaCreationException if the map cannot be loaded or the arena budget is exceeded
     */
    ArenaHandle createInstance(String mapId, ArenaMode mode, OptionalLong seed) throws ArenaCreationException;

    /**
     * Returns the arena instance associated with the provided identifier.
     */
    Optional<ArenaHandle> getInstance(UUID arenaId);

    /**
     * Returns an immutable snapshot of the currently active arenas.
     */
    Collection<ArenaHandle> instances();

    /**
     * Requests a phase transition for the given arena. This method must be
     * invoked from the main server thread and should not block.
     */
    void transition(ArenaHandle handle, ArenaPhase nextPhase);

    /**
     * Retrieves the performance budget applied to the arena.
     */
    ArenaBudget budget(ArenaHandle handle);

    /**
     * Registers a listener receiving internal arena signals.
     */
    void registerListener(ArenaListener listener);

    /**
     * Unregisters a previously registered listener.
     */
    void unregisterListener(ArenaListener listener);

    /**
     * Applies new arena related settings. Called when the configuration is
     * reloaded.
     */
    void applyArenaSettings(CoreConfig.ArenaSettings settings);

    default void applyWatchdogSettings(CoreConfig.TimeoutSettings.WatchdogSettings settings) {
        Objects.requireNonNull(settings, "settings");
    }

    interface ArenaListener {
        void onPhaseChange(ArenaHandle handle, ArenaPhase previous, ArenaPhase next);

        void onResetStart(ArenaHandle handle);

        void onResetEnd(ArenaHandle handle);
    }
}
