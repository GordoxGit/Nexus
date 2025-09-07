package fr.heneria.nexus.player.repository;

import fr.heneria.nexus.player.model.PlayerProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerRepository {
    CompletableFuture<Optional<PlayerProfile>> findProfileByUUID(UUID uuid);
    CompletableFuture<Void> saveProfile(PlayerProfile profile);
}
