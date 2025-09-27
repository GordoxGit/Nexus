package com.heneria.nexus.service.core;

import com.heneria.nexus.api.PlayerProfile;
import com.heneria.nexus.service.LifecycleAware;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Coordinates write-behind persistence for player related data.
 */
public interface PersistenceService extends LifecycleAware {

    /**
     * Marks the profile of the provided player as dirty. The supplier will be
     * invoked during the next flush to obtain the most up-to-date snapshot.
     */
    void markProfileDirty(UUID playerId, Supplier<PlayerProfile> snapshotSupplier);

    /**
     * Marks the balance of the provided player as dirty. The supplier will be
     * invoked during the next flush to obtain the current balance.
     */
    void markEconomyDirty(UUID playerId, LongSupplier balanceSupplier);

    /**
     * Forces a synchronous flush of all pending changes. Intended for shutdown
     * sequences.
     */
    void flushAllOnShutdown();

    @Override
    default CompletableFuture<Void> start() {
        return LifecycleAware.super.start();
    }

    @Override
    default CompletableFuture<Void> stop() {
        return LifecycleAware.super.stop();
    }
}
