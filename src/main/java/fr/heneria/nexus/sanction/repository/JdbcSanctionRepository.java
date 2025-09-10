package fr.heneria.nexus.sanction.repository;

import fr.heneria.nexus.sanction.model.Sanction;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcSanctionRepository implements SanctionRepository {

    private final DataSource dataSource;

    public JdbcSanctionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Sanction sanction) {
        String sql = "INSERT INTO player_sanctions (player_uuid, sanction_type, expiration_time, reason) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sanction.getPlayerUuid().toString());
            stmt.setString(2, sanction.getSanctionType());
            if (sanction.getExpirationTime() != null) {
                stmt.setTimestamp(3, Timestamp.from(sanction.getExpirationTime()));
            } else {
                stmt.setNull(3, Types.TIMESTAMP);
            }
            stmt.setString(4, sanction.getReason());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Sanction> findActiveSanction(UUID playerId, String sanctionType) {
        String sql = "SELECT id, player_uuid, sanction_type, expiration_time, sanction_date, is_active, reason " +
                "FROM player_sanctions WHERE player_uuid = ? AND sanction_type = ? AND is_active = TRUE " +
                "AND (expiration_time IS NULL OR expiration_time > CURRENT_TIMESTAMP) " +
                "ORDER BY sanction_date DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, sanctionType);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String uuidStr = rs.getString("player_uuid");
                    String type = rs.getString("sanction_type");
                    Timestamp exp = rs.getTimestamp("expiration_time");
                    Timestamp date = rs.getTimestamp("sanction_date");
                    boolean active = rs.getBoolean("is_active");
                    String reason = rs.getString("reason");
                    Instant expiration = exp != null ? exp.toInstant() : null;
                    Instant sanctionDate = date != null ? date.toInstant() : null;
                    Sanction sanction = new Sanction(id, UUID.fromString(uuidStr), type, expiration, sanctionDate, active, reason);
                    return Optional.of(sanction);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void deactivateLastSanction(UUID playerId) {
        String sql = "UPDATE player_sanctions SET is_active = FALSE WHERE player_uuid = ? AND is_active = TRUE ORDER BY sanction_date DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Sanction> findSanctionsByUuid(UUID playerUuid) {
        String sql = "SELECT id, player_uuid, sanction_type, expiration_time, sanction_date, is_active, reason " +
                "FROM player_sanctions WHERE player_uuid = ? ORDER BY sanction_date DESC";
        List<Sanction> sanctions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String uuidStr = rs.getString("player_uuid");
                    String type = rs.getString("sanction_type");
                    Timestamp exp = rs.getTimestamp("expiration_time");
                    Timestamp date = rs.getTimestamp("sanction_date");
                    boolean active = rs.getBoolean("is_active");
                    String reason = rs.getString("reason");
                    Instant expiration = exp != null ? exp.toInstant() : null;
                    Instant sanctionDate = date != null ? date.toInstant() : null;
                    sanctions.add(new Sanction(id, UUID.fromString(uuidStr), type, expiration, sanctionDate, active, reason));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return sanctions;
    }
}
