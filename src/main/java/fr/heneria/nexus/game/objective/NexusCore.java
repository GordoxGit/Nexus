package fr.heneria.nexus.game.objective;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.holo.HoloService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import lombok.Getter;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.UUID;

public class NexusCore {

    private final NexusPlugin plugin;
    @Getter
    private final Location location;
    @Getter
    private final GameTeam owner;
    @Getter
    private final double maxHealth;
    @Getter
    private double currentHealth;
    private UUID hologramId;
    private BlockDisplay displayEntity;
    private BukkitRunnable animationTask;

    public enum State {
        PROTECTED,
        VULNERABLE
    }

    @Getter
    private State state = State.PROTECTED;
    private int shieldLayers = 2;

    public NexusCore(NexusPlugin plugin, Location location, GameTeam owner, double maxHealth) {
        this.plugin = plugin;
        this.location = location;
        this.owner = owner;
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
    }

    public void spawn() {
        // Spawn BlockDisplay
        Location spawnLoc = location.clone().add(0.5, 0.5, 0.5); // Center block
        displayEntity = (BlockDisplay) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        displayEntity.setBlock(Material.BEACON.createBlockData());
        displayEntity.setTransformation(new Transformation(
                new Vector3f(-0.5f, -0.5f, -0.5f), // Translation to center the block
                new AxisAngle4f(0, 0, 1, 0), // Rotation left
                new Vector3f(1f, 1f, 1f), // Scale
                new AxisAngle4f(0, 0, 1, 0) // Rotation right
        ));

        // Start animation
        startAnimation();

        // Create Hologram
        HoloService holo = plugin.getHoloService();
        hologramId = holo.createHologram(location.clone().add(0.5, 2.5, 0.5), Collections.singletonList(
                getHologramText()
        ));
    }

    private void startAnimation() {
        animationTask = new BukkitRunnable() {
            float angle = 0;

            @Override
            public void run() {
                if (displayEntity == null || !displayEntity.isValid()) {
                    this.cancel();
                    return;
                }

                angle += 0.05f; // Rotation speed
                if (angle > Math.PI * 2) angle -= Math.PI * 2;

                displayEntity.setTransformation(new Transformation(
                        new Vector3f(-0.5f, -0.5f, -0.5f),
                        new AxisAngle4f(angle, 0, 1, 0),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f(0, 0, 1, 0)
                ));
                displayEntity.setInterpolationDelay(0);
                displayEntity.setInterpolationDuration(1);
            }
        };
        animationTask.runTaskTimer(plugin, 0L, 1L);
    }

    public void cleanup() {
        if (displayEntity != null) {
            displayEntity.remove();
        }
        if (animationTask != null) {
            animationTask.cancel();
        }
        if (hologramId != null) {
             plugin.getHoloService().removeHologram(hologramId);
        }
    }

    public void overload() {
        if (state == State.VULNERABLE) return;

        shieldLayers--;
        if (shieldLayers <= 0) {
            state = State.VULNERABLE;
            location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location.clone().add(0.5, 0.5, 0.5), 1);
            plugin.getServer().broadcast(Component.text("Le Nexus " + owner.getName() + " est désormais VULNÉRABLE !", NamedTextColor.RED));
        } else {
            location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
            plugin.getServer().broadcast(Component.text("Bouclier du Nexus " + owner.getName() + " endommagé (" + shieldLayers + " restant)!", NamedTextColor.GOLD));
        }

        if (hologramId != null) {
            plugin.getHoloService().updateLine(hologramId, 0, getHologramText());
        }
    }

    public void damage(double amount, Player attacker) {
        if (state == State.PROTECTED) {
            location.getWorld().playSound(location, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
            attacker.sendMessage(Component.text("Ce Nexus est protégé par un bouclier !", NamedTextColor.RED));
            return;
        }

        if (currentHealth <= 0) return;

        currentHealth -= amount;
        if (currentHealth < 0) currentHealth = 0;

        // Visual effects
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location.clone().add(0.5, 0.5, 0.5), 1);
        location.getWorld().playSound(location, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 1f);

        // Update Hologram
        if (hologramId != null) {
            plugin.getHoloService().updateLine(hologramId, 0, getHologramText());
        }

        if (currentHealth <= 0) {
            onDestroy();
        }
    }

    private void onDestroy() {
        Component teamName = owner != null ? Component.text(owner.getName(), owner.getColor()) : Component.text("Neutre", NamedTextColor.WHITE);

        plugin.getServer().broadcast(
                Component.text("Le Nexus ", NamedTextColor.YELLOW)
                        .append(teamName)
                        .append(Component.text(" a été détruit !", NamedTextColor.YELLOW))
        );

        cleanup();
        // Trigger game end in GameManager (todo)
        // plugin.getGameManager().endGame(winnerTeam);
    }

    private Component getHologramText() {
        TextColor color = owner != null ? owner.getColor() : NamedTextColor.WHITE;
        Component status;
        if (state == State.PROTECTED) {
            status = Component.text("PROTECTED (" + shieldLayers + ")", NamedTextColor.GOLD);
        } else {
            status = Component.text((int)currentHealth + "/" + (int)maxHealth + " ❤", NamedTextColor.WHITE);
        }

        return Component.text("Nexus : ", color).append(status);
    }
}
