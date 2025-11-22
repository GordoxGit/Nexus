package fr.heneria.nexus.game;

import fr.heneria.nexus.NexusPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import lombok.Getter;

import java.util.Collections;

public class GameManager {
    private final NexusPlugin plugin;
    @Getter
    private GameState state;

    public GameManager(NexusPlugin plugin) {
        this.plugin = plugin;
        this.state = GameState.LOBBY;
    }

    public void setState(GameState state) {
        this.state = state;
        plugin.getLogger().info("Game State changed to: " + state);

        switch (state) {
            case STARTING:
                handleStarting();
                break;
            case PLAYING:
                handlePlaying();
                break;
            case END:
                handleEnd();
                break;
            default:
                break;
        }
    }

    private void handleStarting() {
        plugin.getMapManager().loadMap("default_arena").thenAccept(world -> {
             plugin.getLogger().info("Map loaded for game start.");
             // Ideally we would transition to PLAYING here automatically or wait for a timer.
             // For now, we just wait for manual command or logic.
        }).exceptionally(e -> {
            plugin.getLogger().severe("Failed to load map: " + e.getMessage());
            return null;
        });
    }

    private void handlePlaying() {
        World world = plugin.getMapManager().getCurrentWorld();
        if (world == null) {
            plugin.getLogger().severe("Cannot start game: Map not loaded.");
            return;
        }

        Location spawn = new Location(world, 0.5, 100, 0.5);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawn);
            player.sendMessage(Component.text("La partie commence !", NamedTextColor.GREEN));
        }

        plugin.getHoloService().createHologram(spawn, Collections.singletonList(
                Component.text("Bienvenue sur Nexus", NamedTextColor.AQUA)
        ));
    }

    private void handleEnd() {
        World lobby = Bukkit.getWorlds().get(0);
        Location lobbySpawn = lobby.getSpawnLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(lobbySpawn);
            player.sendMessage(Component.text("La partie est terminée.", NamedTextColor.RED));
        }

        plugin.getHoloService().removeAll();
        plugin.getMapManager().unloadWorld();
    }
}
