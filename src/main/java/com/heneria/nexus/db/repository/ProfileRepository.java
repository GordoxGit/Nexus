package com.heneria.nexus.db.repository;

import com.heneria.nexus.api.PlayerProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository dedicated to player profile persistence.
 */
public interface ProfileRepository {

    /**
     * Loads the profile stored for the provided player identifier.
     *
     * @param playerUuid unique identifier of the player
     * @return future yielding the stored profile when available
     */
    CompletableFuture<Optional<PlayerProfile>> findByUuid(UUID playerUuid);

    /**
     * Creates or updates the profile for the provided player.
     *
     * @param profile profile instance to persist
     * @return future completed once the profile has been saved
     */
    CompletableFuture<Void> createOrUpdate(PlayerProfile profile);
}
