package com.heneria.nexus.service.core.nexus;

import com.heneria.nexus.api.ArenaHandle;
import com.heneria.nexus.hologram.Hologram;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scoreboard.Team;

/**
 * Represents a single nexus instance bound to an arena team.
 */
public final class NexusCore {

    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    private final ArenaHandle arena;
    private final Team team;
    private final String teamId;
    private final String teamDisplayName;
    private final Location blockLocation;
    private final Hologram hologram;
    private final int maxHp;

    private volatile int currentHp;
    private volatile NexusState state;
    private volatile boolean destroyed;

    public NexusCore(ArenaHandle arena,
                     Team team,
                     String teamId,
                     String teamDisplayName,
                     Location blockLocation,
                     int hitPoints,
                     Hologram hologram) {
        this.arena = Objects.requireNonNull(arena, "arena");
        this.team = Objects.requireNonNull(team, "team");
        this.teamId = Objects.requireNonNull(teamId, "teamId").toLowerCase(Locale.ROOT);
        this.teamDisplayName = Objects.requireNonNull(teamDisplayName, "teamDisplayName");
        this.blockLocation = Objects.requireNonNull(blockLocation, "blockLocation").clone();
        this.hologram = Objects.requireNonNull(hologram, "hologram");
        this.maxHp = Math.max(1, hitPoints);
        this.currentHp = this.maxHp;
        this.state = NexusState.PROTECTED;
        updateHologram();
    }

    public ArenaHandle arena() {
        return arena;
    }

    public Team team() {
        return team;
    }

    public String teamId() {
        return teamId;
    }

    public String teamDisplayName() {
        return teamDisplayName;
    }

    public Location blockLocation() {
        return blockLocation.clone();
    }

    public Location hologramLocation() {
        return blockLocation.clone().add(0.5D, 2.25D, 0.5D);
    }

    public int maxHp() {
        return maxHp;
    }

    public int currentHp() {
        return currentHp;
    }

    public NexusState state() {
        return state;
    }

    public boolean destroyed() {
        return destroyed;
    }

    public void setState(NexusState state) {
        if (destroyed) {
            return;
        }
        this.state = Objects.requireNonNull(state, "state");
        updateHologram();
    }

    public boolean applyDamage(int amount) {
        if (destroyed || amount <= 0) {
            return false;
        }
        currentHp = Math.max(0, currentHp - amount);
        updateHologram();
        if (currentHp == 0) {
            destroyed = true;
            return true;
        }
        return false;
    }

    public void restoreFullHealth() {
        this.currentHp = this.maxHp;
        this.destroyed = false;
        this.state = NexusState.PROTECTED;
        updateHologram();
    }

    public void showCriticalEffect() {
        if (destroyed) {
            return;
        }
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5D, 1.2D, 0.5D);
        world.spawnParticle(Particle.END_ROD, center, 40, 0.25D, 0.5D, 0.25D, 0.01D);
    }

    public void showExposureEffect() {
        if (destroyed) {
            return;
        }
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5D, 1.0D, 0.5D);
        world.spawnParticle(Particle.CRIT_MAGIC, center, 20, 0.2D, 0.3D, 0.2D, 0.01D);
    }

    public void playDestructionEffects() {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5D, 0.5D, 0.5D);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);
    }

    public void dispose() {
        destroyed = true;
        hologram.destroy();
    }

    public void updateHologram() {
        if (destroyed) {
            hologram.updateLines(List.of("<dark_red>Nexus détruit"));
            return;
        }
        String stateLine = switch (state) {
            case PROTECTED -> "<gray>État : Protégé";
            case EXPOSED -> "<gold>État : Exposé";
            case CRITICAL -> "<red>État : Critique";
        };
        String hpLine = "<yellow>PV : " + currentHp + " / " + maxHp;
        hologram.updateLines(List.of(
                "<aqua>Nexus " + sanitize(teamDisplayName),
                hpLine,
                stateLine
        ));
    }

    private String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return team.getName();
        }
        Component display = team.displayName();
        if (display != null) {
            String plain = PLAIN_SERIALIZER.serialize(display).trim();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return input;
    }
}
