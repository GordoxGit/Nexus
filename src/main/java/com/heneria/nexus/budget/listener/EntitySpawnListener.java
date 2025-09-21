package com.heneria.nexus.budget.listener;

import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetType;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Intercepts entity spawns (excluding items and projectiles) to enforce budgets.
 */
public final class EntitySpawnListener implements Listener {

    private final BudgetService budgetService;

    public EntitySpawnListener(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        EntityType type = entity.getType();
        if (type == EntityType.PLAYER || type == EntityType.DROPPED_ITEM || entity instanceof Projectile) {
            return;
        }
        Optional<UUID> arenaId = budgetService.resolveArenaId(entity.getLocation());
        if (arenaId.isEmpty()) {
            return;
        }
        if (!budgetService.canSpawn(arenaId.get(), type, 1)) {
            event.setCancelled(true);
            return;
        }
        budgetService.markPending(arenaId.get(), entity, BudgetType.ENTITY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntitySpawnPost(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        EntityType type = entity.getType();
        if (type == EntityType.PLAYER || type == EntityType.DROPPED_ITEM || entity instanceof Projectile) {
            return;
        }
        if (!event.isCancelled()) {
            return;
        }
        budgetService.cancelPending(entity);
    }
}
