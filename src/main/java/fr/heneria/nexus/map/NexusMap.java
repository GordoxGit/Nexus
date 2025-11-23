package fr.heneria.nexus.map;

import fr.heneria.nexus.game.team.GameTeam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class NexusMap {
    private final String id;
    private final String name;
    private final String description;
    private final String sourceFolder;

    // Config data (Loaded from yaml)
    private final Map<GameTeam, ConfigLocation> teamSpawns;
    private final NexusConfig nexusConfig;
    private final List<CaptureConfig> captureConfigs;

    @Getter
    @AllArgsConstructor
    public static class ConfigLocation {
        double x, y, z;
        float yaw, pitch;

        public Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class NexusConfig {
        ConfigLocation location;
        double maxHealth;
    }

    @Getter
    @AllArgsConstructor
    public static class CaptureConfig {
        String id;
        ConfigLocation center;
        double radius;
    }
}
