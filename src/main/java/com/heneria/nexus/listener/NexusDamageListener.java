package com.heneria.nexus.listener;

import com.heneria.nexus.service.core.nexus.NexusManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;

/**
 * Listens for block damage attempts against nexus beacons.
 */
public final class NexusDamageListener implements Listener {

    private final NexusManager nexusManager;

    public NexusDamageListener(NexusManager nexusManager) {
        this.nexusManager = nexusManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        nexusManager.handleBlockDamage(event);
    }
}
