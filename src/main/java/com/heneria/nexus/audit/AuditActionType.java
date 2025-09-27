package com.heneria.nexus.audit;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enumerates the different audit action categories persisted in the database.
 */
public enum AuditActionType {

    ADMIN_COMMAND("ADMIN_COMMAND", "Commande admin"),
    PLAYER_PURCHASE("PLAYER_PURCHASE", "Achat boutique"),
    SYSTEM_EVENT("SYSTEM_EVENT", "Événement système"),
    UNKNOWN("UNKNOWN", "Inconnu");

    private static final Map<String, AuditActionType> LOOKUP = new ConcurrentHashMap<>();

    static {
        for (AuditActionType type : values()) {
            LOOKUP.put(type.databaseValue, type);
        }
    }

    private final String databaseValue;
    private final String displayName;

    AuditActionType(String databaseValue, String displayName) {
        this.databaseValue = Objects.requireNonNull(databaseValue, "databaseValue");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Identifier persisted in the database for this action type.
     */
    public String databaseValue() {
        return databaseValue;
    }

    /**
     * Human readable representation of the action type.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Resolves an {@link AuditActionType} from a persisted value.
     */
    public static AuditActionType fromDatabaseValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        AuditActionType type = LOOKUP.get(value);
        if (type != null) {
            return type;
        }
        String normalised = value.toUpperCase(Locale.ROOT);
        return LOOKUP.getOrDefault(normalised, UNKNOWN);
    }
}
