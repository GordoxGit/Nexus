package com.heneria.nexus.config;

import com.heneria.nexus.util.NexusLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Applies versioned migrations to user configuration files, ensuring that
 * custom values are preserved while schema changes are reconciled with the
 * defaults shipped with the plugin.
 */
public final class ConfigMigrator {

    private static final long MIN_VERSION = 1L;

    private final NexusLogger logger;
    private final Map<String, List<MigrationStep>> migrations;

    public ConfigMigrator(NexusLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.migrations = initialiseMigrations();
    }

    private Map<String, List<MigrationStep>> initialiseMigrations() {
        Map<String, List<MigrationStep>> map = new HashMap<>();
        List<MigrationStep> configMigrations = new ArrayList<>();
        configMigrations.add(new MigrationStep(2, ConfigMigrator::migrateConfigToV2));
        configMigrations.add(new MigrationStep(3, ConfigMigrator::migrateConfigToV3));
        map.put("config.yml", Collections.unmodifiableList(configMigrations));
        map.put("economy.yml", List.of());
        map.put("maps.yml", List.of());
        map.put("holograms.yml", List.of());
        map.put("lang/messages_fr.yml", List.of());
        return Collections.unmodifiableMap(map);
    }

    public void migrate(Path dataDirectory,
                        String resourceName,
                        YamlConfiguration configuration,
                        YamlConfiguration defaults) throws IOException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(defaults, "defaults");

        long targetVersion = Math.max(MIN_VERSION, readVersion(defaults, resourceName, true));
        Object rawUserVersion = configuration.get("config-version");
        boolean hasExplicitVersion = rawUserVersion != null;
        long userVersion = Math.max(MIN_VERSION, readVersion(configuration, resourceName, false));

        if (hasExplicitVersion && userVersion > targetVersion) {
            logger.warn("Le fichier '" + resourceName + "' est en version " + userVersion
                    + " (supérieure à la version supportée " + targetVersion + ")");
            return;
        }

        boolean requiresUpdate = !hasExplicitVersion || userVersion < targetVersion;
        if (!requiresUpdate) {
            if (userVersion != targetVersion) {
                configuration.set("config-version", targetVersion);
            }
            return;
        }

        Path filePath = dataDirectory.resolve(resourceName);
        Path backupPath = null;
        long fromVersion = hasExplicitVersion ? userVersion : MIN_VERSION;
        if (Files.exists(filePath)) {
            backupPath = createBackup(filePath, fromVersion);
        }

