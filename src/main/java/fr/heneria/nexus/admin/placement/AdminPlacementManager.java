package fr.heneria.nexus.admin.placement;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les sessions de placement de spawns pour les administrateurs.
 */
public class AdminPlacementManager {

    private static AdminPlacementManager instance;

    public static void init() {
        instance = new AdminPlacementManager();
    }

    public static AdminPlacementManager getInstance() {
        return instance;
    }

    private final Map<UUID, SpawnPlacementContext> placementSessions = new ConcurrentHashMap<>();

    private AdminPlacementManager() {
    }

    /**
     * Démarre le mode de placement pour un administrateur.
     */
    public void startPlacementMode(Player admin, SpawnPlacementContext context) {
        placementSessions.put(admin.getUniqueId(), context);
        admin.sendMessage("§eMode placement activé. Cliquez-gauche sur un bloc pour définir le spawn de l'Équipe "
                + context.teamId() + ", Spawn " + context.spawnNumber() + ". Clic-droit pour annuler.");
    }

    /**
     * Termine le mode de placement pour un administrateur.
     */
    public void endPlacementMode(Player admin) {
        endPlacementMode(admin.getUniqueId());
    }

    public void endPlacementMode(UUID adminId) {
        placementSessions.remove(adminId);
    }

    /**
     * Récupère le contexte de placement d'un administrateur.
     */
    public SpawnPlacementContext getPlacementContext(Player admin) {
        return placementSessions.get(admin.getUniqueId());
    }

    /**
     * Vérifie si un administrateur est en mode de placement.
     */
    public boolean isInPlacementMode(Player admin) {
        return placementSessions.containsKey(admin.getUniqueId());
    }
}

