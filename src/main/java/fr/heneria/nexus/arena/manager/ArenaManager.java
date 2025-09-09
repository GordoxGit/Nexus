package fr.heneria.nexus.arena.manager;

import fr.heneria.nexus.arena.model.Arena;
import fr.heneria.nexus.arena.repository.ArenaRepository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {
    private final ArenaRepository arenaRepository;
    private final Map<String, Arena> arenasByName = new ConcurrentHashMap<>();
    private final Map<UUID, Arena> adminEditingMap = new ConcurrentHashMap<>();

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
        arenaRepository.saveGameObjects(arena);
    }

    public void deleteArena(Arena arena) {
        arenasByName.remove(arena.getName());
        arenaRepository.deleteArena(arena);
    }

    public Arena getArena(String name) {
        return arenasByName.get(name);
    }

    public Collection<Arena> getAllArenas() {
        return arenasByName.values();
    }

    public void setEditingArena(UUID adminId, Arena arena) {
        adminEditingMap.put(adminId, arena);
    }

    public void stopEditing(UUID adminId) {
        adminEditingMap.remove(adminId);
    }

    public Arena getEditingArena(UUID adminId) {
        return adminEditingMap.get(adminId);
    }
}
