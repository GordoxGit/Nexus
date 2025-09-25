package com.heneria.nexus.analytics;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * High level service managing the analytics outbox lifecycle.
 */
public final class AnalyticsService implements LifecycleAware {

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final AnalyticsRepository repository;
    private final AtomicReference<CoreConfig.AnalyticsSettings> settingsRef;
    private final ConcurrentLinkedQueue<AnalyticsEvent> outbox = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final Object flushMutex = new Object();

    private volatile CompletableFuture<Void> currentFlush = CompletableFuture.completedFuture(null);
    private volatile BukkitTask flushTask;

    public AnalyticsService(JavaPlugin plugin,
                            NexusLogger logger,
                            AnalyticsRepository repository,
                            CoreConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settingsRef = new AtomicReference<>(Objects.requireNonNull(config, "config").analyticsSettings());
    }

    @Override
    public CompletableFuture<Void> start() {
        CoreConfig.AnalyticsSettings settings = settingsRef.get();
        if (settings == null || !settings.outbox().enabled()) {
            logger.info("Système analytics outbox désactivé");
            accepting.set(false);
            return CompletableFuture.completedFuture(null);
        }
        accepting.set(true);
        scheduleFlush(settings.outbox());
        logger.info(() -> "Outbox analytics activée (intervalle="
                + settings.outbox().flushInterval().toSeconds() + "s, batch="
                + settings.outbox().maxBatchSize() + ")");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        accepting.set(false);
        cancelFlushTask();
        return flushNow();
    }

    /**
     * Records a new analytics event into the outbox.
     */
    public void record(AnalyticsEvent event) {
        Objects.requireNonNull(event, "event");
        if (!accepting.get()) {
            return;
        }
        outbox.add(event);
        pending.incrementAndGet();
        CoreConfig.AnalyticsSettings settings = settingsRef.get();
        if (settings != null && pending.get() >= settings.outbox().maxBatchSize()) {
            triggerFlush(true, false);
        }
    }

    /**
     * Returns the number of events currently buffered in memory.
     */
    public int pendingEvents() {
        return pending.get();
    }

    /**
     * Applies new analytics settings, rescheduling tasks when required.
     */
    public void applySettings(CoreConfig.AnalyticsSettings settings) {
        Objects.requireNonNull(settings, "settings");
        CoreConfig.AnalyticsSettings previous = settingsRef.getAndSet(settings);
        if (!accepting.get()) {
            if (settings.outbox().enabled()) {
                accepting.set(true);
                scheduleFlush(settings.outbox());
                logger.info("Outbox analytics activée après rechargement");
            }
            return;
        }
        if (!settings.outbox().enabled()) {
            accepting.set(false);
            cancelFlushTask();
            logger.info("Outbox analytics désactivée par configuration");
            triggerFlush(true, true);
            return;
        }
        if (previous == null || !previous.outbox().flushInterval().equals(settings.outbox().flushInterval())) {
            cancelFlushTask();
            scheduleFlush(settings.outbox());
            logger.info(() -> "Intervalle de flush analytics mis à "
                    + settings.outbox().flushInterval().toSeconds() + "s");
        }
    }

    /**
     * Forces an immediate flush regardless of the configured interval.
     */
    public CompletableFuture<Void> flushNow() {
        return triggerFlush(false, true);
    }

    private void scheduleFlush(CoreConfig.AnalyticsSettings.OutboxSettings settings) {
        long ticks = Math.max(1L, Math.max(1L, settings.flushInterval().toMillis()) / 50L);
        flushTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, () -> triggerFlush(true, false), ticks, ticks);
    }

    private void cancelFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    private CompletableFuture<Void> triggerFlush(boolean allowSkip, boolean ignoreEnabled) {
        CompletableFuture<Void> action;
        CoreConfig.AnalyticsSettings settings = settingsRef.get();
        synchronized (flushMutex) {
            if (!ignoreEnabled && (settings == null || !settings.outbox().enabled())) {
                return CompletableFuture.completedFuture(null);
            }
            if (!currentFlush.isDone()) {
                action = currentFlush;
            } else {
                List<AnalyticsEvent> drained = drainOutbox();
                if (drained.isEmpty()) {
                    currentFlush = CompletableFuture.completedFuture(null);
                    return currentFlush;
                }
                int drainedSize = drained.size();
                pending.updateAndGet(current -> Math.max(0, current - drainedSize));
                CoreConfig.AnalyticsSettings.OutboxSettings outboxSettings = settings != null
                        ? settings.outbox()
                        : new CoreConfig.AnalyticsSettings.OutboxSettings(true, Duration.ofSeconds(30L), 200);
                currentFlush = persist(drained, outboxSettings);
                action = currentFlush;
            }
        }
        if (allowSkip) {
            return action;
        }
        return action.thenCompose(ignored -> triggerFlush(true, ignoreEnabled));
    }

    private List<AnalyticsEvent> drainOutbox() {
        List<AnalyticsEvent> drained = new ArrayList<>();
        AnalyticsEvent event;
        while ((event = outbox.poll()) != null) {
            drained.add(event);
        }
        return drained;
    }

    private CompletableFuture<Void> persist(List<AnalyticsEvent> drained,
                                            CoreConfig.AnalyticsSettings.OutboxSettings settings) {
        int batchSize = Math.max(1, settings.maxBatchSize());
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (int index = 0; index < drained.size(); index += batchSize) {
            List<AnalyticsEvent> slice = List.copyOf(drained.subList(index, Math.min(drained.size(), index + batchSize)));
            future = future.thenCompose(ignored -> repository.saveBatch(slice)
                    .thenAccept(inserted -> logger.debug(() -> "Flush analytics de " + inserted
                            + " événement(s)")));
        }
        CompletableFuture<Void> finalFuture = future.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                drained.forEach(event -> {
                    outbox.add(event);
                    pending.incrementAndGet();
                });
                logger.warn("Échec du flush analytics (" + drained.size() + " événements re-queue)",
                        throwable instanceof java.util.concurrent.CompletionException completion
                                && completion.getCause() != null ? completion.getCause() : throwable);
            }
            synchronized (flushMutex) {
                currentFlush = CompletableFuture.completedFuture(null);
            }
        });
        return finalFuture;
    }
}
