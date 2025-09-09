package fr.heneria.nexus.arena.repository;

import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.arena.model.ArenaGameObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JdbcArenaRepository implements ArenaRepository {

    private final DataSource dataSource;

    public JdbcArenaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveArena(Arena arena) {
        String sql = "INSERT INTO arenas (name, max_players) VALUES (?, ?) ON DUPLICATE KEY UPDATE max_players = VALUES(max_players), id = LAST_INSERT_ID(id)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, arena.getName());
            stmt.setInt(2, arena.getMaxPlayers());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    arena.setId(rs.getInt(1));
                } else {
                    try (PreparedStatement select = connection.prepareStatement("SELECT id FROM arenas WHERE name = ?")) {
                        select.setString(1, arena.getName());
                        try (ResultSet rs2 = select.executeQuery()) {
                            if (rs2.next()) {
                                arena.setId(rs2.getInt("id"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveSpawns(Arena arena) {
        String deleteSql = "DELETE FROM arena_spawns WHERE arena_id = ?";
        String insertSql = "INSERT INTO arena_spawns (arena_id, team_id, spawn_number, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, arena.getId());
                deleteStmt.executeUpdate();
            }
            try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                for (Map.Entry<Integer, Map<Integer, Location>> teamEntry : arena.getSpawns().entrySet()) {
                    int teamId = teamEntry.getKey();
                    for (Map.Entry<Integer, Location> spawnEntry : teamEntry.getValue().entrySet()) {
                        int spawnNumber = spawnEntry.getKey();
                        Location loc = spawnEntry.getValue();
                        insertStmt.setInt(1, arena.getId());
                        insertStmt.setInt(2, teamId);
                        insertStmt.setInt(3, spawnNumber);
                        insertStmt.setString(4, loc.getWorld().getName());
                        insertStmt.setDouble(5, loc.getX());
                        insertStmt.setDouble(6, loc.getY());
                        insertStmt.setDouble(7, loc.getZ());
                        insertStmt.setFloat(8, loc.getYaw());
                        insertStmt.setFloat(9, loc.getPitch());
                        insertStmt.addBatch();
                    }
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveGameObjects(Arena arena) {
        String deleteSql = "DELETE FROM arena_game_objects WHERE arena_id = ?";
        String insertSql = "INSERT INTO arena_game_objects (arena_id, object_type, object_index, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, arena.getId());
                deleteStmt.executeUpdate();
            }
            try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                for (ArenaGameObject obj : arena.getGameObjects()) {
                    if (obj.getLocation() == null) continue;
                    Location loc = obj.getLocation();
                    insertStmt.setInt(1, arena.getId());
                    insertStmt.setString(2, obj.getObjectType());
                    insertStmt.setInt(3, obj.getObjectIndex());
                    insertStmt.setString(4, loc.getWorld().getName());
                    insertStmt.setDouble(5, loc.getX());
                    insertStmt.setDouble(6, loc.getY());
                    insertStmt.setDouble(7, loc.getZ());
                    insertStmt.setFloat(8, loc.getYaw());
                    insertStmt.setFloat(9, loc.getPitch());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<Integer, Arena> loadAllArenas() {
        Map<Integer, Arena> arenas = new HashMap<>();
        String arenaSql = "SELECT id, name, max_players FROM arenas";
        String spawnSql = "SELECT arena_id, team_id, spawn_number, world, x, y, z, yaw, pitch FROM arena_spawns";
        String objectSql = "SELECT arena_id, object_type, object_index, world, x, y, z, yaw, pitch FROM arena_game_objects";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(arenaSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    int maxPlayers = rs.getInt("max_players");
                    Arena arena = new Arena(name, maxPlayers);
                    arena.setId(id);
                    arenas.put(id, arena);
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(spawnSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int arenaId = rs.getInt("arena_id");
                    int teamId = rs.getInt("team_id");
                    int spawnNumber = rs.getInt("spawn_number");
                    String worldName = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    Arena arena = arenas.get(arenaId);
                    if (arena != null) {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            arena.setSpawn(teamId, spawnNumber, new Location(world, x, y, z, yaw, pitch));
                        }
                    }
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(objectSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int arenaId = rs.getInt("arena_id");
                    String type = rs.getString("object_type");
                    int index = rs.getInt("object_index");
                    String worldName = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");
                    Arena arena = arenas.get(arenaId);
                    if (arena != null) {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            Location loc = new Location(world, x, y, z, yaw, pitch);
                            if ("NEXUS_CORE".equalsIgnoreCase(type)) {
                                arena.setNexusCoreLocation(index, loc);
                            } else {
                                arena.setGameObjectLocation(type, index, loc);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return arenas;
    }

    @Override
    public Optional<Arena> findArenaByName(String name) {
        String sql = "SELECT id, name, max_players FROM arenas WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Arena arena = new Arena(rs.getString("name"), rs.getInt("max_players"));
                    arena.setId(rs.getInt("id"));
                    try (PreparedStatement spawnStmt = connection.prepareStatement(
                            "SELECT team_id, spawn_number, world, x, y, z, yaw, pitch FROM arena_spawns WHERE arena_id = ?")) {
                        spawnStmt.setInt(1, arena.getId());
                        try (ResultSet spawnRs = spawnStmt.executeQuery()) {
                            while (spawnRs.next()) {
                                int teamId = spawnRs.getInt("team_id");
                                int spawnNumber = spawnRs.getInt("spawn_number");
                                String worldName = spawnRs.getString("world");
                                double x = spawnRs.getDouble("x");
                                double y = spawnRs.getDouble("y");
                                double z = spawnRs.getDouble("z");
                                float yaw = spawnRs.getFloat("yaw");
                                float pitch = spawnRs.getFloat("pitch");
                                World world = Bukkit.getWorld(worldName);
                                if (world != null) {
                                    arena.setSpawn(teamId, spawnNumber, new Location(world, x, y, z, yaw, pitch));
                                }
                            }
                        }
                    }
                    try (PreparedStatement objStmt = connection.prepareStatement(
                            "SELECT object_type, object_index, world, x, y, z, yaw, pitch FROM arena_game_objects WHERE arena_id = ?")) {
                        objStmt.setInt(1, arena.getId());
                        try (ResultSet objRs = objStmt.executeQuery()) {
                            while (objRs.next()) {
                                String type = objRs.getString("object_type");
                                int index = objRs.getInt("object_index");
                                String worldName = objRs.getString("world");
                                double x = objRs.getDouble("x");
                                double y = objRs.getDouble("y");
                                double z = objRs.getDouble("z");
                                float yaw = objRs.getFloat("yaw");
                                float pitch = objRs.getFloat("pitch");
                                World world = Bukkit.getWorld(worldName);
                                if (world != null) {
                                    Location loc = new Location(world, x, y, z, yaw, pitch);
                                    if ("NEXUS_CORE".equalsIgnoreCase(type)) {
                                        arena.setNexusCoreLocation(index, loc);
                                    } else {
                                        arena.setGameObjectLocation(type, index, loc);
                                    }
                                }
                            }
                        }
                    }
                    return Optional.of(arena);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public void deleteArena(Arena arena) {
        String sql = "DELETE FROM arenas WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, arena.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
