package com.heneria.nexus.db;

import com.heneria.nexus.util.NexusLogger;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Applies SQL migrations at startup in a Flyway-like fashion.
 */
public final class DatabaseMigrator {

    private static final String MIGRATION_DIRECTORY = "db/migration";
    private static final Pattern MIGRATION_PATTERN = Pattern.compile("V(?<version>[^_]+)__(?<description>.+)\\.sql");
    private static final Comparator<MigrationScript> SCRIPT_COMPARATOR = (left, right) -> {
        int comparison = compareVersion(left.version(), right.version());
        if (comparison != 0) {
            return comparison;
        }
        return left.scriptName().compareTo(right.scriptName());
    };
    private static final String ENSURE_VERSION_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS nexus_schema_version (" +
                    " installed_rank INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    " version VARCHAR(50) NOT NULL UNIQUE," +
                    " description VARCHAR(200) NOT NULL," +
                    " script VARCHAR(1000) NOT NULL," +
                    " checksum INT," +
                    " installed_by VARCHAR(100) NOT NULL," +
                    " installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    " success BOOLEAN NOT NULL" +
                    ") ENGINE=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";

    private final NexusLogger logger;
    private final JavaPlugin plugin;
    private final DbProvider dbProvider;

    public DatabaseMigrator(NexusLogger logger, JavaPlugin plugin, DbProvider dbProvider) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
    }

    public void migrate() {
        List<MigrationScript> scripts = discoverMigrations();
        if (scripts.isEmpty()) {
            return;
        }
        validateNoDuplicateVersions(scripts);

        Set<String> appliedVersions;
        try (Connection connection = dbProvider.getConnection()) {
            ensureSchemaVersionTableExists(connection);
            AppliedMigrations applied = loadAppliedMigrations(connection);
            if (!applied.failedScripts().isEmpty()) {
                throw new MigrationException("Des migrations précédentes ont échoué : " + String.join(", ", applied.failedScripts()));
            }
            appliedVersions = applied.appliedVersions();
        } catch (SQLException exception) {
            throw new MigrationException("Impossible de préparer les migrations MariaDB", exception);
        }

        for (MigrationScript script : scripts) {
            if (appliedVersions.contains(script.version())) {
                continue;
            }
            applyMigration(script);
        }
    }

