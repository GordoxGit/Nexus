package com.heneria.nexus.budget;

import com.heneria.nexus.service.api.ArenaMode;
import java.util.UUID;

/**
 * Immutable snapshot describing the live counters of an arena budget.
 */
public record BudgetSnapshot(UUID arenaId,
                              String mapId,
                              ArenaMode mode,
                              long entities,
                              long pendingEntities,
                              long maxEntities,
                              long items,
                              long pendingItems,
                              long maxItems,
                              long projectiles,
                              long pendingProjectiles,
                              long maxProjectiles,
                              long particles,
                              long particlesSoftCap,
                              long particlesHardCap) {
}
