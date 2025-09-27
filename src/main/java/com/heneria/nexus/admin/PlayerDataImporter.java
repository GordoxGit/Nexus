package com.heneria.nexus.admin;

import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.db.repository.EconomyRepository;
import com.heneria.nexus.db.repository.ProfileRepository;
import com.heneria.nexus.util.NexusLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles validation and application of player data imports.
 */
public final class PlayerDataImporter {

    private static final Duration CONFIRMATION_WINDOW = Duration.ofMinutes(2);
    private static final String UPSERT_PROFILE_SQL =
            "INSERT INTO nexus_profiles (player_uuid, elo_rating, total_kills, total_deaths, total_wins, total_losses, matches_played) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "elo_rating = VALUES(elo_rating), " +
                    "total_kills = VALUES(total_kills), " +
                    "total_deaths = VALUES(total_deaths), " +
                    "total_wins = VALUES(total_wins), " +
                    "total_losses = VALUES(total_losses), " +
                    "matches_played = VALUES(matches_played)";
    private static final String UPSERT_BALANCE_SQL =
            "INSERT INTO nexus_economy (player_uuid, balance) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE balance = VALUES(balance)";

    private final ProfileRepository profileRepository;
    private final EconomyRepository economyRepository;
    private final DbProvider dbProvider;
    private final ExecutorManager executorManager;
    private final PlayerDataCodec codec;
    private final Path importDirectory;
    private final NexusLogger logger;
    private final Map<String, PendingImport> pendingImports = new ConcurrentHashMap<>();

    public PlayerDataImporter(ProfileRepository profileRepository,
                              EconomyRepository economyRepository,
                              DbProvider dbProvider,
                              ExecutorManager executorManager,
                              PlayerDataCodec codec,
                              Path importDirectory,
                              NexusLogger logger) {
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository");
        this.economyRepository = Objects.requireNonNull(economyRepository, "economyRepository");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.importDirectory = Objects.requireNonNull(importDirectory, "importDirectory");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Prepares an import by validating the provided file and caching the payload for confirmation.
     *
     * @param actorKey unique identifier representing the administrator initiating the import
     * @param playerId player targeted by the import
     * @param fileName file located within the import directory
     * @return future yielding a preview of the import payload
     */
    public CompletableFuture<ImportPreparation> prepareImport(String actorKey, UUID playerId, String fileName) {
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(fileName, "fileName");
        purgeExpired();
        return executorManager.supplyIo(() -> {
                    try {
                        return loadSnapshot(fileName);
                    } catch (IOException exception) {
                        logger.warn("Échec du chargement du fichier d'import '" + fileName + "'", exception);
                        throw new UncheckedIOException(exception);
                    }
                })
                .thenCompose(loaded -> profileRepository.findByUuid(playerId)
                        .thenCombine(economyRepository.getBalance(playerId), (maybeProfile, balance) -> {
                            validateSnapshot(loaded.snapshot(), playerId);
                            PlayerProfileSnapshot currentProfile = maybeProfile
                                    .map(PlayerProfileSnapshot::fromProfile)
                                    .orElseGet(PlayerProfileSnapshot::empty);
                            PlayerEconomySnapshot currentEconomy = new PlayerEconomySnapshot(Math.max(balance, 0L));
                            PendingImport pending = new PendingImport(playerId, loaded.fileName(), loaded.snapshot(), Instant.now().plus(CONFIRMATION_WINDOW));
                            pendingImports.put(actorKey, pending);
                            return new ImportPreparation(loaded.fileName(), loaded.snapshot(), currentProfile, currentEconomy);
                        }));
    }

    /**
     * Executes the import cached for the provided administrator.
     *
     * @param actorKey unique identifier representing the administrator
     * @param playerId target player identifier
     * @param fileName file name confirmation
     * @return future completed when the import has been applied
     */
    public CompletableFuture<Void> confirmImport(String actorKey, UUID playerId, String fileName) {
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(fileName, "fileName");
        purgeExpired();
        PendingImport pending = pendingImports.get(actorKey);
        if (pending == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("no-pending"));
        }
        if (!pending.playerId().equals(playerId) || !pending.fileName().equals(fileName)) {
            return CompletableFuture.failedFuture(new IllegalStateException("mismatch"));
        }
        if (pending.expiresAt().isBefore(Instant.now())) {
            pendingImports.remove(actorKey);
            return CompletableFuture.failedFuture(new IllegalStateException("expired"));
        }
        validateSnapshot(pending.snapshot(), playerId);
        return persistSnapshot(pending.snapshot())
                .whenComplete((unused, throwable) -> {
                    if (throwable == null) {
                        pendingImports.remove(actorKey);
                    }
                });
    }

