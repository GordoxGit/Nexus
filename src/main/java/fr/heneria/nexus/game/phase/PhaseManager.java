package fr.heneria.nexus.game.phase;

import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Gère les transitions entre les phases du jeu pour une partie.
 */
public class PhaseManager {

    private final JavaPlugin plugin;
    private final Map<GamePhase, IPhase> phases = new EnumMap<>(GamePhase.class);

    public PhaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // Enregistre les phases connues
        phases.put(GamePhase.CAPTURE, new CapturePhase(plugin));
    }

    public IPhase getPhase(GamePhase phase) {
        return phases.get(phase);
    }

    public void transitionTo(Match match, GamePhase nextPhase) {
        GamePhase current = match.getCurrentPhase();
        IPhase currentImpl = phases.get(current);
        if (currentImpl != null) {
            currentImpl.onEnd(match);
        }
        match.setCurrentPhase(nextPhase);
        IPhase nextImpl = phases.get(nextPhase);
        if (nextImpl != null) {
            nextImpl.onStart(match);
        }
    }

    public void transitionTo(Match match, GamePhase nextPhase, Team team) {
        GamePhase current = match.getCurrentPhase();
        IPhase currentImpl = phases.get(current);
        if (currentImpl != null) {
            currentImpl.onEnd(match);
        }
        match.setCurrentPhase(nextPhase);
        IPhase nextImpl;
        switch (nextPhase) {
            case TRANSPORT -> {
                nextImpl = new TransportPhase(plugin, team);
                phases.put(GamePhase.TRANSPORT, nextImpl);
            }
            case DESTRUCTION -> {
                nextImpl = new DestructionPhase(plugin, team);
                phases.put(GamePhase.DESTRUCTION, nextImpl);
            }
            case ELIMINATION -> {
                nextImpl = new EliminationPhase(plugin, team);
                phases.put(GamePhase.ELIMINATION, nextImpl);
            }
            default -> nextImpl = phases.get(nextPhase);
        }
        if (nextImpl != null) {
            nextImpl.onStart(match);
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
