package com.heneria.nexus.admin;

import java.util.Objects;
import java.util.UUID;

/**
 * Bundles together the persisted profile and economy state for a player.
 */
public record PlayerDataSnapshot(UUID playerId,
                                 String lastKnownName,
                                 PlayerProfileSnapshot profile,
                                 PlayerEconomySnapshot economy) {

    public PlayerDataSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(economy, "economy");
    }
}
