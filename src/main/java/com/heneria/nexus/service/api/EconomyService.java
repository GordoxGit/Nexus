package com.heneria.nexus.service.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Persistent currency management for the Nexus network.
 */
public interface EconomyService extends LifecycleAware {

    CompletionStage<Long> getBalance(UUID accountId);

    CompletionStage<Long> credit(UUID accountId, long amount, String reason);

    CompletionStage<Long> debit(UUID accountId, long amount, String reason) throws EconomyException;

    CompletionStage<EconomyTransferResult> transfer(UUID from, UUID to, long amount, String reason) throws EconomyException;

    EconomyTransaction beginTransaction();

    void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings);

    boolean isDegraded();
}
