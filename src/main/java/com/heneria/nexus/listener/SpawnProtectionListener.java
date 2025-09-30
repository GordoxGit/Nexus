package com.heneria.nexus.listener;

import com.heneria.nexus.api.AntiSpawnKillService;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Removes spawn protection when a protected player initiates combat.
 */
public final class SpawnProtectionListener implements Listener {

    private final AntiSpawnKillService antiSpawnKillService;

    public SpawnProtectionListener(AntiSpawnKillService antiSpawnKillService) {
        this.antiSpawnKillService = Objects.requireNonNull(antiSpawnKillService, "antiSpawnKillService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            handleAttack(player);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                handleAttack(player);
            }
        }
    }

    private void handleAttack(Player player) {
        UUID playerId = player.getUniqueId();
        if (!antiSpawnKillService.isProtected(playerId)) {
            return;
        }
        antiSpawnKillService.revokeProtection(playerId);
    }
}
