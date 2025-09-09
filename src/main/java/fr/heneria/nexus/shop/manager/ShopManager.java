package fr.heneria.nexus.shop.manager;

import fr.heneria.nexus.shop.model.ShopItem;
import fr.heneria.nexus.shop.repository.ShopRepository;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ShopManager {

    private final ShopRepository repository;
    private final Map<String, List<ShopItem>> itemsByCategory = new ConcurrentHashMap<>();

    public ShopManager(ShopRepository repository) {
        this.repository = repository;
    }

    public void loadItems() {
        itemsByCategory.clear();
        itemsByCategory.putAll(repository.loadAllItems());
    }

    public ShopItem getItem(String category, Material material) {
        return itemsByCategory.getOrDefault(category, Collections.emptyList())
                .stream()
                .filter(i -> i.getMaterial() == material)
                .findFirst()
                .orElse(null);
    }

    public void updateItemPrice(ShopItem item, int newPrice) {
        item.setPrice(newPrice);
    }

    public List<ShopItem> getItemsForCategory(String category) {
        return itemsByCategory.getOrDefault(category, Collections.emptyList());
    }

    public Set<String> getCategories() {
        return itemsByCategory.keySet();
    }
}
