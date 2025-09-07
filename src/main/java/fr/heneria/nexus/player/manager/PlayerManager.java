package fr.heneria.nexus.player.manager;

import fr.heneria.nexus.player.model.PlayerProfile;
import fr.heneria.nexus.player.repository.PlayerRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final PlayerRepository repository;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerManager(PlayerRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<PlayerProfile> loadProfile(UUID uuid, String playerName) {
        return repository.findProfileByUUID(uuid).thenApply(optional -> {
            PlayerProfile profile = optional.orElseGet(() -> new PlayerProfile(uuid, playerName));
            profile.setPlayerName(playerName);
            profile.setLastLogin(Instant.now());
            cache.put(uuid, profile);
            return profile;
        });
    }

    public Optional<PlayerProfile> getProfile(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public CompletableFuture<Void> saveProfile(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.saveProfile(profile);
    }

    public CompletableFuture<Void> unloadProfile(UUID uuid) {
        PlayerProfile profile = cache.remove(uuid);
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }
        profile.setLastLogin(Instant.now());
        return repository.saveProfile(profile);
    }
}
