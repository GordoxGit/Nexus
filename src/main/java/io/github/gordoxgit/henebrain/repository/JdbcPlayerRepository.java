package io.github.gordoxgit.henebrain.repository;

import io.github.gordoxgit.henebrain.database.HikariDataSourceProvider;
import io.github.gordoxgit.henebrain.player.PlayerData;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link PlayerRepository}.
 */
public class JdbcPlayerRepository implements PlayerRepository {

    private final DataSource dataSource;
    private final int retries;
    private final long backoff;

    public JdbcPlayerRepository(JavaPlugin plugin, HikariDataSourceProvider provider) {
        this.dataSource = provider.getDataSource();
        var retry = plugin.getConfig().getConfigurationSection("database.retryPolicy");
        this.retries = retry != null ? retry.getInt("retries", 3) : 3;
        this.backoff = retry != null ? retry.getLong("backoffMs", 250) : 250L;
    }

    private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }

    @FunctionalInterface
    private interface SqlFunction<R> {
        R apply(Connection connection) throws SQLException;
    }

    private <T> T execute(SqlFunction<T> function) throws SQLException {
        SQLException last = null;
        for (int attempt = 1; attempt <= retries; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                return function.apply(connection);
            } catch (SQLException e) {
                last = e;
                if (attempt == retries) {
                    throw e;
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw last;
    }

    @Override
    public Optional<PlayerData> findByUUID(UUID uuid) {
        try {
            return execute(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT elo, wins, losses FROM players WHERE uuid=?")) {
                    ps.setBytes(1, uuidToBytes(uuid));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(new PlayerData(uuid,
                                    rs.getInt("elo"),
                                    rs.getInt("wins"),
                                    rs.getInt("losses")));
                        }
                        return Optional.empty();
                    }
                }
            });
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public void upsert(PlayerData player) {
        try {
            execute(connection -> {
                String sql = "INSERT INTO players (uuid, elo, wins, losses) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE elo=VALUES(elo), wins=VALUES(wins), losses=VALUES(losses)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setBytes(1, uuidToBytes(player.getUuid()));
                    ps.setInt(2, player.getElo());
                    ps.setInt(3, player.getWins());
                    ps.setInt(4, player.getLosses());
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void updateElo(UUID uuid, int newElo) {
        executeSimple("UPDATE players SET elo=? WHERE uuid=?", newElo, uuid);
    }

    @Override
    public void incrementWins(UUID uuid) {
        executeSimple("UPDATE players SET wins = wins + 1 WHERE uuid=?", uuid);
    }

    @Override
    public void incrementLosses(UUID uuid) {
        executeSimple("UPDATE players SET losses = losses + 1 WHERE uuid=?", uuid);
    }

    private void executeSimple(String sql, Object... params) {
        try {
            execute(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (Object param : params) {
                        if (param instanceof Integer) {
                            ps.setInt(index++, (Integer) param);
                        } else if (param instanceof UUID) {
                            ps.setBytes(index++, uuidToBytes((UUID) param));
                        } else {
                            ps.setObject(index++, param);
                        }
                    }
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ignored) {
        }
    }
}