        applySteps(resourceName, configuration, fromVersion, targetVersion);
        configuration.set("config-version", targetVersion);
        configuration.setDefaults(defaults);
        configuration.options().copyDefaults(true);

        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            configuration.save(filePath.toFile());
        } catch (IOException exception) {
            restoreBackup(filePath, backupPath);
            throw new IOException("Impossible de sauvegarder le fichier migré '" + resourceName + "'", exception);
        }

        String message;
        if (fromVersion == targetVersion) {
            message = "Le fichier '" + resourceName + "' a été mis à jour (version "
                    + targetVersion + ")";
        } else {
            message = "Le fichier '" + resourceName + "' a été migré de la version "
                    + fromVersion + " à la version " + targetVersion + ".";
        }
        if (backupPath != null) {
            message += " Sauvegarde: " + backupPath.getFileName();
        }
        logger.info(message);
    }

    private long readVersion(YamlConfiguration configuration,
                             String resourceName,
                             boolean defaults) {
        Object raw = configuration.get("config-version");
        if (raw == null) {
            if (defaults) {
                logger.warn("La ressource '" + resourceName + "' ne définit pas 'config-version'.");
            }
            return MIN_VERSION;
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value < MIN_VERSION) {
                logger.warn("Valeur 'config-version' invalide dans '" + resourceName
                        + "': " + value + ". Utilisation de " + MIN_VERSION + ".");
                return MIN_VERSION;
            }
            return value;
        }
        if (raw instanceof String string) {
            try {
                long value = Long.parseLong(string.trim());
                if (value < MIN_VERSION) {
                    logger.warn("Valeur 'config-version' invalide dans '" + resourceName
                            + "': " + string + ". Utilisation de " + MIN_VERSION + ".");
                    return MIN_VERSION;
                }
                return value;
            } catch (NumberFormatException exception) {
                logger.warn("Valeur 'config-version' invalide dans '" + resourceName
                        + "': " + string + ". Utilisation de " + MIN_VERSION + ".");
                return MIN_VERSION;
            }
        }
        logger.warn("Valeur 'config-version' invalide dans '" + resourceName + "'. Utilisation de "
                + MIN_VERSION + ".");
        return MIN_VERSION;
    }

    private void applySteps(String resourceName,
                            YamlConfiguration configuration,
                            long fromVersion,
                            long targetVersion) {
        List<MigrationStep> steps = migrations.get(resourceName);
        if (steps == null || steps.isEmpty()) {
            return;
        }
        List<MigrationStep> ordered = new ArrayList<>(steps);
        ordered.sort(Comparator.comparingLong(MigrationStep::targetVersion));
        long current = fromVersion;
        for (MigrationStep step : ordered) {
            if (current < step.targetVersion() && targetVersion >= step.targetVersion()) {
                step.action().accept(configuration);
                current = step.targetVersion();
            }
        }
    }

    private Path createBackup(Path filePath, long version) throws IOException {
        Path backupPath = filePath.resolveSibling(
                filePath.getFileName().toString() + ".v" + Math.max(version, MIN_VERSION) + ".bak");
        Path parent = backupPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }

    private void restoreBackup(Path filePath, Path backupPath) {
        if (backupPath == null) {
            return;
        }
        try {
            Files.move(backupPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.error("Impossible de restaurer la sauvegarde '" + backupPath.getFileName()
                    + "' après un échec de migration", exception);
        }
    }

    private static void migrateConfigToV2(YamlConfiguration configuration) {
        if (configuration.contains("threads")) {
            return;
        }
        if (!configuration.contains("executors")) {
            return;
        }
        Object virtual = configuration.get("executors.io.virtual");
        if (virtual != null && !configuration.contains("threads.io_virtual")) {
            configuration.set("threads.io_virtual", virtual);
        }
        Object maxThreads = configuration.get("executors.io.maxThreads");
        if (maxThreads != null && !configuration.contains("threads.io_pool")) {
            configuration.set("threads.io_pool", maxThreads);
        }
        Object keepAlive = configuration.get("executors.io.keepAliveMs");
        if (keepAlive != null && !configuration.contains("threads.io_keep_alive_ms")) {
            configuration.set("threads.io_keep_alive_ms", keepAlive);
        }
        Object computeSize = configuration.get("executors.compute.size");
        if (computeSize != null && !configuration.contains("threads.compute_pool")) {
            configuration.set("threads.compute_pool", computeSize);
        }
        Object shutdownAwait = configuration.get("executors.shutdown.awaitSeconds");
        if (shutdownAwait != null && !configuration.contains("threads.shutdown.await_seconds")) {
            configuration.set("threads.shutdown.await_seconds", shutdownAwait);
        }
        Object shutdownForce = configuration.get("executors.shutdown.forceCancelSeconds");
        if (shutdownForce != null && !configuration.contains("threads.shutdown.force_cancel_seconds")) {
            configuration.set("threads.shutdown.force_cancel_seconds", shutdownForce);
        }
        Object scheduler = configuration.get("executors.scheduler.main_check_interval_ticks");
        if (scheduler != null && !configuration.contains("threads.scheduler.main_check_interval_ticks")) {
            configuration.set("threads.scheduler.main_check_interval_ticks", scheduler);
        }
        configuration.set("executors", null);
    }

    private static void migrateConfigToV3(YamlConfiguration configuration) {
        if (configuration.contains("rate-limits")) {
            return;
        }
        configuration.set("rate-limits.enabled", true);
        configuration.set("rate-limits.cleanup.interval_minutes", 60);
        configuration.set("rate-limits.cleanup.retention_minutes", 1440);
        configuration.set("rate-limits.actions.purchase:class", 3);
        configuration.set("rate-limits.actions.purchase:cosmetic", 3);
        configuration.set("rate-limits.actions.shop:refresh", 60);
        configuration.set("rate-limits.actions.quest:reroll_daily", 300);
    }

    private record MigrationStep(long targetVersion, Consumer<YamlConfiguration> action) {
    }
}
