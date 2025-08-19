package io.github.gordoxgit.henebrain.repository;

import io.github.gordoxgit.henebrain.arena.Arena;

import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting arenas and their spawn points.
 */
public interface ArenaRepository {
    void createArena(String name, int maxPlayers);
    Optional<Arena> findByName(String name);
    List<Arena> loadAllArenas();
    void saveSpawns(Arena arena);
}
