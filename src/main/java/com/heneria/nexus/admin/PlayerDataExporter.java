package com.heneria.nexus.admin;

import com.heneria.nexus.api.PlayerProfile;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.repository.EconomyRepository;
import com.heneria.nexus.db.repository.ProfileRepository;
import com.heneria.nexus.util.NexusLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for exporting player data snapshots to disk.
 */
public final class PlayerDataExporter {

    private final ProfileRepository profileRepository;
    private final EconomyRepository economyRepository;
    private final ExecutorManager executorManager;
    private final PlayerDataCodec codec;
    private final Path exportDirectory;
    private final NexusLogger logger;

    public PlayerDataExporter(ProfileRepository profileRepository,
                              EconomyRepository economyRepository,
                              ExecutorManager executorManager,
                              PlayerDataCodec codec,
                              Path exportDirectory,
                              NexusLogger logger) {
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
        this.economyRepository = Objects.requireNonNull(economyRepository, "economyRepository");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.exportDirectory = Objects.requireNonNull(exportDirectory, "exportDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Exports the player data snapshot using the provided format.
     *
     * @param playerId      unique identifier of the player
     * @param lastKnownName last known name of the player (informative only)
     * @param format        output format
     * @return future yielding the path to the generated file
     */
    public CompletableFuture<Path> exportPlayerData(UUID playerId, String lastKnownName, PlayerDataFormat format) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(format, "format");
        CompletableFuture<Optional<PlayerProfile>> profileFuture = profileRepository.findByUuid(playerId);
        CompletableFuture<Long> balanceFuture = economyRepository.getBalance(playerId);
        return profileFuture.thenCombine(balanceFuture, (maybeProfile, balance) -> {
                    PlayerProfileSnapshot profileSnapshot = maybeProfile
                            .map(PlayerProfileSnapshot::fromProfile)
                            .orElseGet(PlayerProfileSnapshot::empty);
                    PlayerEconomySnapshot economySnapshot = new PlayerEconomySnapshot(Math.max(balance, 0L));
                    return new PlayerDataSnapshot(playerId, lastKnownName, profileSnapshot, economySnapshot);
                })
                .thenCompose(snapshot -> executorManager.supplyIo(() -> writeSnapshot(snapshot, format)));
    }

    private Path writeSnapshot(PlayerDataSnapshot snapshot, PlayerDataFormat format) {
        Path target = exportDirectory.resolve(snapshot.playerId() + "." + format.primaryExtension());
        try {
            codec.write(target, snapshot, format);
            return target;
        } catch (IOException exception) {
            logger.warn("Impossible d'exporter les données pour " + snapshot.playerId(), exception);
            throw new RuntimeException(exception);
        }
    }
}
