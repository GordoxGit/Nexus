package fr.heneria.nexus.game.objective;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.game.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

public class CapturePoint implements Runnable {

    private final NexusPlugin plugin;
    @Getter
    private final String id;
    private final Location center;
    private final double radius;
    private final BoundingBox boundingBox;

    @Getter
    private GameTeam owningTeam = null; // Null if neutral
    private double progress = 0; // -100 (Red) to 100 (Blue) ? Or 0-100 per team?
    // Let's use 0-100 and keep track of who is capturing.
    // Spec says: "Majority logic".

    // To simplify: let's say progress is 0-100.
    // If owningTeam is null, first team to step in starts capturing.
    // If enemies step in, it contests or reduces progress.

    // Simpler approach based on ticket:
    // "La barre monte pour les Bleus" if more Blues than Reds.

    private GameTeam capturingTeam = null;
    private double captureProgress = 0.0; // 0 to 100
    private UUID hologramId;

    public CapturePoint(NexusPlugin plugin, String id, Location center, double radius) {
        this.plugin = plugin;
        this.id = id;
        this.center = center;
        this.radius = radius;
        this.boundingBox = BoundingBox.of(center, radius, radius, radius);
    }

    public void spawn() {
        hologramId = plugin.getHoloService().createHologram(center.clone().add(0, 3, 0), getHologramLines());
    }

    @Override
    public void run() {
        // Scan for players
        List<Player> players = center.getWorld().getNearbyEntities(boundingBox).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .collect(Collectors.toList());

        TeamManager tm = plugin.getTeamManager();
        int blueCount = 0;
        int redCount = 0;

        for (Player p : players) {
            GameTeam team = tm.getPlayerTeam(p);
            if (team == GameTeam.BLUE) blueCount++;
            else if (team == GameTeam.RED) redCount++;
        }

        if (blueCount > redCount) {
            tickCapture(GameTeam.BLUE);
        } else if (redCount > blueCount) {
            tickCapture(GameTeam.RED);
        } else {
             // Tie or no one present. Decay? Or just stay?
             // Spec doesn't specify decay, but usually it does.
             // For now, let's just do nothing on tie.
        }

        updateVisuals();
    }

    private void tickCapture(GameTeam dominantTeam) {
        double speed = 2.0; // 2% per second

        if (owningTeam == dominantTeam) {
            // Already owned, maybe heal to 100% if damaged?
            if (captureProgress < 100) {
                captureProgress = Math.min(100, captureProgress + speed);
            }
            return;
        }

        if (capturingTeam == null) {
            capturingTeam = dominantTeam;
            captureProgress += speed;
        } else if (capturingTeam == dominantTeam) {
            captureProgress += speed;
        } else {
            // Opposing team is capturing
            captureProgress -= speed;
            if (captureProgress <= 0) {
                capturingTeam = dominantTeam; // Switch control
                captureProgress = Math.abs(captureProgress); // Flip
            }
        }

        if (captureProgress >= 100) {
            captureProgress = 100;
            if (owningTeam != capturingTeam) {
                owningTeam = capturingTeam;
                plugin.getServer().broadcast(Component.text("La zone " + id + " a été capturée par " + owningTeam.getName() + " !", owningTeam.getColor()));
            }
        }
    }

    private void updateVisuals() {
        // Particles
        GameTeam particleTeam = owningTeam != null ? owningTeam : (capturingTeam != null ? capturingTeam : null);
        if (particleTeam != null) {
             // Spawn particles at corners (simplified to just center for now or 4 corners)
             // Use DUST particle with color
             Particle.DustOptions dust = new Particle.DustOptions(
                     particleTeam == GameTeam.BLUE ? org.bukkit.Color.BLUE : org.bukkit.Color.RED,
                     1.5f
             );
             center.getWorld().spawnParticle(Particle.DUST, center.clone().add(radius, 0, radius), 5, dust);
             center.getWorld().spawnParticle(Particle.DUST, center.clone().add(-radius, 0, radius), 5, dust);
             center.getWorld().spawnParticle(Particle.DUST, center.clone().add(radius, 0, -radius), 5, dust);
             center.getWorld().spawnParticle(Particle.DUST, center.clone().add(-radius, 0, -radius), 5, dust);
        }

        // Hologram
        if (hologramId != null) {
            List<Component> lines = getHologramLines();
            for (int i = 0; i < lines.size(); i++) {
                plugin.getHoloService().updateLine(hologramId, i, lines.get(i));
            }
        }
    }

    private List<Component> getHologramLines() {
        Component header = Component.text("Cellule", NamedTextColor.GRAY);

        // Spec: <blue>Bleu: 50%</blue> | <red>Rouge: 10%</red>
        // My logic tracks a single progress bar for the capturing team.
        // I will adapt the display to match my logic but try to respect the request.

        double bluePerc = 0;
        double redPerc = 0;

        if (owningTeam == GameTeam.BLUE) {
            bluePerc = 100; // Owned
             // If being contested by Red, maybe show that?
             // But my logic is simpler: Single progress bar.
        } else if (owningTeam == GameTeam.RED) {
            redPerc = 100;
        }

        if (capturingTeam == GameTeam.BLUE) {
            bluePerc = captureProgress;
        } else if (capturingTeam == GameTeam.RED) {
            redPerc = captureProgress;
        }

        // If owned by Blue (100%), and Red is capturing (progress goes down from 100 to 0 then up for red)
        // With my logic `captureProgress` is 0-100 towards `capturingTeam`.
        // If `owningTeam` is set, we need to decapture first.

        // Let's stick to the prompt's suggested display format exactly:
        // "<blue>Bleu: 50%</blue> | <red>Rouge: 10%</red>"

        // If Blue is capturing (50%), Red is 0%.
        if (capturingTeam == GameTeam.BLUE) {
             bluePerc = captureProgress;
             redPerc = 0;
        } else if (capturingTeam == GameTeam.RED) {
             redPerc = captureProgress;
             bluePerc = 0;
        }

        // If Owned
        if (owningTeam == GameTeam.BLUE && capturingTeam == GameTeam.BLUE) bluePerc = 100;
        if (owningTeam == GameTeam.RED && capturingTeam == GameTeam.RED) redPerc = 100;

        Component status = Component.text("Bleu: " + (int)bluePerc + "%", NamedTextColor.BLUE)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("Rouge: " + (int)redPerc + "%", NamedTextColor.RED));

        return Arrays.asList(header, status);
    }
}
