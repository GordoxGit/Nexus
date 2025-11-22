package fr.heneria.nexus.listeners;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.classes.NexusClass;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ClassListener implements Listener {

    private final NexusPlugin plugin;

    public ClassListener(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            NexusClass nexusClass = plugin.getClassManager().getClass(event.getPlayer());
            if (nexusClass != null) {
                // TODO: Move item check to class implementation or make generic
                if (event.getItem() != null && event.getItem().getType() == Material.IRON_SWORD) {
                    nexusClass.onAbility(event.getPlayer());
                }
            }
        }
    }
}
