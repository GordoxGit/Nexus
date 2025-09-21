package com.heneria.nexus.budget.listener;

import com.heneria.nexus.budget.BudgetService;
import com.heneria.nexus.budget.BudgetType;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * Enforces item stack budgets for arenas.
 */
public final class ItemSpawnListener implements Listener {

    private final BudgetService budgetService;

    public ItemSpawnListener(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        Optional<UUID> arenaId = budgetService.resolveArenaId(entity.getLocation());
        if (arenaId.isEmpty()) {
            return;
        }
        if (!budgetService.canSpawn(arenaId.get(), entity.getType(), 1)) {
            event.setCancelled(true);
            return;
        }
        budgetService.markPending(arenaId.get(), entity, BudgetType.ITEM);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onItemSpawnPost(ItemSpawnEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        budgetService.cancelPending(event.getEntity());
    }
}
