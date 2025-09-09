package fr.heneria.nexus.game.kit.repository;

import fr.heneria.nexus.game.kit.model.Kit;

import java.util.Map;

public interface KitRepository {
    void saveKit(Kit kit);
    void deleteKit(String kitName);
    Map<String, Kit> loadAllKits();
}
