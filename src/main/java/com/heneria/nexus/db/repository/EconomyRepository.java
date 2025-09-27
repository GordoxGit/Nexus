package com.heneria.nexus.db.repository;

import com.heneria.nexus.api.EconomyTransferResult;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository dedicated to player economy persistence.
 */
public interface EconomyRepository {

    /**
     * Retrieves the persisted balance for the given account.
     *
     * @param playerUuid unique identifier of the account
     * @return future yielding the current balance in minor units
     */
    CompletableFuture<Long> getBalance(UUID playerUuid);

    /**
     * Sets the balance for the given account.
     *
     * @param playerUuid unique identifier of the account
     * @param balance    new balance in minor units
     * @return future yielding the resulting balance
     */
    CompletableFuture<Long> setBalance(UUID playerUuid, long balance);

    /**
     * Applies a delta to the stored balance.
     *
     * @param playerUuid unique identifier of the account
     * @param amount     delta to apply (positive or negative)
     * @return future yielding the resulting balance
     */
    CompletableFuture<Long> addToBalance(UUID playerUuid, long amount);

    /**
     * Transfers the specified amount between two accounts atomically.
     *
     * @param from   account debited
     * @param to     account credited
     * @param amount amount to transfer in minor units
     * @return future yielding resulting balances for both accounts
     */
    CompletableFuture<EconomyTransferResult> transfer(UUID from, UUID to, long amount);
}
