package com.heneria.nexus.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Describes an audit entry to be persisted.
 */
public record AuditEntry(UUID actorUuid,
                         String actorName,
                         AuditActionType actionType,
                         UUID targetUuid,
                         String targetName,
                         String details) {

    public AuditEntry {
        Objects.requireNonNull(actionType, "actionType");
        actorName = normaliseName(actorName);
        targetName = normaliseName(targetName);
        details = details != null ? details.trim() : "";
    }

    private static String normaliseName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
