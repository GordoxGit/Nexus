package fr.heneria.nexus.admin.placement;

import fr.heneria.nexus.arena.model.Arena;

/**
 * Contexte de placement pour un point de spawn.
 */
public record SpawnPlacementContext(Arena arena, int teamId, int spawnNumber) implements PlacementContext {
}

