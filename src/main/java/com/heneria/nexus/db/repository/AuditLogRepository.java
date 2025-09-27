package com.heneria.nexus.db.repository;

import com.heneria.nexus.audit.AuditLogRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository responsible for persisting audit log entries.
 */
public interface AuditLogRepository {

    CompletableFuture<Void> saveAll(Collection<AuditLogRecord> entries);

    CompletableFuture<List<AuditLogRecord>> findRecent(Optional<UUID> subjectUuid,
                                                        Optional<String> subjectName,
                                                        int limit,
                                                        int offset);
}
