package fr.heneria.nexus.game.manager;

import fr.heneria.nexus.game.GameConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suit les dégâts infligés entre joueurs pour attribuer correctement les kills indirects.
 */
public class DamageTrackerManager {

    private static final DamageTrackerManager INSTANCE = new DamageTrackerManager();

    public static DamageTrackerManager getInstance() {
        return INSTANCE;
    }

    /**
     * Informations sur le dernier joueur ayant infligé des dégâts à une victime.
     * @param attackerUuid UUID de l'attaquant
     * @param time moment de l'attaque
     */
    public record DamageInfo(UUID attackerUuid, Instant time) {}

    private final Map<UUID, DamageInfo> lastDamagers = new ConcurrentHashMap<>();

    private DamageTrackerManager() {
    }

    /**
     * Enregistre les dégâts infligés par un joueur à un autre.
     */
    public void recordDamage(Player victim, Player attacker) {
        lastDamagers.put(victim.getUniqueId(), new DamageInfo(attacker.getUniqueId(), Instant.now()));
    }

    /**
     * Récupère le dernier attaquant si l'attaque est récente.
     */
    public Optional<Player> getKiller(Player victim) {
        DamageInfo info = lastDamagers.get(victim.getUniqueId());
        if (info == null) {
            return Optional.empty();
        }
        int delay = GameConfig.get().getKillAttributionTimeSeconds();
        if (info.time().isBefore(Instant.now().minusSeconds(delay))) {
            return Optional.empty();
        }
        Player attacker = Bukkit.getPlayer(info.attackerUuid());
        return Optional.ofNullable(attacker);
    }

    /**
     * Efface les informations liées à un joueur.
     */
    public void clear(Player player) {
        lastDamagers.remove(player.getUniqueId());
    }
}
