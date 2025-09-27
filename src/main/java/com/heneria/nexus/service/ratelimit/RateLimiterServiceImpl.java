package com.heneria.nexus.service.ratelimit;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.repository.RateLimitRepository;
import com.heneria.nexus.ratelimit.RateLimitResult;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Default implementation orchestrating rate limit checks and background cleanup.
 */
public final class RateLimiterServiceImpl implements RateLimiterService {

    private static final Duration MIN_RETENTION = Duration.ofMinutes(10);

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final RateLimitRepository repository;
    private final AtomicReference<ServiceConfiguration> configurationRef;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object taskLock = new Object();

    private BukkitTask cleanupTask;

    public RateLimiterServiceImpl(JavaPlugin plugin,
                                  NexusLogger logger,
                                  RateLimitRepository repository,
                                  CoreConfig coreConfig) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.repository = Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.configurationRef = new AtomicReference<>(toConfiguration(coreConfig));
    }

    @Override
    public CompletableFuture<Void> start() {
        running.set(true);
        scheduleCleanup();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        running.set(false);
        cancelCleanup();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<RateLimitResult> check(UUID playerUuid, String actionKey, Duration cooldown) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(actionKey, "actionKey");
        Objects.requireNonNull(cooldown, "cooldown");
        ServiceConfiguration configuration = configurationRef.get();
        if (!configuration.enabled() || !configuration.databaseEnabled() || cooldown.isZero() || cooldown.isNegative()) {
            return CompletableFuture.completedFuture(RateLimitResult.allowed());
        }
        return repository.checkAndRecord(playerUuid, actionKey, cooldown)
                .exceptionally(throwable -> {
                    Throwable cause = unwrap(throwable);
                    logger.warn("Vérification de rate limit en échec pour " + actionKey, cause);
                    return RateLimitResult.allowed();
                });
    }

    @Override
    public void applyConfiguration(CoreConfig coreConfig) {
        Objects.requireNonNull(coreConfig, "coreConfig");
        ServiceConfiguration configuration = toConfiguration(coreConfig);
        ServiceConfiguration previous = configurationRef.getAndSet(configuration);
        if (!running.get()) {
            return;
        }
        if (!configuration.equals(previous)) {
            scheduleCleanup();
        }
    }

    private void scheduleCleanup() {
        if (!running.get()) {
            return;
        }
        ServiceConfiguration configuration = configurationRef.get();
        cancelCleanup();
        if (!configuration.enabled() || !configuration.databaseEnabled()) {
            return;
        }
        long periodTicks = Math.max(1L, (configuration.cleanupInterval().toMillis() + 49L) / 50L);
        synchronized (taskLock) {
            cleanupTask = plugin.getServer().getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::runCleanup, periodTicks, periodTicks);
        }
        logger.info(() -> "Nettoyage des limiteurs de taux planifié toutes "
                + configuration.cleanupInterval().toMinutes() + " minute(s)");
    }

    private void cancelCleanup() {
        synchronized (taskLock) {
            if (cleanupTask != null) {
                cleanupTask.cancel();
                cleanupTask = null;
            }
        }
    }

    private void runCleanup() {
        if (!running.get()) {
            return;
        }
        ServiceConfiguration configuration = configurationRef.get();
        if (!configuration.enabled() || !configuration.databaseEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(configuration.retentionDuration());
        repository.purgeOlderThan(cutoff).whenComplete((deleted, throwable) -> {
            if (throwable != null) {
                logger.warn("Nettoyage des limiteurs de taux en échec", unwrap(throwable));
                return;
            }
            if (deleted != null && deleted > 0) {
                logger.debug(() -> "Nettoyage des limiteurs de taux — " + deleted + " entrées supprimées");
            }
        });
    }

    private ServiceConfiguration toConfiguration(CoreConfig coreConfig) {
        CoreConfig.RateLimitSettings rateLimits = coreConfig.rateLimitSettings();
        boolean databaseEnabled = coreConfig.databaseSettings().enabled();
        Duration cleanupInterval = rateLimits.cleanupInterval();
        Duration retention = rateLimits.retentionDuration();
        Duration maxCooldown = rateLimits.maxConfiguredCooldown();
        if (retention.compareTo(maxCooldown) < 0) {
            retention = maxCooldown;
        }
        if (retention.compareTo(MIN_RETENTION) < 0) {
            retention = MIN_RETENTION;
        }
        return new ServiceConfiguration(rateLimits.enabled(), databaseEnabled, cleanupInterval, retention);
    }

    private Throwable unwrap(Throwable throwable) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    private record ServiceConfiguration(boolean enabled,
                                        boolean databaseEnabled,
                                        Duration cleanupInterval,
                                        Duration retentionDuration) {
    }
}
