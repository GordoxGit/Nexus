package fr.heneria.nexus.game.objective;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.map.NexusMap;
import fr.heneria.nexus.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ObjectiveManager {

    private final NexusPlugin plugin;
    @Getter
    private final List<NexusCore> nexusList = new ArrayList<>();
    @Getter
    private final List<CapturePoint> capturePoints = new ArrayList<>();

    private BukkitTask captureTask;
    private BukkitTask respawnTask;

    public ObjectiveManager(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    public static ItemStack createCellItem() {
        return new ItemBuilder(Material.NETHER_STAR)
                .name(Component.text("Cellule d'Énergie", NamedTextColor.GOLD))
                .lore(Component.text("Portez-la au Nexus ennemi !", NamedTextColor.GRAY))
                .build();
    }

    public static boolean isCellItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        if (!item.hasItemMeta()) return false;
        // Simple check on name
        return item.getItemMeta().getDisplayName().contains("Cellule d'Énergie");
    }

    public void loadObjectives(NexusMap map, World world) {
        nexusList.clear();
        capturePoints.clear();

        // Load Nexus per Team
        for (Map.Entry<GameTeam, NexusMap.ConfigLocation> entry : map.getTeamNexusLocations().entrySet()) {
            GameTeam team = entry.getKey();
            Location loc = entry.getValue().toLocation(world);

            // Visual check or clear area? The block display will float.
            // Let's clear the block at location to be air if needed, or bedrock.
            // loc.getBlock().setType(Material.BEDROCK); // Optional

            NexusCore nexus = new NexusCore(plugin, loc, team, 100); // 100 HP default
            nexusList.add(nexus);
            nexus.spawn();
        }

        // Load Captures
        if (map.getCaptureConfigs() != null) {
            for (NexusMap.CaptureConfig config : map.getCaptureConfigs()) {
                Location center = config.getCenter().toLocation(world);
                CapturePoint point = new CapturePoint(plugin, config.getId(), center, config.getRadius(), config.getRespawnTime());
                capturePoints.add(point);
                point.spawn();
            }
        }
    }

    public void startLoops() {
        if (captureTask != null) captureTask.cancel();
        captureTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (CapturePoint point : capturePoints) {
                point.run();
            }
        }, 20L, 20L);
    }

    public void stopLoops() {
        if (captureTask != null) {
            captureTask.cancel();
            captureTask = null;
        }
        if (respawnTask != null) {
            respawnTask.cancel();
            respawnTask = null;
        }

        // Cleanup entities
        for (NexusCore nexus : nexusList) {
            nexus.cleanup();
        }
        for (CapturePoint point : capturePoints) {
            point.despawn();
        }
    }

    public void triggerCellRespawn(int delaySeconds) {
        if (respawnTask != null && !respawnTask.isCancelled()) {
             // Already respawning?
             return;
        }

        plugin.getServer().broadcast(Component.text("La Cellule réapparaîtra dans " + delaySeconds + " secondes...", NamedTextColor.GRAY));

        respawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
             for (CapturePoint point : capturePoints) {
                 point.reset();
                 plugin.getServer().broadcast(Component.text("La Cellule est réapparue au centre !", NamedTextColor.GREEN));
             }
             respawnTask = null;
        }, delaySeconds * 20L);
    }

    public NexusCore getNexus(GameTeam team) {
        return nexusList.stream().filter(n -> n.getOwner() == team).findFirst().orElse(null);
    }
}
