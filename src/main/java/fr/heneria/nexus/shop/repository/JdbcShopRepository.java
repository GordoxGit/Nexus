package fr.heneria.nexus.shop.repository;

import fr.heneria.nexus.shop.model.ShopItem;
import org.bukkit.Material;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class JdbcShopRepository implements ShopRepository {

    private final DataSource dataSource;

    public JdbcShopRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<String, List<ShopItem>> loadAllItems() {
        Map<String, List<ShopItem>> items = new HashMap<>();
        String sql = "SELECT id, item_category, material, price, is_enabled, display_name, lore_lines FROM shop_items";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String category = rs.getString("item_category");
                Material material;
                try {
                    material = Material.valueOf(rs.getString("material"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ShopItem item = new ShopItem(
                        rs.getInt("id"),
                        category,
                        material,
                        rs.getInt("price"),
                        rs.getBoolean("is_enabled"),
                        rs.getString("display_name"),
                        rs.getString("lore_lines")
                );
                items.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    @Override
    public void saveItem(ShopItem item) {
        String sql = "INSERT INTO shop_items (item_category, material, price, is_enabled, display_name, lore_lines) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE price = VALUES(price), is_enabled = VALUES(is_enabled), display_name = VALUES(display_name), lore_lines = VALUES(lore_lines)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, item.getCategory());
            stmt.setString(2, item.getMaterial().name());
            stmt.setInt(3, item.getPrice());
            stmt.setBoolean(4, item.isEnabled());
            stmt.setString(5, item.getDisplayName());
            stmt.setString(6, item.getLoreLines());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    item.setId(rs.getInt(1));
                } else if (item.getId() == 0) {
                    String select = "SELECT id FROM shop_items WHERE item_category = ? AND material = ?";
                    try (PreparedStatement sel = connection.prepareStatement(select)) {
                        sel.setString(1, item.getCategory());
                        sel.setString(2, item.getMaterial().name());
                        try (ResultSet rs2 = sel.executeQuery()) {
                            if (rs2.next()) {
                                item.setId(rs2.getInt("id"));
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
