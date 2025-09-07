package fr.heneria.nexus.player.repository;

import fr.heneria.nexus.player.model.PlayerProfile;
import fr.heneria.nexus.player.rank.PlayerRank;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JdbcPlayerRepository implements PlayerRepository {

    private final DataSource dataSource;

    public JdbcPlayerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findProfileByUUID(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_uuid, player_name, elo_rating, current_rank, total_points, first_login, last_login FROM player_profiles WHERE player_uuid = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String playerName = rs.getString("player_name");
                        int elo = rs.getInt("elo_rating");
                        PlayerRank rank = PlayerRank.valueOf(rs.getString("current_rank"));
                        long points = rs.getLong("total_points");
                        Timestamp firstLoginTs = rs.getTimestamp("first_login");
                        Timestamp lastLoginTs = rs.getTimestamp("last_login");
                        Instant firstLogin = firstLoginTs != null ? firstLoginTs.toInstant() : Instant.now();
                        Instant lastLogin = lastLoginTs != null ? lastLoginTs.toInstant() : Instant.now();
                        PlayerProfile profile = new PlayerProfile(uuid, playerName, elo, rank, points, firstLogin, lastLogin);
                        return Optional.of(profile);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_profiles (player_uuid, player_name, elo_rating, current_rank, total_points, first_login, last_login) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), elo_rating = VALUES(elo_rating), current_rank = VALUES(current_rank), total_points = VALUES(total_points), last_login = VALUES(last_login)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, profile.getPlayerId().toString());
                stmt.setString(2, profile.getPlayerName());
                stmt.setInt(3, profile.getEloRating());
                stmt.setString(4, profile.getRank().name());
                stmt.setLong(5, profile.getPoints());
                stmt.setTimestamp(6, Timestamp.from(profile.getFirstLogin()));
                stmt.setTimestamp(7, Timestamp.from(profile.getLastLogin()));
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
