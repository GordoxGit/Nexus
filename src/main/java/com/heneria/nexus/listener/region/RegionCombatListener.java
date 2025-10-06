package com.heneria.nexus.listener.region;

import com.heneria.nexus.api.region.RegionFlag;
import com.heneria.nexus.api.region.RegionService;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Applies combat related region rules such as PvP or knockback modifiers.
 */
public final class RegionCombatListener implements Listener {

    private final RegionService regionService;

    public RegionCombatListener(RegionService regionService) {
        this.regionService = Objects.requireNonNull(regionService, "regionService");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Location location = event.getEntity().getLocation();
        Map<RegionFlag, Object> flags = regionService.getFlagsAt(location);
        boolean pvpEnabled = RegionFlagUtil.getBoolean(flags, RegionFlag.PVP_ENABLED, true);
        if (!pvpEnabled) {
            event.setCancelled(true);
            return;
        }
        double reduction = Math.max(0D, RegionFlagUtil.getDouble(flags, RegionFlag.KNOCKBACK_REDUCTION, 0D));
        if (reduction <= 0D) {
            return;
        }
        double factor = Math.max(0D, 1D - Math.min(reduction, 1D));
        event.setDamage(event.getDamage() * factor);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Location location = event.getEntity().getLocation();
        Map<RegionFlag, Object> flags = regionService.getFlagsAt(location);
        boolean enabled = RegionFlagUtil.getBoolean(flags, RegionFlag.FALL_DAMAGE, true);
        if (!enabled) {
            event.setCancelled(true);
        }
    }
}