    private void applyMigration(MigrationScript script) {
        logger.info("Application de la migration %s...".formatted(script.scriptName()));
        try (Connection connection = dbProvider.getConnection()) {
            boolean previousAutoCommit = true;
            try {
                previousAutoCommit = connection.getAutoCommit();
            } catch (SQLException ignore) {
                // Use default value if the driver does not support getAutoCommit
            }
            connection.setAutoCommit(false);
            try {
                executeSqlScript(connection, script.sql());
                recordMigration(connection, script);
                connection.commit();
                logger.info("Migration %s appliquée".formatted(script.scriptName()));
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw new MigrationException("Échec de la migration %s".formatted(script.scriptName()), exception);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // Ignore
                }
            }
        } catch (SQLException exception) {
            throw new MigrationException("Impossible d'appliquer la migration %s".formatted(script.scriptName()), exception);
        }
    }

    private void executeSqlScript(Connection connection, String script) throws SQLException {
        StringBuilder sanitized = new StringBuilder();
        String[] lines = script.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                continue;
            }
            sanitized.append(line).append('\n');
        }
        String[] statements = sanitized.toString().split(";");
        try (Statement statement = connection.createStatement()) {
            for (String raw : statements) {
                String sql = raw.trim();
                if (sql.isEmpty()) {
                    continue;
                }
                statement.execute(sql);
            }
        }
    }

    private void recordMigration(Connection connection, MigrationScript script) throws SQLException {
        String installedBy = "unknown";
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            if (metaData != null && metaData.getUserName() != null) {
                installedBy = metaData.getUserName();
            }
        } catch (SQLException ignored) {
            // Keep default value
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO nexus_schema_version (version, description, script, checksum, installed_by, success) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, script.version());
            statement.setString(2, script.description());
            statement.setString(3, script.resourcePath());
            statement.setInt(4, script.checksum());
            statement.setString(5, installedBy);
            statement.setBoolean(6, true);
            statement.executeUpdate();
        }
    }

    private void ensureSchemaVersionTableExists(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ENSURE_VERSION_TABLE_SQL);
        }
    }

    private AppliedMigrations loadAppliedMigrations(Connection connection) throws SQLException {
        Set<String> appliedVersions = new HashSet<>();
        List<String> failedScripts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, success, script FROM nexus_schema_version ORDER BY installed_rank")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String version = resultSet.getString("version");
                    boolean success = resultSet.getBoolean("success");
                    String script = resultSet.getString("script");
                    if (success) {
                        appliedVersions.add(version);
                    } else {
                        failedScripts.add(script != null ? script : version);
                    }
                }
            }
        }
        return new AppliedMigrations(Collections.unmodifiableSet(appliedVersions), Collections.unmodifiableList(failedScripts));
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            logger.warn("Impossible d'annuler la transaction de migration", rollbackException);
        }
    }

    private List<MigrationScript> discoverMigrations() {
        CodeSource codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw new MigrationException("Impossible de localiser les scripts de migration");
        }
        try {
            Path rootPath = Path.of(codeSource.getLocation().toURI());
            if (Files.isDirectory(rootPath)) {
                return readScriptsFromDirectory(rootPath.resolve(MIGRATION_DIRECTORY));
            }
            return readScriptsFromArchive(rootPath);
        } catch (URISyntaxException | IOException exception) {
            throw new MigrationException("Impossible de lire les scripts de migration", exception);
        }
    }

    private List<MigrationScript> readScriptsFromDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        List<MigrationScript> scripts = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .map(this::readScript)
                    .forEach(scripts::add);
        }
        scripts.sort(SCRIPT_COMPARATOR);
        return scripts;
    }

    private List<MigrationScript> readScriptsFromArchive(Path archivePath) throws IOException {
        URI jarUri = URI.create("jar:" + archivePath.toUri());
        try (FileSystem fileSystem = newFileSystem(jarUri)) {
            Path directory = fileSystem.getPath(MIGRATION_DIRECTORY);
            if (!Files.exists(directory)) {
                return List.of();
            }
            List<MigrationScript> scripts = new ArrayList<>();
            try (var stream = Files.list(directory)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                        .map(this::readScript)
                        .forEach(scripts::add);
            }
            scripts.sort(SCRIPT_COMPARATOR);
            return scripts;
        }
    }

    private FileSystem newFileSystem(URI uri) throws IOException {
        try {
            return FileSystems.newFileSystem(uri, new HashMap<>());
        } catch (FileSystemAlreadyExistsException ignored) {
            return FileSystems.getFileSystem(uri);
        }
    }

    private MigrationScript readScript(Path path) {
        String fileName = path.getFileName().toString();
        Matcher matcher = MIGRATION_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            throw new MigrationException("Nom de fichier de migration invalide: " + fileName);
        }
        String version = matcher.group("version");
        String description = matcher.group("description").replace('_', ' ');
        String resourcePath = MIGRATION_DIRECTORY + "/" + fileName;
        try {
            String sql = Files.readString(path, StandardCharsets.UTF_8);
            int checksum = computeChecksum(sql);
            return new MigrationScript(version, description, resourcePath, fileName, sql, checksum);
        } catch (IOException exception) {
            throw new MigrationException("Impossible de lire la migration " + fileName, exception);
        }
    }

    private static int computeChecksum(String content) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return (int) crc32.getValue();
    }

    private void validateNoDuplicateVersions(List<MigrationScript> scripts) {
        Set<String> versions = new HashSet<>();
        for (MigrationScript script : scripts) {
            if (!versions.add(script.version())) {
                throw new MigrationException("Version de migration dupliquée: " + script.version());
            }
        }
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? parseVersionComponent(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? parseVersionComponent(rightParts[index]) : 0;
            int comparison = Integer.compare(leftValue, rightValue);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int parseVersionComponent(String component) {
        try {
            return Integer.parseInt(component);
        } catch (NumberFormatException exception) {
            throw new MigrationException("Composant de version invalide: " + component, exception);
        }
    }

    private record MigrationScript(String version,
                                   String description,
                                   String resourcePath,
                                   String scriptName,
                                   String sql,
                                   int checksum) {
    }

    private record AppliedMigrations(Set<String> appliedVersions, List<String> failedScripts) {
    }

    public static final class MigrationException extends RuntimeException {
        public MigrationException(String message) {
            super(message);
        }

        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
