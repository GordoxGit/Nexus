package com.heneria.nexus.budget.listener;

import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetType;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Guards projectile launches to ensure arena budgets are respected.
 */
public final class ProjectileLaunchListener implements Listener {

    private final BudgetService budgetService;

    public ProjectileLaunchListener(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        Optional<UUID> arenaId = budgetService.resolveArenaId(projectile.getLocation());
        if (arenaId.isEmpty()) {
            return;
        }
        if (!budgetService.canSpawn(arenaId.get(), projectile.getType(), 1)) {
            event.setCancelled(true);
            return;
        }
        budgetService.markPending(arenaId.get(), projectile, BudgetType.PROJECTILE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onProjectileLaunchPost(ProjectileLaunchEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        budgetService.cancelPending(event.getEntity());
    }
}
