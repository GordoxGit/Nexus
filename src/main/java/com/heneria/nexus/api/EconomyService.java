package com.heneria.nexus.api;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Persistent currency management for the Nexus network.
 */
public interface EconomyService extends LifecycleAware {

    /**
     * Returns the current balance of an account.
     *
     * @param accountId identifier of the account to query
     * @return asynchronous stage resolving to the account balance in minor units
     */
    CompletionStage<Long> getBalance(UUID accountId);

    /**
     * Credits the provided amount to an account.
     *
     * @param accountId identifier of the account to credit
     * @param amount amount to add, in minor units
     * @param reason human readable reason recorded in audit logs
     * @return asynchronous stage resolving to the new balance
     */
    CompletionStage<Long> credit(UUID accountId, long amount, String reason);

    /**
     * Debits the provided amount from an account.
     *
     * @param accountId identifier of the account to debit
     * @param amount amount to remove, in minor units
     * @param reason human readable reason recorded in audit logs
     * @return asynchronous stage resolving to the new balance
     * @throws EconomyException when the operation cannot be performed
     */
    CompletionStage<Long> debit(UUID accountId, long amount, String reason) throws EconomyException;

    /**
     * Transfers an amount between two accounts.
     *
     * @param from identifier of the source account
     * @param to identifier of the target account
     * @param amount amount to transfer, in minor units
     * @param reason human readable reason recorded in audit logs
     * @return asynchronous stage resolving to the transfer result
     * @throws EconomyException when the operation cannot be performed
     */
    CompletionStage<EconomyTransferResult> transfer(UUID from, UUID to, long amount, String reason) throws EconomyException;

    /**
     * Starts a multi-step transaction combining several operations atomically.
     *
     * @return transaction builder used to schedule operations
     */
    EconomyTransaction beginTransaction();

    /**
     * Applies new degraded mode settings controlling fallback behaviour.
     *
     * @param settings effective degraded mode configuration
     */
    void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings);

    /**
     * Returns whether the economy is currently operating in degraded mode.
     *
     * @return {@code true} when the economy backend is degraded
     */
    boolean isDegraded();
}
