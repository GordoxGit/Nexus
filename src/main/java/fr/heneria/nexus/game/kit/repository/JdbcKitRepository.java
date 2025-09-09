package fr.heneria.nexus.game.kit.repository;

import fr.heneria.nexus.game.kit.model.Kit;
import fr.heneria.nexus.game.kit.serializer.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class JdbcKitRepository implements KitRepository {

    private final DataSource dataSource;

    public JdbcKitRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveKit(Kit kit) {
        String sql = "INSERT INTO kits (kit_name, inventory_content) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE inventory_content = VALUES(inventory_content)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, kit.getName());
            stmt.setString(2, ItemSerializer.serialize(kit.getContents()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteKit(String kitName) {
        String sql = "DELETE FROM kits WHERE kit_name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, kitName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Kit> loadAllKits() {
        Map<String, Kit> kits = new HashMap<>();
        String sql = "SELECT kit_name, inventory_content FROM kits";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("kit_name");
                String content = rs.getString("inventory_content");
                ItemStack[] items = ItemSerializer.deserialize(content);
                kits.put(name, new Kit(name, items));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return kits;
    }
}
