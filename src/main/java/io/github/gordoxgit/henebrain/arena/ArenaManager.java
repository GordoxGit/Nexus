package io.github.gordoxgit.henebrain.arena;

import io.github.gordoxgit.henebrain.repository.ArenaRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages arenas loaded in memory.
 */
public class ArenaManager {

    private final ArenaRepository repository;
    private final Map<String, Arena> arenas = new HashMap<>();

    public ArenaManager(ArenaRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads all arenas from the repository.
     */
    public void loadArenas() {
        arenas.clear();
        for (Arena arena : repository.loadAllArenas()) {
            arenas.put(arena.getName().toLowerCase(), arena);
        }
    }

    public void addArena(Arena arena) {
        arenas.put(arena.getName().toLowerCase(), arena);
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getAllArenas() {
        return arenas.values();
    }
}
