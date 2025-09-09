package fr.heneria.nexus.admin.placement;

import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.arena.model.ArenaGameObject;

/**
 * Contexte de placement pour un objet de jeu (ex: cellule d'énergie).
 */
public record GameObjectPlacementContext(Arena arena, ArenaGameObject gameObject) implements PlacementContext {
}
