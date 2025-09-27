package com.heneria.nexus.config;

import com.heneria.nexus.config.BackupService.BackupMetadata;
import com.heneria.nexus.config.BackupService.RestoreResult;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Default implementation of {@link BackupService} using the local filesystem.
 */
public final class BackupServiceImpl implements BackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private static final ZoneId TIMESTAMP_ZONE = ZoneOffset.UTC;
    private static final int TIMESTAMP_LENGTH = 18;
    private static final Pattern BACKUP_SUFFIX = Pattern.compile(".+\\.bak");

    private final Path dataDirectory;
    private final Path backupsDirectory;
    private final NexusLogger logger;
    private final ExecutorService executor;
    private final AtomicInteger retentionLimit = new AtomicInteger(10);
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    public BackupServiceImpl(Path dataDirectory, NexusLogger logger) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.backupsDirectory = this.dataDirectory.resolve("backups");
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                new NamedThreadFactory("Nexus-Backup", true, logger));
    }

    @Override
    public CompletableFuture<Optional<BackupMetadata>> createBackup(Path originalFile) {
        Objects.requireNonNull(originalFile, "originalFile");
        return CompletableFuture.supplyAsync(() -> {
            if (retentionLimit.get() == 0) {
                return Optional.empty();
            }
            Path normalised = originalFile.normalize();
            if (!normalised.startsWith(dataDirectory)) {
                throw new CompletionException(new IOException("Le fichier à sauvegarder doit se trouver dans le dossier de données"));
            }
            if (Files.notExists(normalised) || !Files.isRegularFile(normalised)) {
                return Optional.empty();
            }
            ensureDirectory();
            String relative = relativePath(normalised);
            Object lock = fileLocks.computeIfAbsent(relative, key -> new Object());
            synchronized (lock) {
                try {
                    return Optional.ofNullable(createBackupLocked(relative, normalised));
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<BackupMetadata>> listBackups(@Nullable String baseFile) {
        return CompletableFuture.supplyAsync(() -> {
            ensureDirectory();
            if (Files.notExists(backupsDirectory)) {
                return List.of();
            }
            try (Stream<Path> stream = Files.list(backupsDirectory)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> BACKUP_SUFFIX.matcher(path.getFileName().toString()).matches())
                        .map(this::metadataFromFile)
                        .flatMap(Optional::stream)
                        .filter(metadata -> baseFile == null
                                || metadata.baseFileName().equals(normaliseRelative(baseFile)))
                        .sorted(Comparator.comparing(BackupMetadata::createdAt).reversed())
                        .collect(Collectors.toList());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<BackupMetadata>> getBackup(String backupFileName) {
        Objects.requireNonNull(backupFileName, "backupFileName");
        return CompletableFuture.supplyAsync(() -> {
            ensureDirectory();
            Path candidate = resolveBackupPath(backupFileName);
            if (Files.notExists(candidate) || !Files.isRegularFile(candidate)) {
                return Optional.empty();
            }
            return metadataFromFile(candidate);
        }, executor);
    }

    @Override
    public CompletableFuture<RestoreResult> restoreBackup(String backupFileName) {
        Objects.requireNonNull(backupFileName, "backupFileName");
        return CompletableFuture.supplyAsync(() -> {
            ensureDirectory();
            Path backupPath = resolveBackupPath(backupFileName);
            if (Files.notExists(backupPath) || !Files.isRegularFile(backupPath)) {
                throw new CompletionException(new IOException("Sauvegarde introuvable: " + backupFileName));
            }
            BackupMetadata metadata = metadataFromFile(backupPath)
                    .orElseThrow(() -> new CompletionException(new IOException("Sauvegarde invalide: " + backupFileName)));
            String relative = metadata.baseFileName();
            Path destination = resolveRelative(relative);
            Object lock = fileLocks.computeIfAbsent(relative, key -> new Object());
            synchronized (lock) {
                try {
                    Optional<BackupMetadata> preRestore = Optional.empty();
                    if (Files.exists(destination) && Files.isRegularFile(destination) && retentionLimit.get() != 0) {
                        preRestore = Optional.ofNullable(createBackupLocked(relative, destination));
                    }
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(backupPath, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    logger.info("Configuration restaurée depuis la sauvegarde '" + backupFileName + "'");
                    return new RestoreResult(metadata, preRestore);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }
        }, executor);
    }

    @Override
    public void updateRetentionLimit(int maxBackupsPerFile) {
        if (maxBackupsPerFile < 0) {
            throw new IllegalArgumentException("maxBackupsPerFile doit être >= 0");
        }
        retentionLimit.set(maxBackupsPerFile);
    }

    @Override
    public Path backupsDirectory() {
        return backupsDirectory;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private @Nullable BackupMetadata createBackupLocked(String relative, Path originalFile) throws IOException {
        if (retentionLimit.get() == 0) {
            return null;
        }
        String encodedRelative = encodeRelative(relative);
        String timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now(TIMESTAMP_ZONE));
        Path backupPath = backupsDirectory.resolve(encodedRelative + "." + timestamp + ".bak");
        int counter = 1;
        while (Files.exists(backupPath)) {
            backupPath = backupsDirectory.resolve(encodedRelative + "." + timestamp + "-" + counter + ".bak");
            counter++;
        }
        Path parent = backupPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(originalFile, backupPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        performRotationLocked(relative);
        BasicFileAttributes attrs = Files.readAttributes(backupPath, BasicFileAttributes.class);
        Instant createdAt = parseTimestamp(backupPath.getFileName().toString())
                .orElseGet(() -> attrs.creationTime().toInstant());
        return new BackupMetadata(relative, backupPath.getFileName().toString(), backupPath, createdAt, attrs.size());
    }

    private void performRotationLocked(String relative) throws IOException {
        int limit = retentionLimit.get();
        if (limit <= 0) {
            return;
        }
        String encoded = encodeRelative(relative);
        try (Stream<Path> stream = Files.list(backupsDirectory)) {
            List<Path> backups = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(encoded + ".") && name.endsWith(".bak");
                    })
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .collect(Collectors.toList());
            if (backups.size() <= limit) {
                return;
            }
            for (int i = limit; i < backups.size(); i++) {
                Path toDelete = backups.get(i);
                try {
                    Files.deleteIfExists(toDelete);
                } catch (IOException exception) {
                    logger.warn("Impossible de supprimer l'ancienne sauvegarde " + toDelete.getFileName(), exception);
                }
            }
        }
    }

    private Optional<BackupMetadata> metadataFromFile(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".bak")) {
            return Optional.empty();
        }
        String withoutSuffix = fileName.substring(0, fileName.length() - 4);
        int lastDot = withoutSuffix.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }
        String encodedRelative = withoutSuffix.substring(0, lastDot);
        String decodedRelative = decodeRelative(encodedRelative);
        Instant createdAt = parseTimestamp(fileName)
                .orElseGet(() -> {
                    try {
                        return Files.readAttributes(path, BasicFileAttributes.class).creationTime().toInstant();
                    } catch (IOException exception) {
                        logger.warn("Impossible de lire les métadonnées de la sauvegarde " + fileName, exception);
                        return Instant.now();
                    }
                });
        long size;
        try {
            size = Files.size(path);
        } catch (IOException exception) {
            logger.warn("Impossible de déterminer la taille de la sauvegarde " + fileName, exception);
            size = 0L;
        }
        return Optional.of(new BackupMetadata(decodedRelative, fileName, path, createdAt, size));
    }

    private Optional<Instant> parseTimestamp(String fileName) {
        String withoutSuffix = fileName.endsWith(".bak")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        int lastDot = withoutSuffix.lastIndexOf('.');
        if (lastDot < 0) {
            return Optional.empty();
        }
        String timestampPart = withoutSuffix.substring(lastDot + 1);
        if (timestampPart.length() < TIMESTAMP_LENGTH) {
            return Optional.empty();
        }
        String token = timestampPart.substring(0, TIMESTAMP_LENGTH);
        try {
            LocalDateTime dateTime = LocalDateTime.parse(token, TIMESTAMP_FORMATTER);
            return Optional.of(dateTime.atZone(TIMESTAMP_ZONE).toInstant());
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(backupsDirectory);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private Path resolveBackupPath(String backupFileName) {
        Path resolved = backupsDirectory.resolve(backupFileName).normalize();
        if (!resolved.startsWith(backupsDirectory)) {
            throw new CompletionException(new IOException("Accès à une sauvegarde en dehors du dossier autorisé"));
        }
        return resolved;
    }

    private Path resolveRelative(String relative) {
        Path resolved = dataDirectory.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(dataDirectory)) {
            throw new CompletionException(new IOException("Chemin de sauvegarde en dehors du dossier de données"));
        }
        return resolved;
    }

    private String relativePath(Path path) {
        Path relative = dataDirectory.relativize(path);
        return relative.toString().replace(java.io.File.separatorChar, '/');
    }

    private String encodeRelative(String relative) {
        return URLEncoder.encode(relative, StandardCharsets.UTF_8);
    }

    private String decodeRelative(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private String normaliseRelative(String input) {
        String normalised = input.replace('\\', '/');
        while (normalised.startsWith("./")) {
            normalised = normalised.substring(2);
        }
        while (normalised.startsWith("/")) {
            normalised = normalised.substring(1);
        }
        return normalised;
    }
}
