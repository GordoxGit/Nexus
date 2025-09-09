package fr.heneria.nexus.sanction;

import fr.heneria.nexus.economy.manager.EconomyManager;
import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.player.repository.PlayerRepository;
import fr.heneria.nexus.sanction.model.Sanction;
import fr.heneria.nexus.sanction.repository.SanctionRepository;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class SanctionManager {

    private static SanctionManager instance;

    private final SanctionRepository sanctionRepository;
    private final PlayerRepository playerRepository;
    private final EconomyManager economyManager;

    private SanctionManager(SanctionRepository sanctionRepository, PlayerRepository playerRepository, EconomyManager economyManager) {
        this.sanctionRepository = sanctionRepository;
        this.playerRepository = playerRepository;
        this.economyManager = economyManager;
    }

    public static void init(SanctionRepository sanctionRepository, PlayerRepository playerRepository, EconomyManager economyManager) {
        instance = new SanctionManager(sanctionRepository, playerRepository, economyManager);
    }

    public static SanctionManager getInstance() {
        return instance;
    }

    public void penalizeLeaver(Player player, Match match) {
        UUID playerId = player.getUniqueId();
        playerRepository.findProfileByUUID(playerId).thenAccept(optionalProfile -> {
            optionalProfile.ifPresent(profile -> {
                Duration duration = getDurationForLevel(profile.getLeaverLevel());
                Instant expiration = duration != null ? Instant.now().plus(duration) : null;
                Sanction sanction = new Sanction(0, playerId, "LEAVE_PENALTY", expiration, Instant.now(), true, "Left ranked match");
                sanctionRepository.save(sanction);
                profile.setLeaverLevel(profile.getLeaverLevel() + 1);
                profile.setEloRating(profile.getEloRating() - 25);
                playerRepository.saveProfile(profile);
            });
        });
    }

    private Duration getDurationForLevel(int level) {
        return switch (level) {
            case 0 -> Duration.ofMinutes(5);
            case 1 -> Duration.ofMinutes(10);
            case 2 -> Duration.ofHours(1);
            case 3 -> Duration.ofHours(24);
            default -> null; // Permanent
        };
    }

    public Optional<Sanction> getActiveRankedPenalty(UUID playerId) {
        return sanctionRepository.findActiveSanction(playerId, "LEAVE_PENALTY");
    }

    public void pardonLastPenalty(UUID playerId) {
        sanctionRepository.deactivateLastSanction(playerId);
    }
}
