package com.heneria.nexus.audit;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Query descriptor used when retrieving audit log entries.
 */
public record AuditLogQuery(int page,
                            int pageSize,
                            Optional<UUID> subjectUuid,
                            Optional<String> subjectName) {

    public AuditLogQuery {
        if (page <= 0) {
            throw new IllegalArgumentException("page must be positive");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        subjectUuid = Objects.requireNonNull(subjectUuid, "subjectUuid");
        subjectName = Objects.requireNonNull(subjectName, "subjectName");
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
