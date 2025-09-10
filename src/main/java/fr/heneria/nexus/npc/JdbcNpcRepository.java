package fr.heneria.nexus.npc;

import fr.heneria.nexus.npc.model.Npc;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link NpcRepository}.
 */
public class JdbcNpcRepository implements NpcRepository {

    private final DataSource dataSource;

    public JdbcNpcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(Npc npc) {
        String sql = "INSERT INTO npcs (npc_name, world, x, y, z, yaw, pitch, click_command) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, npc.getName());
            ps.setString(2, npc.getWorld());
            ps.setDouble(3, npc.getX());
            ps.setDouble(4, npc.getY());
            ps.setDouble(5, npc.getZ());
            ps.setFloat(6, npc.getYaw());
            ps.setFloat(7, npc.getPitch());
            ps.setString(8, npc.getClickCommand());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    npc.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Npc npc) {
        String sql = "UPDATE npcs SET npc_name=?, world=?, x=?, y=?, z=?, yaw=?, pitch=?, click_command=? WHERE id=?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, npc.getName());
            ps.setString(2, npc.getWorld());
            ps.setDouble(3, npc.getX());
            ps.setDouble(4, npc.getY());
            ps.setDouble(5, npc.getZ());
            ps.setFloat(6, npc.getYaw());
            ps.setFloat(7, npc.getPitch());
            ps.setString(8, npc.getClickCommand());
            ps.setInt(9, npc.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(int id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM npcs WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Npc> findAll() {
        List<Npc> list = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM npcs")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public Optional<Npc> findById(int id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM npcs WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Npc mapRow(ResultSet rs) throws SQLException {
        return new Npc(
                rs.getInt("id"),
                rs.getString("npc_name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getString("click_command")
        );
    }
}

