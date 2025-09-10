package fr.heneria.nexus.npc;

import fr.heneria.nexus.npc.model.Npc;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads and manages lobby NPCs.
 */
public class NpcManager {

    public static final String UNSET_WORLD = "UNSET";

    private final NpcRepository repository;
    private final Map<UUID, Npc> entityNpcMap = new HashMap<>();
    private final Map<Integer, Npc> npcs = new HashMap<>();

    public NpcManager(NpcRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads all NPCs from the database and spawns those with a valid location.
     */
    public void loadNpcs() {
        for (Npc npc : repository.findAll()) {
            npcs.put(npc.getId(), npc);
            if (!UNSET_WORLD.equalsIgnoreCase(npc.getWorld())) {
                spawnNpc(npc);
            }
        }
    }

    /**
     * Spawns an NPC in the world.
     */
    public void spawnNpc(Npc npc) {
        Location loc = npc.toLocation();
        if (loc == null) return;
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setCustomNameVisible(true);
            as.customName(Component.text(npc.getName()));
            as.setGravity(false);
            as.setAI(false);
            as.setInvulnerable(true);
            as.setPersistent(true);
        });
        entityNpcMap.put(stand.getUniqueId(), npc);
    }

    public Optional<Npc> getNpcByEntity(Entity entity) {
        return Optional.ofNullable(entityNpcMap.get(entity.getUniqueId()));
    }

    public void removeAll() {
        for (UUID uuid : entityNpcMap.keySet()) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
        entityNpcMap.clear();
    }

    public Npc createNpc(String name, String command) {
        Npc npc = new Npc(0, name, UNSET_WORLD, 0, 0, 0, 0, 0, command);
        repository.save(npc);
        npcs.put(npc.getId(), npc);
        return npc;
    }

    public void placeNpc(Npc npc, Player admin) {
        npc.setLocation(admin.getLocation());
        repository.update(npc);
        spawnNpc(npc);
    }

    public void deleteNpc(Npc npc) {
        entityNpcMap.entrySet().removeIf(entry -> {
            if (entry.getValue().getId() == npc.getId()) {
                Entity e = Bukkit.getEntity(entry.getKey());
                if (e != null) e.remove();
                return true;
            }
            return false;
        });
        repository.delete(npc.getId());
        npcs.remove(npc.getId());
    }

    public Map<Integer, Npc> getNpcs() {
        return npcs;
    }
}

