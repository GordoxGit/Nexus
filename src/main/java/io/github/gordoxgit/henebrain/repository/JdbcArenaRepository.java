package io.github.gordoxgit.henebrain.repository;

import io.github.gordoxgit.henebrain.arena.Arena;
import io.github.gordoxgit.henebrain.database.HikariDataSourceProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * JDBC implementation of {@link ArenaRepository}.
 */
public class JdbcArenaRepository implements ArenaRepository {

    private final DataSource dataSource;
    private final int retries;
    private final long backoff;

    public JdbcArenaRepository(JavaPlugin plugin, HikariDataSourceProvider provider) {
        this.dataSource = provider.getDataSource();
        var retry = plugin.getConfig().getConfigurationSection("database.retryPolicy");
        this.retries = retry != null ? retry.getInt("retries", 3) : 3;
        this.backoff = retry != null ? retry.getLong("backoffMs", 250) : 250L;
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
    public void createArena(String name, int maxPlayers) {
        try {
            execute(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO arenas (name, max_players) VALUES (?, ?)")) {
                    ps.setString(1, name);
                    ps.setInt(2, maxPlayers);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ignored) {
        }
    }

    @Override
    public Optional<Arena> findByName(String name) {
        try {
            return execute(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, name, max_players FROM arenas WHERE name=?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        Arena arena = new Arena();
                        arena.setId(rs.getInt("id"));
                        arena.setName(rs.getString("name"));
                        arena.setMaxPlayers(rs.getInt("max_players"));
                        loadSpawns(connection, arena);
                        return Optional.of(arena);
                    }
                }
            });
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Arena> loadAllArenas() {
        try {
            return execute(connection -> {
                Map<Integer, Arena> map = new HashMap<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, name, max_players FROM arenas")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Arena arena = new Arena();
                            arena.setId(rs.getInt("id"));
                            arena.setName(rs.getString("name"));
                            arena.setMaxPlayers(rs.getInt("max_players"));
                            map.put(arena.getId(), arena);
                        }
                    }
                }
                if (map.isEmpty()) {
                    return new ArrayList<>();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT arena_id, spawn_type, world, x, y, z, yaw, pitch FROM arena_spawns")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Arena arena = map.get(rs.getInt("arena_id"));
                            if (arena == null) {
                                continue;
                            }
                            Location loc = new Location(
                                    Bukkit.getWorld(rs.getString("world")),
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch"));
                            String type = rs.getString("spawn_type");
                            switch (type) {
                                case "TEAM_1" -> arena.getTeam1Spawns().add(loc);
                                case "TEAM_2" -> arena.getTeam2Spawns().add(loc);
                                case "SPECTATOR" -> arena.setSpectatorSpawn(loc);
                            }
                        }
                    }
                }
                return new ArrayList<>(map.values());
            });
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    private void loadSpawns(Connection connection, Arena arena) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT spawn_type, world, x, y, z, yaw, pitch FROM arena_spawns WHERE arena_id=?")) {
            ps.setInt(1, arena.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Location loc = new Location(
                            Bukkit.getWorld(rs.getString("world")),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"));
                    String type = rs.getString("spawn_type");
                    switch (type) {
                        case "TEAM_1" -> arena.getTeam1Spawns().add(loc);
                        case "TEAM_2" -> arena.getTeam2Spawns().add(loc);
                        case "SPECTATOR" -> arena.setSpectatorSpawn(loc);
                    }
                }
            }
        }
    }

    @Override
    public void saveSpawns(Arena arena) {
        try {
            execute(connection -> {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM arena_spawns WHERE arena_id=?")) {
                    del.setInt(1, arena.getId());
                    del.executeUpdate();
                }
                String sql = "INSERT INTO arena_spawns (arena_id, spawn_type, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (Location loc : arena.getTeam1Spawns()) {
                        fill(ps, arena.getId(), "TEAM_1", loc);
                        ps.addBatch();
                    }
                    for (Location loc : arena.getTeam2Spawns()) {
                        fill(ps, arena.getId(), "TEAM_2", loc);
                        ps.addBatch();
                    }
                    if (arena.getSpectatorSpawn() != null) {
                        fill(ps, arena.getId(), "SPECTATOR", arena.getSpectatorSpawn());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                return null;
            });
        } catch (SQLException ignored) {
        }
    }

    private void fill(PreparedStatement ps, int arenaId, String type, Location loc) throws SQLException {
        ps.setInt(1, arenaId);
        ps.setString(2, type);
        ps.setString(3, loc.getWorld().getName());
        ps.setDouble(4, loc.getX());
        ps.setDouble(5, loc.getY());
        ps.setDouble(6, loc.getZ());
        ps.setFloat(7, loc.getYaw());
        ps.setFloat(8, loc.getPitch());
    }
}
