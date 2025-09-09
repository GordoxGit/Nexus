package fr.heneria.nexus.admin.placement;

import fr.heneria.nexus.arena.model.ArenaGameObject;
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

    private final Map<UUID, PlacementContext> placementSessions = new ConcurrentHashMap<>();

    private AdminPlacementManager() {
    }

    /**
     * Démarre le mode de placement pour un administrateur.
     */
    public void startPlacementMode(Player admin, PlacementContext context) {
        placementSessions.put(admin.getUniqueId(), context);
        if (context instanceof SpawnPlacementContext spawn) {
            admin.sendMessage("§eMode placement activé. Cliquez-gauche sur un bloc pour définir le spawn de l'Équipe "
                    + spawn.teamId() + ", Spawn " + spawn.spawnNumber() + ". Clic-droit pour annuler.");
        } else if (context instanceof GameObjectPlacementContext objectCtx) {
            ArenaGameObject obj = objectCtx.gameObject();
            admin.sendMessage("§eMode placement activé. Cliquez-gauche pour définir l'emplacement de "
                    + obj.getObjectType() + " " + obj.getObjectIndex() + ". Clic-droit pour annuler.");
        }
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
    public PlacementContext getPlacementContext(Player admin) {
        return placementSessions.get(admin.getUniqueId());
    }

    /**
     * Vérifie si un administrateur est en mode de placement.
     */
    public boolean isInPlacementMode(Player admin) {
        return placementSessions.containsKey(admin.getUniqueId());
    }
}

