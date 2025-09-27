package com.heneria.nexus.db.repository;

import com.heneria.nexus.match.MatchSnapshot;
import java.time.Instant;
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

    /**
     * Purges completed matches older than the provided timestamp.
     *
     * @param olderThan cutoff instant; matches with an end timestamp strictly before this instant are deleted
     * @return future containing the number of deleted matches once the purge completes
     */
    CompletableFuture<Integer> purgeOldMatches(Instant olderThan);
}
