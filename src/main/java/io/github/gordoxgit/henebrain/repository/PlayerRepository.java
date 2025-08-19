package io.github.gordoxgit.henebrain.repository;

import io.github.gordoxgit.henebrain.player.PlayerData;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CRUD operations on player data.
 */
public interface PlayerRepository {
    Optional<PlayerData> findByUUID(UUID uuid);
    void upsert(PlayerData player);
    void updateElo(UUID uuid, int newElo);
    void incrementWins(UUID uuid);
    void incrementLosses(UUID uuid);
}
