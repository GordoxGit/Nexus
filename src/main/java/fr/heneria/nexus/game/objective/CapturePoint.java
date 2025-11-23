package fr.heneria.nexus.game.objective;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.map.NexusMap;
import fr.heneria.nexus.utils.ItemBuilder;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private final int respawnTime;
    private final BoundingBox boundingBox;

    @Getter
    private boolean active = true;
    private long lastCaptureTime = 0;

    @Getter
    private GameTeam owningTeam = null;
    private GameTeam capturingTeam = null;
    private double captureProgress = 0.0; // 0 to 100
    private boolean spawning = false;
    private UUID hologramId;
    private BossBar bossBar;

    public CapturePoint(NexusPlugin plugin, String id, Location center, double radius, int respawnTime) {
        this.plugin = plugin;
        this.id = id;
        this.center = center;
        this.radius = radius;
        this.respawnTime = respawnTime;
        this.boundingBox = BoundingBox.of(center, radius, radius, radius);
    }

    public void spawn() {
        if (active) {
            hologramId = plugin.getHoloService().createHologram(center.clone().add(0, 3, 0), getHologramLines());
        }
    }

    public void despawn() {
        if (hologramId != null) {
            plugin.getHoloService().removeHologram(hologramId);
            hologramId = null;
        }
        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bossBar);
            }
        }
    }

    @Override
    public void run() {
        if (!active) return;

        updateBossBar();

        // Scan for players
        List<Player> players = center.getWorld().getNearbyEntities(boundingBox).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .collect(Collectors.toList());

        // Simple majority logic
        int blueCount = 0;
        int redCount = 0;

        for (Player p : players) {
            GameTeam team = plugin.getTeamManager().getPlayerTeam(p);
            if (team == GameTeam.BLUE) blueCount++;
            else if (team == GameTeam.RED) redCount++;
        }

        if (blueCount > redCount) {
            tickCapture(GameTeam.BLUE);
        } else if (redCount > blueCount) {
            tickCapture(GameTeam.RED);
        } else {
            // Decay if empty?
            if (blueCount == 0 && redCount == 0 && capturingTeam != null && captureProgress > 0) {
                 captureProgress = Math.max(0, captureProgress - 1.0);
                 if (captureProgress == 0) capturingTeam = null;
            }
        }

        updateVisuals();
    }

    private void tickCapture(GameTeam dominantTeam) {
        double speed = 5.0; // Faster capture for testing (5% per tick -> 20 ticks = 1s -> 100% in 20s if run every 20 ticks... wait run() is called every 20L? yes)
        // If run() is every second, 5% is 20 seconds.
        // Let's make it 10% per second.

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
                captureProgress = Math.abs(captureProgress);
            }
        }

        if (captureProgress >= 100 && !spawning) {
            captureProgress = 100;
            spawning = true;
            // Capture complete
            completeCapture(dominantTeam);
        }
    }

    private void completeCapture(GameTeam team) {
        owningTeam = team;
        active = false;
        lastCaptureTime = System.currentTimeMillis();

        plugin.getServer().broadcast(Component.text("La Cellule a été récupérée par " + team.getName() + " !", team.getColor()));

        // Give cell to a player in the zone (priority to one who is there)
        List<Player> players = center.getWorld().getNearbyEntities(boundingBox).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .filter(p -> plugin.getTeamManager().getPlayerTeam(p) == team)
                .collect(Collectors.toList());

        if (!players.isEmpty()) {
            Player carrier = players.get(0); // Pick first one
            giveCell(carrier);
        } else {
             // Drop it if no one? unexpected but safe fallback
             ItemStack cell = ObjectiveManager.createCellItem();
             center.getWorld().dropItemNaturally(center.clone().add(0, 1, 0), cell);
        }

        despawn();
    }

    private void giveCell(Player player) {
        ItemStack cell = ObjectiveManager.createCellItem();
        player.getInventory().addItem(cell);
        player.sendMessage(Component.text("Vous portez la Cellule ! Apportez-la au Nexus ennemi !", NamedTextColor.GOLD));
        // Force hold logic handled by listener? Or force held slot here.
        // Listener will prevent switching away.
        // We should ensure they hold it.
        // But if we just addItem, it goes to first empty slot.
        // We might want to clear a slot or swap.
        // For now, let's just add it. The listener will enforce "if holding cell".
        // Ah, requirement: "Impossible de changer d'arme quand on a la Cellule".
        // Implies they must hold it.
        // Best practice: Set it in hand.

        int slot = player.getInventory().firstEmpty();
        if (slot == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), cell);
            player.sendMessage(Component.text("Inventaire plein, la cellule est au sol !", NamedTextColor.RED));
        } else {
            // Put in hand?
            // If we put it in hand, we swap whatever is in hand.
            ItemStack held = player.getInventory().getItemInMainHand();
            player.getInventory().setItemInMainHand(cell);
            if (held != null && held.getType() != Material.AIR) {
                player.getInventory().addItem(held);
            }
        }
    }

    public void reset() {
        active = true;
        owningTeam = null;
        capturingTeam = null;
        captureProgress = 0;
        spawning = false;
        spawn();
    }

    private void updateBossBar() {
        if (bossBar == null) {
            bossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        }

        double bluePerc = (capturingTeam == GameTeam.BLUE) ? captureProgress : 0;
        double redPerc = (capturingTeam == GameTeam.RED) ? captureProgress : 0;

        Component title = Component.text("Capture : ", NamedTextColor.GRAY)
                .append(Component.text("Bleu " + (int)bluePerc + "%", NamedTextColor.BLUE))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text("Rouge " + (int)redPerc + "%", NamedTextColor.RED));

        bossBar.name(title);
        bossBar.progress((float) (captureProgress / 100.0));

        if (capturingTeam == GameTeam.BLUE) {
            bossBar.color(BossBar.Color.BLUE);
        } else if (capturingTeam == GameTeam.RED) {
            bossBar.color(BossBar.Color.RED);
        } else {
            bossBar.color(BossBar.Color.WHITE);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(center.getWorld())) {
                double dist = p.getLocation().distance(center);
                if (dist > 20) {
                    p.showBossBar(bossBar);
                } else {
                    p.hideBossBar(bossBar);
                }
            } else {
                p.hideBossBar(bossBar);
            }
        }
    }

    private void updateVisuals() {
        // Particles
        GameTeam particleTeam = owningTeam != null ? owningTeam : (capturingTeam != null ? capturingTeam : null);
        if (particleTeam != null) {
             Particle.DustOptions dust = new Particle.DustOptions(
                     particleTeam == GameTeam.BLUE ? org.bukkit.Color.BLUE : org.bukkit.Color.RED,
                     1.5f
             );
             // Draw circle or corners
             for (double angle = 0; angle < 360; angle += 45) {
                 double x = center.getX() + Math.cos(Math.toRadians(angle)) * radius;
                 double z = center.getZ() + Math.sin(Math.toRadians(angle)) * radius;
                 center.getWorld().spawnParticle(Particle.DUST, x, center.getY(), z, 1, dust);
             }
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

        double bluePerc = 0;
        double redPerc = 0;

        if (capturingTeam == GameTeam.BLUE) {
             bluePerc = captureProgress;
        } else if (capturingTeam == GameTeam.RED) {
             redPerc = captureProgress;
        }

        Component status = Component.text("Bleu: " + (int)bluePerc + "%", NamedTextColor.BLUE)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("Rouge: " + (int)redPerc + "%", NamedTextColor.RED));

        return Arrays.asList(header, status);
    }
}
