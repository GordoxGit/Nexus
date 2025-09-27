package com.heneria.nexus.audit;

import com.heneria.nexus.service.LifecycleAware;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point used to record and query audit logs.
 */
public interface AuditService extends LifecycleAware {

    /**
     * Schedules the provided entry for asynchronous persistence.
     */
    void log(AuditEntry entry);

    /**
     * Retrieves a page of audit logs matching the provided query.
     */
    CompletableFuture<AuditLogPage> query(AuditLogQuery query);

    /**
     * Immutable result returned by {@link #query(AuditLogQuery)}.
     */
    record AuditLogPage(int page, int pageSize, boolean hasNext, java.util.List<AuditLogRecord> entries) {
        public AuditLogPage {
            Objects.requireNonNull(entries, "entries");
        }
    }
}
