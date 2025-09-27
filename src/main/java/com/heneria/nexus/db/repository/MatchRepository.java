package com.heneria.nexus.db.repository;

import com.heneria.nexus.match.MatchSnapshot;
import java.util.concurrent.CompletableFuture;

/**
 * Repository responsible for persisting match snapshots to the database.
 */
public interface MatchRepository {

    /**
     * Persists the supplied match snapshot asynchronously.
     *
     * @param snapshot snapshot describing the match outcome
     * @return future completing once the snapshot has been persisted
     */
    CompletableFuture<Void> save(MatchSnapshot snapshot);
}
