package fr.heneria.nexus.economy.manager;

import fr.heneria.nexus.economy.model.TransactionType;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.model.PlayerProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final PlayerManager playerManager;

    public EconomyManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public CompletableFuture<Boolean> addPoints(UUID playerUuid, long amount, TransactionType type, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = playerManager.getPlayerProfile(playerUuid);
            if (profile == null) {
                return false;
            }
            synchronized (profile) {
                long before = profile.getPoints();
                profile.setPoints(before + amount);
                // TODO: enregistrer la transaction dans la table economy_transactions
            }
            return true;
        });
    }

    public CompletableFuture<Boolean> removePoints(UUID playerUuid, long amount, TransactionType type, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasEnoughPoints(playerUuid, amount)) {
                return false;
            }
            PlayerProfile profile = playerManager.getPlayerProfile(playerUuid);
            if (profile == null) {
                return false;
            }
            synchronized (profile) {
                if (profile.getPoints() < amount) {
                    return false;
                }
                long before = profile.getPoints();
                profile.setPoints(before - amount);
                // TODO: enregistrer la transaction dans la table economy_transactions
                return true;
            }
        });
    }

    public long getPoints(UUID playerUuid) {
        PlayerProfile profile = playerManager.getPlayerProfile(playerUuid);
        return profile != null ? profile.getPoints() : 0L;
    }

    public boolean hasEnoughPoints(UUID playerUuid, long amount) {
        PlayerProfile profile = playerManager.getPlayerProfile(playerUuid);
        return profile != null && profile.getPoints() >= amount;
    }
}
