package fr.heneria.nexus.game;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.map.NexusMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameManager {
    private final NexusPlugin plugin;
    @Getter
    private GameState state;

    public GameManager(NexusPlugin plugin) {
        this.plugin = plugin;
        this.state = GameState.LOBBY;
    }

    public void setState(GameState state) {
        // If we are already STARTING, don't re-trigger STARTING logic if called again, but we might transition.
        if (this.state == state) return;

        this.state = state;
        plugin.getLogger().info("Game State changed to: " + state);

        switch (state) {
            case STARTING:
                handleStarting();
                break;
            case PLAYING:
                // handlePlaying is called via transition from STARTING usually
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

             NexusMap map = plugin.getMapManager().getMapConfig().getMap("default_arena");
             if (map != null) {
                 // Ensure we are on main thread for Bukkit API calls
                 plugin.getServer().getScheduler().runTask(plugin, () -> {
                     plugin.getObjectiveManager().loadObjectives(map, world);
                     // Automatically transition to PLAYING
                     setState(GameState.PLAYING);
                 });
             } else {
                 plugin.getLogger().severe("Map config not found for default_arena");
             }

        }).exceptionally(e -> {
            plugin.getLogger().severe("Failed to load map: " + e.getMessage());
            return null;
        });
    }

    private void handlePlaying() {
        World world = plugin.getMapManager().getCurrentWorld();
        if (world == null) {
            plugin.getLogger().severe("Cannot start game: Map not loaded (World is null).");
            return;
        }

        NexusMap map = plugin.getMapManager().getMapConfig().getMap("default_arena");
        Location fallbackSpawn = new Location(world, 0.5, 100, 0.5);

        // Assign Teams
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);

        int teamSize = (int) Math.ceil(players.size() / 2.0);
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            GameTeam team = (i < teamSize) ? GameTeam.BLUE : GameTeam.RED;
            plugin.getTeamManager().addPlayerToTeam(p, team);

            p.sendMessage(Component.text("Vous avez rejoint l'équipe ", NamedTextColor.GRAY)
                    .append(Component.text(team.getName(), team.getColor())));

            // Teleport to Team Spawn
            Location teamSpawn = fallbackSpawn;
            if (map != null && map.getTeamSpawns() != null && map.getTeamSpawns().containsKey(team)) {
                teamSpawn = map.getTeamSpawns().get(team).toLocation(world);
            }
            p.teleport(teamSpawn);

            // Give kit (TODO: using ClassManager later, for now just empty)
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text("La partie commence !", NamedTextColor.GREEN));
        }

        // Start Objective Loops
        plugin.getObjectiveManager().startLoops();

        plugin.getHoloService().createHologram(fallbackSpawn, Collections.singletonList(
                Component.text("Bienvenue sur Nexus", NamedTextColor.AQUA)
        ));
    }

    private void handleEnd() {
        // Stop Objective Loops
        plugin.getObjectiveManager().stopLoops();

        World lobby = Bukkit.getWorlds().get(0);
        Location lobbySpawn = lobby.getSpawnLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(lobbySpawn);
            player.sendMessage(Component.text("La partie est terminée.", NamedTextColor.RED));
            plugin.getTeamManager().removePlayer(player);
        }

        plugin.getHoloService().removeAll();
        plugin.getMapManager().unloadWorld();
    }
}
