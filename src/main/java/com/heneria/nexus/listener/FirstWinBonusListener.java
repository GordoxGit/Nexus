package com.heneria.nexus.listener;

import com.heneria.nexus.api.FirstWinBonusService;
import com.heneria.nexus.api.events.NexusArenaEndEvent;
import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Team;

/**
 * Listens to arena completion events to grant the daily first win bonus.
 */
public final class FirstWinBonusListener implements Listener {

    private final NexusLogger logger;
    private final FirstWinBonusService firstWinBonusService;

    public FirstWinBonusListener(NexusLogger logger, FirstWinBonusService firstWinBonusService) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.firstWinBonusService = Objects.requireNonNull(firstWinBonusService, "firstWinBonusService");
    }

    @EventHandler
    public void onArenaEnd(NexusArenaEndEvent event) {
        Team winner = event.getWinner();
        if (winner == null) {
            return;
        }
        Set<String> entries = winner.getEntries();
        if (entries.isEmpty()) {
            return;
        }
        entries.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .map(this::resolvePlayerId)
                .filter(Objects::nonNull)
                .forEach(this::grantBonusSafely);
    }

    private UUID resolvePlayerId(String scoreboardEntry) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(scoreboardEntry);
        UUID uniqueId = offlinePlayer.getUniqueId();
        if (uniqueId == null) {
            logger.debug(() -> "Entrée de scoreboard sans identifiant: " + scoreboardEntry);
        }
        return uniqueId;
    }

    private void grantBonusSafely(UUID playerId) {
        firstWinBonusService.grantFirstWinBonus(playerId).whenComplete((granted, throwable) -> {
            if (throwable != null) {
                logger.warn("Échec du bonus première victoire pour " + playerId, throwable);
                return;
            }
            if (Boolean.TRUE.equals(granted)) {
                logger.debug(() -> "Bonus première victoire appliqué pour " + playerId);
            }
        });
    }
}
