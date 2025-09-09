package fr.heneria.nexus.economy.manager;

import fr.heneria.nexus.economy.model.TransactionType;
import fr.heneria.nexus.player.manager.PlayerManager;
import fr.heneria.nexus.player.model.PlayerProfile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final PlayerManager playerManager;
    private final DataSource dataSource;

    public EconomyManager(PlayerManager playerManager, DataSource dataSource) {
        this.playerManager = playerManager;
        this.dataSource = dataSource;
    }

    public CompletableFuture<Boolean> addPoints(UUID playerUuid, long amount, TransactionType type, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProfile profile = playerManager.getPlayerProfile(playerUuid);
            if (profile == null) {
                return false;
            }
            synchronized (profile) {
                long before = profile.getPoints();
                long after = before + amount;
                profile.setPoints(after);

                String sql = "INSERT INTO economy_transactions (player_uuid, transaction_type, amount, balance_before, balance_after, reason) VALUES (?, ?, ?, ?, ?, ?)";
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, type.name());
                    stmt.setLong(3, amount);
                    stmt.setLong(4, before);
                    stmt.setLong(5, after);
                    stmt.setString(6, reason);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("[EconomyManager] Failed to insert addPoints transaction: " + e.getMessage());
                    return false;
                }
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
                long after = before - amount;
                profile.setPoints(after);

                String sql = "INSERT INTO economy_transactions (player_uuid, transaction_type, amount, balance_before, balance_after, reason) VALUES (?, ?, ?, ?, ?, ?)";
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, type.name());
                    stmt.setLong(3, amount);
                    stmt.setLong(4, before);
                    stmt.setLong(5, after);
                    stmt.setString(6, reason);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("[EconomyManager] Failed to insert removePoints transaction: " + e.getMessage());
                    return false;
                }
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
