package fr.heneria.nexus.shop.repository;

import fr.heneria.nexus.shop.model.ShopItem;

import java.util.List;
import java.util.Map;

public interface ShopRepository {
    Map<String, List<ShopItem>> loadAllItems();
    void saveItem(ShopItem item);
}
