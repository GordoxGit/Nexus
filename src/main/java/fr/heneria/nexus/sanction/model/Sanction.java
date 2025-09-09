package fr.heneria.nexus.sanction.model;

import java.time.Instant;
import java.util.UUID;

public class Sanction {
    private final int id;
    private final UUID playerUuid;
    private final String sanctionType;
    private final Instant expirationTime;
    private final Instant sanctionDate;
    private final boolean active;
    private final String reason;

    public Sanction(int id, UUID playerUuid, String sanctionType, Instant expirationTime,
                    Instant sanctionDate, boolean active, String reason) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.sanctionType = sanctionType;
        this.expirationTime = expirationTime;
        this.sanctionDate = sanctionDate;
        this.active = active;
        this.reason = reason;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getSanctionType() {
        return sanctionType;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public Instant getSanctionDate() {
        return sanctionDate;
    }

    public boolean isActive() {
        return active;
    }

    public String getReason() {
        return reason;
    }
}
