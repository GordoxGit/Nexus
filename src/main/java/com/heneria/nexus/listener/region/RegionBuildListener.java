package com.heneria.nexus.listener.region;

import com.heneria.nexus.api.region.RegionFlag;
import com.heneria.nexus.api.region.RegionService;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Prevents block interactions in regions where building is disabled.
 */
public final class RegionBuildListener implements Listener {

    private final RegionService regionService;

    public RegionBuildListener(RegionService regionService) {
        this.regionService = Objects.requireNonNull(regionService, "regionService");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isBuildAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isBuildAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isBuildAllowed(Location location) {
        Map<RegionFlag, Object> flags = regionService.getFlagsAt(location);
        return RegionFlagUtil.getBoolean(flags, RegionFlag.BUILD_ALLOWED, true);
    }
}
