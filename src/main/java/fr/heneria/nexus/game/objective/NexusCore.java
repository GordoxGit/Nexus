package fr.heneria.nexus.game.objective;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.team.GameTeam;
import fr.heneria.nexus.holo.HoloService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import lombok.Getter;

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

    public NexusCore(NexusPlugin plugin, Location location, GameTeam owner, double maxHealth) {
        this.plugin = plugin;
        this.location = location;
        this.owner = owner;
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
    }

    public void spawn() {
        // Create Hologram
        HoloService holo = plugin.getHoloService();
        hologramId = holo.createHologram(location.clone().add(0.5, 2.5, 0.5), Collections.singletonList(
                getHologramText()
        ));
    }

    public void damage(double amount, Player attacker) {
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
        // Temporary end game logic
        plugin.getServer().broadcast(
                Component.text("Le Nexus de l'équipe ", NamedTextColor.YELLOW)
                        .append(Component.text(owner.getName(), owner.getColor()))
                        .append(Component.text(" a été détruit !", NamedTextColor.YELLOW))
        );
        // Trigger game end in GameManager (todo)
        // plugin.getGameManager().endGame(winnerTeam);
    }

    private Component getHologramText() {
        return Component.text("Nexus : ", owner.getColor())
                .append(Component.text((int)currentHealth + "/" + (int)maxHealth + " ❤", NamedTextColor.WHITE));
    }
}
