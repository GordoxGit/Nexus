package fr.heneria.nexus.map;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.utils.FileUtils;
import lombok.Getter;
import org.bukkit.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MapManager {

    private final NexusPlugin plugin;
    @Getter
    private final MapConfig mapConfig;
    @Getter
    private World currentWorld;
    private NexusMap currentMap;

    public MapManager(NexusPlugin plugin) {
        this.plugin = plugin;
        this.mapConfig = new MapConfig(plugin);
        this.mapConfig.load();

        File templatesDir = new File(plugin.getDataFolder().getParentFile().getParentFile(), "world_templates");
        if (!templatesDir.exists()) {
            templatesDir.mkdirs();
        }
    }

    public CompletableFuture<World> loadMap(String mapId) {
        NexusMap map = mapConfig.getMap(mapId);
        if (map == null) {
            plugin.getLogger().severe("Map " + mapId + " not found in config!");
            return CompletableFuture.failedFuture(new IllegalArgumentException("Map not found"));
        }

        CompletableFuture<World> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File templatesDir = new File(plugin.getDataFolder().getParentFile().getParentFile(), "world_templates");
            File source = new File(templatesDir, map.getSourceFolder());
            File target = new File(plugin.getDataFolder().getParentFile().getParentFile(), "instances/" + mapId + "_active");

            if (!source.exists()) {
                plugin.getLogger().severe("Template folder " + source.getAbsolutePath() + " does not exist!");
                future.completeExceptionally(new IOException("Template folder missing"));
                return;
            }

            try {
                if (target.exists()) {
                    FileUtils.deleteDirectory(target);
                }
                FileUtils.copyDirectory(source, target);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to copy map files: " + e.getMessage());
                future.completeExceptionally(e);
                return;
            }

            // Create empty 'uid.dat' file so Bukkit doesn't try to recover old session.
            File uidFile = new File(target, "uid.dat");
            if (uidFile.exists()) {
                uidFile.delete();
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                WorldCreator creator = new WorldCreator("instances/" + mapId + "_active");
                World world = creator.createWorld();
                if (world != null) {
                    world.setAutoSave(false);
                    world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

                    this.currentWorld = world;
                    this.currentMap = map;

                    // Set active map in GameManager
                    plugin.getGameManager().setActiveMap(map);

                    future.complete(world);
                    plugin.getLogger().info("Map " + map.getName() + " loaded successfully!");
                } else {
                    future.completeExceptionally(new RuntimeException("Failed to create world"));
                }
            });
        });

        return future;
    }

    public void unloadWorld() {
        if (currentWorld != null) {
            String worldName = currentWorld.getName();
            File worldFolder = currentWorld.getWorldFolder();

            // Teleport everyone out
            for (org.bukkit.entity.Player p : currentWorld.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }

            Bukkit.unloadWorld(currentWorld, false);
            plugin.getLogger().info("World " + worldName + " unloaded.");
            currentWorld = null;
            currentMap = null;
            // Should we clear active map in GameManager? Yes, done in handleEnd, but if unloaded manually via map unload?
            // The command map unload calls this. So we should update GameManager too.
            plugin.getGameManager().setActiveMap(null);

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    FileUtils.deleteDirectory(worldFolder);
                    plugin.getLogger().info("Deleted active map folder: " + worldFolder.getName());
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to delete active map folder: " + e.getMessage());
                }
            });
        }
    }
}
