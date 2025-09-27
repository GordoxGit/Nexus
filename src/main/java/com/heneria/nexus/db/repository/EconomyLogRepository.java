package com.heneria.nexus.db.repository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists audit entries for economy transactions.
 */
public interface EconomyLogRepository {

    /**
     * Records a new economy transaction.
     *
     * @param playerUuid identifier of the affected account
     * @param amount delta applied to the account (positive for credit, negative for debit)
     * @param balanceAfter resulting balance after the operation
     * @param transactionType semantic type of the transaction
     * @param reason optional human readable description
     * @return future completing when the log entry is persisted
     */
    CompletableFuture<Void> insert(UUID playerUuid,
                                   long amount,
                                   long balanceAfter,
                                   String transactionType,
                                   String reason);
}
