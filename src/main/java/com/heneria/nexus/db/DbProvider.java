package com.heneria.nexus.db;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
    private final AtomicReference<HikariDataSource> dataSourceRef = new AtomicReference<>();
    private final AtomicReference<CoreConfig.DatabaseSettings> settingsRef = new AtomicReference<>();
    private final AtomicLong failedAttempts = new AtomicLong();
    private volatile boolean degraded;

    public DbProvider(NexusLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<Boolean> applyConfiguration(CoreConfig.DatabaseSettings settings, Executor executor) {
        settingsRef.set(settings);
        if (!settings.enabled()) {
            logger.info("Persistance désactivée, fonctionnement en mémoire");
            degraded = false;
            closeDataSource();
            return CompletableFuture.completedFuture(true);
        }
        logger.info("Initialisation de la base de données MariaDB %s".formatted(settings.jdbcUrl()));
        HikariConfig hikariConfig = toHikariConfig(settings);
        HikariDataSource candidate = new HikariDataSource(hikariConfig);
        return CompletableFuture.supplyAsync(() -> testConnection(candidate), executor)
                .handle((success, throwable) -> {
                    if (throwable != null || Boolean.FALSE.equals(success)) {
                        degraded = true;
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
                    degraded = false;
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
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
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

    public <T> CompletableFuture<T> execute(QueryTask<T> task, Executor executor) {
        HikariDataSource dataSource = dataSourceRef.get();
        if (dataSource == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database not available"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return task.apply(connection);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
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
