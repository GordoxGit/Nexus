package com.heneria.nexus.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a persisted audit log row.
 */
public record AuditLogRecord(long id,
                             Instant timestamp,
                             UUID actorUuid,
                             String actorName,
                             AuditActionType actionType,
                             UUID targetUuid,
                             String targetName,
                             String details) {

    public AuditLogRecord {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(actionType, "actionType");
        details = details != null ? details : "";
    }

    public AuditLogRecord withId(long newId) {
        return new AuditLogRecord(newId, timestamp, actorUuid, actorName, actionType, targetUuid, targetName, details);
    }
}
