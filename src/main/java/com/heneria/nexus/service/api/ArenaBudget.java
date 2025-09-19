package com.heneria.nexus.service.api;

/**
 * Describes the runtime budgets allocated to an arena instance.
 */
public record ArenaBudget(int maxEntities, int maxItems, int maxProjectiles) {
}
