package fr.heneria.nexus.arena.repository;

import fr.heneria.nexus.arena.model.Arena;

import java.util.Map;
import java.util.Optional;

public interface ArenaRepository {
    // Sauvegarde une nouvelle arène et met à jour son ID.
    void saveArena(Arena arena);
    // Sauvegarde uniquement les spawns d'une arène existante.
    void saveSpawns(Arena arena);
    // Charge toutes les arènes et leurs spawns.
    Map<Integer, Arena> loadAllArenas();
    // Trouve une arène par son nom.
    Optional<Arena> findArenaByName(String name);
}
