package com.heneria.nexus.api;

/**
 * Describes the runtime budgets allocated to an arena instance.
 *
 * @param maxEntities maximum number of living entities allowed simultaneously
 * @param maxItems maximum number of dropped item entities allowed simultaneously
 * @param maxProjectiles maximum number of projectile entities allowed simultaneously
 */
public record ArenaBudget(int maxEntities, int maxItems, int maxProjectiles) {
}