    /**
     * Lists the files available for import.
     *
     * @param prefix optional prefix filter
     * @return list of matching file names
     */
    public List<String> suggestAvailableFiles(String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (!Files.isDirectory(importDirectory)) {
            return List.of();
        }
        try {
            List<String> matches = new ArrayList<>();
            try (var stream = Files.list(importDirectory)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
                        .sorted()
                        .limit(30)
                        .forEach(matches::add);
            }
            return Collections.unmodifiableList(matches);
        } catch (IOException exception) {
            logger.warn("Impossible de lister les fichiers d'import", exception);
            return List.of();
        }
    }

    private LoadedSnapshot loadSnapshot(String fileName) throws IOException {
        Path source = resolveImportFile(fileName);
        PlayerDataFormat format = PlayerDataFormat.fromFileName(source.getFileName().toString());
        PlayerDataSnapshot snapshot = codec.read(source, format);
        return new LoadedSnapshot(source.getFileName().toString(), snapshot);
    }

    private Path resolveImportFile(String fileName) throws IOException {
        Path resolved = importDirectory.resolve(fileName).normalize();
        if (!resolved.startsWith(importDirectory)) {
            throw new IOException("Chemin en dehors du dossier d'import");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IOException("Fichier introuvable : " + fileName);
        }
        return resolved;
    }

    private void validateSnapshot(PlayerDataSnapshot snapshot, UUID expectedPlayerId) {
        if (!snapshot.playerId().equals(expectedPlayerId)) {
            throw new PlayerDataValidationException("L'UUID du fichier ne correspond pas à la cible");
        }
        PlayerProfileSnapshot profile = snapshot.profile();
        validateNonNegative(profile.eloRating(), "elo");
        validateNonNegative(profile.totalKills(), "kills");
        validateNonNegative(profile.totalDeaths(), "deaths");
        validateNonNegative(profile.totalWins(), "wins");
        validateNonNegative(profile.totalLosses(), "losses");
        validateNonNegative(profile.matchesPlayed(), "matches");
        validateIntRange(profile.eloRating(), "elo");
        validateIntRange(profile.totalKills(), "kills");
        validateIntRange(profile.totalDeaths(), "deaths");
        validateIntRange(profile.totalWins(), "wins");
        validateIntRange(profile.totalLosses(), "losses");
        validateIntRange(profile.matchesPlayed(), "matches");
        PlayerEconomySnapshot economy = snapshot.economy();
        validateNonNegative(economy.balance(), "balance");
    }

    private void validateNonNegative(long value, String field) {
        if (value < 0L) {
            throw new PlayerDataValidationException("Valeur négative non autorisée pour " + field);
        }
    }

    private void validateIntRange(long value, String field) {
        if (value > Integer.MAX_VALUE) {
            throw new PlayerDataValidationException("Valeur trop élevée pour " + field);
        }
    }

    private CompletableFuture<Void> persistSnapshot(PlayerDataSnapshot snapshot) {
        return dbProvider.execute("PlayerDataImporter::persistSnapshot",
                connection -> persistTransactional(connection, snapshot), executorManager.io());
    }

    private Void persistTransactional(Connection connection, PlayerDataSnapshot snapshot) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement profileStatement = connection.prepareStatement(UPSERT_PROFILE_SQL)) {
                profileStatement.setString(1, snapshot.playerId().toString());
                profileStatement.setInt(2, clamp(snapshot.profile().eloRating()));
                profileStatement.setInt(3, clamp(snapshot.profile().totalKills()));
                profileStatement.setInt(4, clamp(snapshot.profile().totalDeaths()));
                profileStatement.setInt(5, clamp(snapshot.profile().totalWins()));
                profileStatement.setInt(6, clamp(snapshot.profile().totalLosses()));
                profileStatement.setInt(7, clamp(snapshot.profile().matchesPlayed()));
                profileStatement.executeUpdate();
            }
            try (PreparedStatement economyStatement = connection.prepareStatement(UPSERT_BALANCE_SQL)) {
                economyStatement.setString(1, snapshot.playerId().toString());
                economyStatement.setLong(2, snapshot.economy().balance());
                economyStatement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            logger.warn("Échec de l'import des données pour " + snapshot.playerId(), exception);
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return null;
    }

    private int clamp(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pendingImports.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record LoadedSnapshot(String fileName, PlayerDataSnapshot snapshot) {
    }

    /**
     * Preview information returned to the command handler before confirmation.
     */
    public record ImportPreparation(String fileName,
                                    PlayerDataSnapshot snapshot,
                                    PlayerProfileSnapshot currentProfile,
                                    PlayerEconomySnapshot currentEconomy) {
    }

    private record PendingImport(UUID playerId, String fileName, PlayerDataSnapshot snapshot, Instant expiresAt) {
    }
}
