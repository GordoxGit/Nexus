package fr.heneria.nexus.arena.manager;

import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.arena.repository.ArenaRepository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {
    private final ArenaRepository arenaRepository;
    private final Map<String, Arena> arenasByName = new ConcurrentHashMap<>();

    public ArenaManager(ArenaRepository arenaRepository) {
        this.arenaRepository = arenaRepository;
    }

    public void loadArenas() {
        Map<Integer, Arena> arenas = arenaRepository.loadAllArenas();
        arenasByName.clear();
        arenas.values().forEach(arena -> arenasByName.put(arena.getName(), arena));
    }

    public Arena createArena(String name, int maxPlayers) {
        Arena arena = new Arena(name, maxPlayers);
        arenasByName.put(name, arena);
        return arena;
    }

    public void saveArena(Arena arena) {
        arenaRepository.saveArena(arena);
        arenaRepository.saveSpawns(arena);
    }

    public Arena getArena(String name) {
        return arenasByName.get(name);
    }

    public Collection<Arena> getAllArenas() {
        return arenasByName.values();
    }
}
