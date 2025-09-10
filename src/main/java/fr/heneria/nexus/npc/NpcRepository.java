package fr.heneria.nexus.npc;

import fr.heneria.nexus.npc.model.Npc;

import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting NPC definitions.
 */
public interface NpcRepository {

    void save(Npc npc);

    void update(Npc npc);

    void delete(int id);

    List<Npc> findAll();

    Optional<Npc> findById(int id);
}

