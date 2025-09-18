package com.heneria.nexus.service;

import com.heneria.nexus.config.NexusConfig;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages the compute and IO thread pools used throughout the plugin.
 */
public final class ExecutorPools implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final NexusLogger logger;
    private volatile NexusConfig.ThreadSettings settings;
    private volatile ThreadPoolExecutor ioExecutor;
    private volatile ThreadPoolExecutor computeExecutor;

    public ExecutorPools(NexusLogger logger, NexusConfig.ThreadSettings settings) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.ioExecutor = createExecutor("IO", settings.ioPool());
        this.computeExecutor = createExecutor("Compute", settings.computePool());
    }

    private ThreadPoolExecutor createExecutor(String name, int size) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size,
                size,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("Nexus-" + name, false, logger));
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    public ExecutorService ioExecutor() {
        return ioExecutor;
    }

    public ExecutorService computeExecutor() {
        return computeExecutor;
    }

    public synchronized void reconfigure(NexusConfig.ThreadSettings newSettings) {
        if (this.settings.equals(newSettings)) {
            return;
        }
        logger.info("Reconfiguration des pools de threads: io=%d compute=%d".formatted(newSettings.ioPool(), newSettings.computePool()));
        ThreadPoolExecutor newIo = createExecutor("IO", newSettings.ioPool());
        ThreadPoolExecutor newCompute = createExecutor("Compute", newSettings.computePool());

        ThreadPoolExecutor oldIo = this.ioExecutor;
        ThreadPoolExecutor oldCompute = this.computeExecutor;
        this.ioExecutor = newIo;
        this.computeExecutor = newCompute;
        this.settings = newSettings;

        shutdownAsync(oldIo, "IO");
        shutdownAsync(oldCompute, "Compute");
    }

    private void shutdownAsync(ThreadPoolExecutor executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        CompletableFuture.runAsync(() -> awaitTermination(executor, name));
    }

    private void awaitTermination(ThreadPoolExecutor executor, String name) {
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("Arrêt forcé du pool " + name);
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Interruption lors de l'arrêt du pool " + name, exception);
            executor.shutdownNow();
        }
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(settings, snapshot(ioExecutor), snapshot(computeExecutor));
    }

    private PoolSnapshot snapshot(ThreadPoolExecutor executor) {
        if (executor == null) {
            return PoolSnapshot.empty();
        }
        return new PoolSnapshot(
                executor.getCorePoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount());
    }

    @Override
    public void close() {
        shutdownAsync(ioExecutor, "IO");
        shutdownAsync(computeExecutor, "Compute");
    }

    public record Diagnostics(NexusConfig.ThreadSettings settings, PoolSnapshot io, PoolSnapshot compute) {
    }

    public record PoolSnapshot(int corePoolSize, int poolSize, int activeCount, int queuedTasks, long completedTasks) {
        private static PoolSnapshot empty() {
            return new PoolSnapshot(0, 0, 0, 0, 0L);
        }
    }
}
