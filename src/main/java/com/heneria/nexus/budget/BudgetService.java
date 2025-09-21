package com.heneria.nexus.budget;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.service.api.ArenaHandle;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/**
 * Centralises arena related runtime budgets and provides enforcement hooks.
 */
public interface BudgetService extends LifecycleAware {

    void registerArena(ArenaHandle handle);

    void unregisterArena(ArenaHandle handle);

    boolean canSpawn(UUID arenaId, EntityType type, int amount);

    void markPending(UUID arenaId, Entity entity, BudgetType type);

    void cancelPending(Entity entity);

    void recordEntityAdded(Entity entity);

    void recordEntityRemoved(Entity entity);

    void trackParticle(UUID arenaId, int count);

    Optional<BudgetSnapshot> getSnapshot(UUID arenaId);

    Collection<BudgetSnapshot> snapshots();

    Optional<UUID> resolveArenaId(Location location);

    void applySettings(CoreConfig.ArenaSettings settings);
}
