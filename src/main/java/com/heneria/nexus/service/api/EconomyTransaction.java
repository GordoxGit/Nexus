package com.heneria.nexus.service.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Represents a transactional batch of economy operations.
 */
public interface EconomyTransaction extends AutoCloseable {

    void credit(UUID account, long amount, String reason);

    void debit(UUID account, long amount, String reason) throws EconomyException;

    CompletionStage<Void> commit();

    void rollback();

    @Override
    default void close() {
        rollback();
    }
}
