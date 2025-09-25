package com.heneria.nexus.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Represents a transactional batch of economy operations.
 */
public interface EconomyTransaction extends AutoCloseable {

    /**
     * Queues a credit operation to be applied when the transaction commits.
     *
     * @param account identifier of the account to credit
     * @param amount amount to add, in minor units
     * @param reason audit log reason associated with the credit
     */
    void credit(UUID account, long amount, String reason);

    /**
     * Queues a debit operation to be applied when the transaction commits.
     *
     * @param account identifier of the account to debit
     * @param amount amount to remove, in minor units
     * @param reason audit log reason associated with the debit
     * @throws EconomyException when the debit cannot be scheduled
     */
    void debit(UUID account, long amount, String reason) throws EconomyException;

    /**
     * Commits the transaction and applies the queued operations.
     *
     * @return asynchronous stage completing when the transaction is persisted
     */
    CompletionStage<Void> commit();

    /**
     * Cancels the transaction and clears all queued operations.
     */
    void rollback();

    /**
     * Automatically rolls back the transaction when used with try-with-resources.
     */
    @Override
    default void close() {
        rollback();
    }
}
