package fr.heneria.nexus.game;

import fr.heneria.nexus.NexusPlugin;
import lombok.Getter;

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
    }
}
