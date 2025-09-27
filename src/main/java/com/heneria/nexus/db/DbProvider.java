package com.heneria.nexus.db;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides access to the MariaDB datasource.
 */
public final class DbProvider implements LifecycleAware {

    private static final Duration LEAK_DETECTION = Duration.ofSeconds(15);

    private final NexusLogger logger;
    private final JavaPlugin plugin;
    private final AtomicReference<HikariDataSource> dataSourceRef = new AtomicReference<>();
    private final AtomicReference<CoreConfig.DatabaseSettings> settingsRef = new AtomicReference<>();
    private final AtomicLong failedAttempts = new AtomicLong();
    private volatile boolean degraded;

    public DbProvider(NexusLogger logger, JavaPlugin plugin) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CompletableFuture<Boolean> applyConfiguration(CoreConfig.DatabaseSettings settings, Executor executor) {
        settingsRef.set(settings);
        if (!settings.enabled()) {
            logger.info("Persistance désactivée, fonctionnement en mémoire");
            setDegraded(false);
            closeDataSource();
            return CompletableFuture.completedFuture(true);
        }
        logger.info("Initialisation de la base de données MariaDB %s".formatted(settings.jdbcUrl()));
        HikariConfig hikariConfig = toHikariConfig(settings);
        HikariDataSource candidate = new HikariDataSource(hikariConfig);
        return CompletableFuture.supplyAsync(() -> testConnection(candidate), executor)
                .handle((success, throwable) -> {
                    if (throwable != null || Boolean.FALSE.equals(success)) {
                        setDegraded(true);
                        long attempts = failedAttempts.incrementAndGet();
                        closeQuietly(candidate);
                        if (throwable != null) {
                            logger.warn("Connexion MariaDB impossible (tentative %d)".formatted(attempts), throwable);
                        } else {
                            logger.warn("Connexion MariaDB impossible (tentative %d)".formatted(attempts));
                        }
                        return false;
                    }
                    failedAttempts.set(0);
                    setDegraded(false);
                    HikariDataSource previous = dataSourceRef.getAndSet(candidate);
                    closeQuietly(previous);
                    logger.info("Connexion MariaDB opérationnelle (%s)".formatted(settings.jdbcUrl()));
                    return true;
                });
    }

    private boolean testConnection(HikariDataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        } catch (SQLException exception) {
            return false;
        }
    }

    private HikariConfig toHikariConfig(CoreConfig.DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        // Utilise la configuration recommandée pour HikariCP 5.x avec un driver relocalisé
        config.setDataSourceClassName("com.heneria.nexus.lib.mariadb.MariaDbDataSource");
        config.addDataSourceProperty("url", settings.jdbcUrl());
        config.addDataSourceProperty("user", settings.username());
        config.addDataSourceProperty("password", settings.password());
        config.setMaximumPoolSize(settings.poolSettings().maxSize());
        config.setMinimumIdle(settings.poolSettings().minIdle());
        config.setConnectionTimeout(settings.poolSettings().connectionTimeoutMs());
        config.setLeakDetectionThreshold(Math.max(LEAK_DETECTION.toMillis(), TimeUnit.SECONDS.toMillis(2)));
        config.setInitializationFailTimeout(TimeUnit.SECONDS.toMillis(5));
        config.setValidationTimeout(TimeUnit.SECONDS.toMillis(2));
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("Nexus-Hikari");
        return config;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public Connection getConnection() throws SQLException {
        HikariDataSource dataSource = dataSourceRef.get();
        if (dataSource == null) {
            throw new SQLException("Database not available");
        }
        return dataSource.getConnection();
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public <T> CompletableFuture<T> execute(String queryIdentifier, QueryTask<T> task, Executor executor) {
        Objects.requireNonNull(queryIdentifier, "queryIdentifier");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(executor, "executor");
        HikariDataSource dataSource = dataSourceRef.get();
        if (dataSource == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database not available"));
        }
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            try (Connection connection = dataSource.getConnection()) {
                return task.apply(connection);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            } finally {
                final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                CoreConfig.DatabaseSettings settings = settingsRef.get();
                if (settings != null) {
                    CoreConfig.DatabaseSettings.MonitoringSettings monitoring = settings.monitoring();
                    if (monitoring.enableSqlTracing()) {
                        logger.debug(() -> "[SQL TRACE] '%s' exécutée en %d ms".formatted(queryIdentifier, durationMs));
                    }
                    long threshold = monitoring.slowQueryThresholdMs();
                    if (threshold > 0L && durationMs > threshold) {
                        logger.warn("[SLOW QUERY] La requête '%s' a pris %d ms (seuil: %d ms)"
                                .formatted(queryIdentifier, durationMs, threshold));
                    }
                }
            }
        }, executor);
    }

    public CompletableFuture<Boolean> checkHealth(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        HikariDataSource dataSource = dataSourceRef.get();
        if (dataSource == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1");
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            } catch (SQLException exception) {
                logger.debug(() -> "Health check MariaDB en échec : " + exception.getMessage());
                return false;
            }
        }, executor);
    }

    public Diagnostics diagnostics() {
        HikariDataSource dataSource = dataSourceRef.get();
        HikariPoolMXBean pool = dataSource != null ? dataSource.getHikariPoolMXBean() : null;
        CoreConfig.DatabaseSettings settings = settingsRef.get();
        return new Diagnostics(
                settings,
                degraded,
                pool != null ? pool.getActiveConnections() : 0,
                pool != null ? pool.getIdleConnections() : 0,
                pool != null ? pool.getTotalConnections() : 0,
                pool != null ? pool.getThreadsAwaitingConnection() : 0,
                failedAttempts.get());
    }

    private void closeQuietly(HikariDataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            dataSource.close();
        } catch (Exception exception) {
            logger.warn("Erreur lors de la fermeture de la datasource", exception);
        }
    }

    private void closeDataSource() {
        closeQuietly(dataSourceRef.getAndSet(null));
    }

    @Override
    public CompletableFuture<Void> stop() {
        closeDataSource();
        return LifecycleAware.super.stop();
    }

    @FunctionalInterface
    public interface QueryTask<T> {
        T apply(Connection connection) throws SQLException;
    }

    public record Diagnostics(CoreConfig.DatabaseSettings settings,
                              boolean degraded,
                              int activeConnections,
                              int idleConnections,
                              int totalConnections,
                              int awaitingThreads,
                              long failedAttempts) {
    }
}
