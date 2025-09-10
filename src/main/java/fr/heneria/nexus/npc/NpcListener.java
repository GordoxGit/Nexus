package fr.heneria.nexus.npc;

import fr.heneria.nexus.npc.model.Npc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.Optional;

/**
 * Listens for player interactions with NPCs and executes their command.
 */
public class NpcListener implements Listener {

    private final NpcManager npcManager;

    public NpcListener(NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Optional<Npc> npcOpt = npcManager.getNpcByEntity(event.getRightClicked());
        if (npcOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Npc npc = npcOpt.get();
        String command = npc.getClickCommand();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        event.getPlayer().performCommand(command);
    }
}

