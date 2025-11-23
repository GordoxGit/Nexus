package fr.heneria.nexus.game.objective;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.map.NexusMap;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

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

    public ObjectiveManager(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadObjectives(NexusMap map, World world) {
        nexusList.clear();
        capturePoints.clear();

        // Load Nexus (Single)
        if (map.getNexusConfig() != null) {
            NexusMap.NexusConfig config = map.getNexusConfig();
            Location loc = config.getLocation().toLocation(world);

            // Place Beacon block (visual)
            Block block = loc.getBlock();
            block.setType(Material.BEACON);

            // Pass null for owner as it is a central Nexus
            NexusCore nexus = new NexusCore(plugin, loc, null, config.getMaxHealth());
            nexusList.add(nexus);
            nexus.spawn();
        }

        // Load Captures
        if (map.getCaptureConfigs() != null) {
            for (NexusMap.CaptureConfig config : map.getCaptureConfigs()) {
                Location center = config.getCenter().toLocation(world);
                CapturePoint point = new CapturePoint(plugin, config.getId(), center, config.getRadius());
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
    }
}
