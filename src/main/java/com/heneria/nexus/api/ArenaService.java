package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
     *
     * @param arenaId identifier of the arena instance
     * @return optional containing the handle when the arena is active
     */
    Optional<ArenaHandle> getInstance(UUID arenaId);

    /**
     * Returns an immutable snapshot of the currently active arenas.
     *
     * @return collection view of all running arena handles
     */
    Collection<ArenaHandle> instances();

    /**
     * Resolves the spawn location associated with the provided player.
     *
     * @param player player whose spawn should be retrieved
     * @return location of the spawn when known
     */
    default Optional<Location> findSpawnLocation(Player player) {
        Objects.requireNonNull(player, "player");
        return Optional.empty();
    }

    /**
     * Requests a phase transition for the given arena. This method must be
     * invoked from the main server thread and should not block.
     *
     * @param handle handle representing the arena to mutate
     * @param nextPhase phase requested for the arena
     */
    void transition(ArenaHandle handle, ArenaPhase nextPhase);

    /**
     * Retrieves the performance budget applied to the arena.
     *
     * @param handle handle representing the arena to query
     * @return immutable budget snapshot for the arena
     */
    ArenaBudget budget(ArenaHandle handle);

    /**
     * Registers a listener receiving internal arena signals.
     *
     * @param listener listener to register
     */
    void registerListener(ArenaListener listener);

    /**
     * Unregisters a previously registered listener.
     *
     * @param listener listener to unregister
     */
    void unregisterListener(ArenaListener listener);

    /**
     * Applies new arena related settings. Called when the configuration is
     * reloaded.
     *
     * @param settings effective arena settings loaded from configuration
     */
    void applyArenaSettings(CoreConfig.ArenaSettings settings);

    /**
     * Applies new watchdog timeout settings used to monitor long running tasks.
     *
     * @param settings watchdog configuration to apply
     */
    default void applyWatchdogSettings(CoreConfig.TimeoutSettings.WatchdogSettings settings) {
        Objects.requireNonNull(settings, "settings");
    }

    interface ArenaListener {
        /**
         * Called whenever the arena phase changes.
         *
         * @param handle arena emitting the signal
         * @param previous phase that was active before the update
         * @param next phase now active
         */
        void onPhaseChange(ArenaHandle handle, ArenaPhase previous, ArenaPhase next);

        /**
         * Called when the arena begins its reset routine.
         *
         * @param handle arena undergoing a reset
         */
        void onResetStart(ArenaHandle handle);

        /**
         * Called once the arena reset routine completed successfully.
         *
         * @param handle arena that finished resetting
         */
        void onResetEnd(ArenaHandle handle);
    }
}
