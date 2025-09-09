package fr.heneria.nexus.player.manager;

import fr.heneria.nexus.player.model.PlayerProfile;
import fr.heneria.nexus.player.repository.PlayerRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final PlayerRepository playerRepository;
    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();

    public PlayerManager(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void loadPlayerProfile(UUID uuid, String name) {
        playerRepository.findProfileByUUID(uuid).thenAccept(optionalProfile -> {
            PlayerProfile profile = optionalProfile.orElseGet(() -> {
                PlayerProfile newProfile = new PlayerProfile(uuid, name);
                playerRepository.saveProfile(newProfile);
                return newProfile;
            });
            profileCache.put(uuid, profile);
        }).exceptionally(throwable -> {
            // Gérer l'erreur de chargement
            return null;
        });
    }

    public void unloadPlayerProfile(UUID uuid) {
        PlayerProfile profile = profileCache.remove(uuid);
        if (profile != null) {
            playerRepository.saveProfile(profile);
        }
    }

    public PlayerProfile getPlayerProfile(UUID uuid) {
        return profileCache.get(uuid);
    }

    // CORRECTION: Ajout de la méthode pour sauvegarder tous les profils à la désactivation
    public void unloadAllProfiles() {
        profileCache.values().forEach(playerRepository::saveProfile);
        profileCache.clear();
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }
}
