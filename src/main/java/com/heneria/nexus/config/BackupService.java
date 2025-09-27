package com.heneria.nexus.config;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Service responsible for managing configuration file backups.
 */
public interface BackupService extends AutoCloseable {

    /**
     * Creates a new backup for the given file if backups are enabled.
     *
     * @param originalFile absolute path to the file to back up
     * @return future holding metadata for the created backup or empty if backups are disabled
     */
    CompletableFuture<Optional<BackupMetadata>> createBackup(Path originalFile);

    /**
     * Lists the available backups. When {@code baseFile} is provided only backups for
     * that file are returned.
     *
     * @param baseFile optional relative path within the data folder (using forward slashes)
     * @return future with ordered metadata, newest first
     */
    CompletableFuture<List<BackupMetadata>> listBackups(@Nullable String baseFile);

    /**
     * Retrieves metadata for the given backup file name.
     *
     * @param backupFileName file present inside the backups directory
     * @return future containing the metadata if it exists
     */
    CompletableFuture<Optional<BackupMetadata>> getBackup(String backupFileName);

    /**
     * Restores the configuration file from the provided backup.
     *
     * <p>The current version of the file will be backed up before being overwritten.</p>
     *
     * @param backupFileName name of the backup file inside the backups directory
     * @return future describing the operation
     */
    CompletableFuture<RestoreResult> restoreBackup(String backupFileName);

    /**
     * Updates the retention policy controlling how many backups are kept per file.
     *
     * @param maxBackupsPerFile non-negative value, {@code 0} disables backups entirely
     */
    void updateRetentionLimit(int maxBackupsPerFile);

    /**
     * Returns the path to the backups directory.
     */
    Path backupsDirectory();

    /**
     * {@inheritDoc}
     */
    @Override
    void close();

    /**
     * Immutable description of a backup file.
     */
    record BackupMetadata(String baseFileName,
                          String backupFileName,
                          Path path,
                          Instant createdAt,
                          long sizeBytes) {
    }

    /**
     * Information returned after a successful restoration.
     */
    record RestoreResult(BackupMetadata restoredBackup,
                         Optional<BackupMetadata> preRestoreBackup) {
    }
}
