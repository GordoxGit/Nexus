package com.heneria.nexus.concurrent;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralised executor manager responsible for all asynchronous work dispatched
 * by Nexus. It exposes dedicated pools for IO-bound and compute-bound tasks,
 * CompletableFuture helpers and a safe bridge back to the Paper main thread.
 */
public final class ExecutorManager implements AutoCloseable {

    private static final long BACKPRESSURE_LOG_INTERVAL_MS = 10_000L;

    private final JavaPlugin plugin;
    private final NexusLogger logger;
    private final AtomicReference<CoreConfig.ExecutorSettings> settingsRef;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicLong lastBackpressureWarn = new AtomicLong();
    private final SchedulerBridge schedulerBridge;

    private volatile ManagedExecutor ioExecutor;
    private volatile ManagedExecutor computeExecutor;

    public ExecutorManager(JavaPlugin plugin,
                           NexusLogger logger,
                           CoreConfig.ExecutorSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.settingsRef = new AtomicReference<>(Objects.requireNonNull(settings, "settings"));
        this.schedulerBridge = new PaperSchedulerBridge(plugin, logger, settings.scheduler().mainCheckIntervalTicks());
        this.ioExecutor = createIoExecutor(settings.io());
        this.computeExecutor = createComputeExecutor(settings.compute());
        logConfiguration("Initialisation des exécutors");
    }

    private void logConfiguration(String prefix) {
        CoreConfig.ExecutorSettings current = settingsRef.get();
        logger.info("%s — IO: %s, compute: %d threads".formatted(prefix,
                current.io().virtual() ? "virtual threads" : (current.io().maxThreads() + " threads"),
                current.compute().size()));
    }

    private ManagedExecutor createIoExecutor(CoreConfig.ExecutorSettings.IoSettings settings) {
        Objects.requireNonNull(settings, "settings");
        boolean virtual = settings.virtual();
        if (virtual && !supportsVirtualThreads()) {
            logger.warn("Virtual threads demandés mais indisponibles sur cette JVM, repli sur un pool classique");
            virtual = false;
        }
        if (virtual) {
            ThreadFactory factory = Thread.ofVirtual()
                    .name("Nexus-IO-vt-", 0)
                    .uncaughtExceptionHandler((thread, throwable) ->
                            logger.error("Thread virtuel " + thread.getName() + " terminé de manière inattendue", throwable))
                    .factory();
            ExecutorService executor = Executors.newThreadPerTaskExecutor(factory);
            return new VirtualManagedExecutor("io", executor, logger, null);
        }
        int maxThreads = Math.max(2, settings.maxThreads());
        int coreThreads = Math.min(maxThreads, Math.max(2, Runtime.getRuntime().availableProcessors()));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                Math.max(1000L, settings.keepAliveMs()),
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("Nexus-IO", false, logger));
        executor.allowCoreThreadTimeOut(true);
        return new PooledManagedExecutor("io", executor, maxThreads, logger, null);
    }

    private ManagedExecutor createComputeExecutor(CoreConfig.ExecutorSettings.ComputeSettings settings) {
        Objects.requireNonNull(settings, "settings");
        int size = Math.max(1, settings.size());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size,
                size,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("Nexus-Compute", false, logger));
        executor.allowCoreThreadTimeOut(false);
        return new PooledManagedExecutor("compute", executor, size, logger, this::monitorBackpressure);
    }

    private void monitorBackpressure(ManagedExecutor executor) {
        int queueSize = executor.queueSize();
        if (queueSize <= 0) {
            return;
        }
        int threshold = Math.max(10, executor.configuredSize() * 2);
        if (queueSize < threshold) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastBackpressureWarn.get();
        if (now - previous > BACKPRESSURE_LOG_INTERVAL_MS && lastBackpressureWarn.compareAndSet(previous, now)) {
            logger.warn("Saturation du pool compute (file=%d). Ajustez executors.compute.size si nécessaire.".formatted(queueSize));
        }
    }

    private boolean supportsVirtualThreads() {
        try {
            Thread.ofVirtual();
            return Runtime.version().feature() >= 21;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public Executor io() {
        return ioExecutor;
    }

    public Executor compute() {
        return computeExecutor;
    }

    public SchedulerBridge mainThread() {
        return schedulerBridge;
    }

    public PoolStats stats() {
        return new PoolStats(ioExecutor.snapshot(), computeExecutor.snapshot());
    }

    public <T> CompletableFuture<T> supplyIo(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return CompletableFuture.supplyAsync(supplier, ioExecutor);
    }

    public CompletableFuture<Void> runIo(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return CompletableFuture.runAsync(runnable, ioExecutor);
    }

    public <T> CompletableFuture<T> supplyCompute(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return CompletableFuture.supplyAsync(supplier, computeExecutor);
    }

    public CompletableFuture<Void> runCompute(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return CompletableFuture.runAsync(runnable, computeExecutor);
    }

    public <T> CompletableFuture<Void> thenMain(CompletionStage<T> stage, Consumer<? super T> consumer) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<Void> result = new CompletableFuture<>();
        stage.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(unwrapCompletion(throwable));
                return;
            }
            if (!plugin.isEnabled()) {
                logger.debug(() -> "Callback main thread ignorée: plugin désactivé");
                result.complete(null);
                return;
            }
            schedulerBridge.runNow(() -> {
                if (!plugin.isEnabled()) {
                    result.complete(null);
                    return;
                }
                try {
                    consumer.accept(value);
                    result.complete(null);
                } catch (Throwable throwable1) {
                    result.completeExceptionally(throwable1);
                    throw throwable1;
                }
            });
        });
        return result;
    }

    public CompletableFuture<Void> exceptionMain(CompletionStage<?> stage, Consumer<Throwable> handler) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(handler, "handler");
        CompletableFuture<Void> result = new CompletableFuture<>();
        stage.whenComplete((value, throwable) -> {
            if (throwable == null) {
                result.complete(null);
                return;
            }
            Throwable cause = unwrapCompletion(throwable);
            if (!plugin.isEnabled()) {
                result.completeExceptionally(cause);
                return;
            }
            schedulerBridge.runNow(() -> {
                try {
                    handler.accept(cause);
                    result.complete(null);
                } catch (Throwable throwable1) {
                    result.completeExceptionally(throwable1);
                    throw throwable1;
                }
            });
        });
        return result;
    }

    public <T> CompletionStage<T> withTimeout(CompletionStage<T> stage, Duration timeout) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            return stage;
        }
        CompletableFuture<T> mirror = toCompletableFuture(stage);
        mirror.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return mirror.exceptionallyCompose(throwable -> {
            Throwable cause = unwrapCompletion(throwable);
            if (cause instanceof TimeoutException timeoutException) {
                String message = "Opération asynchrone expirée après " + timeout.toMillis() + " ms";
                AsyncTimeoutException asyncTimeout = new AsyncTimeoutException(message, timeoutException);
                logger.warn(message);
                return CompletableFuture.failedFuture(asyncTimeout);
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    public <T> CompletableFuture<List<T>> combineAll(List<? extends CompletionStage<? extends T>> stages) {
        Objects.requireNonNull(stages, "stages");
        if (stages.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<? extends T>> futures = stages.stream()
                .map(this::toCompletableFuture)
                .toList();
        CompletableFuture<?>[] array = futures.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(array).thenApply(ignored -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    public <T> CompletableFuture<T> anyOfFast(List<? extends CompletionStage<? extends T>> stages) {
        Objects.requireNonNull(stages, "stages");
        if (stages.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Liste vide"));
        }
        List<CompletableFuture<? extends T>> futures = stages.stream()
                .map(this::toCompletableFuture)
                .toList();
        CompletableFuture<T> result = new CompletableFuture<>();
        for (CompletableFuture<? extends T> future : futures) {
            future.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(unwrapCompletion(throwable));
                } else {
                    result.complete(value);
                }
            });
        }
        result.whenComplete((value, throwable) -> futures.forEach(future -> future.cancel(true)));
        return result;
    }

    public synchronized void reconfigure(CoreConfig.ExecutorSettings newSettings) {
        Objects.requireNonNull(newSettings, "newSettings");
        CoreConfig.ExecutorSettings current = settingsRef.get();
        if (current.equals(newSettings)) {
            return;
        }
        ManagedExecutor newIo = createIoExecutor(newSettings.io());
        ManagedExecutor newCompute = createComputeExecutor(newSettings.compute());
        ManagedExecutor oldIo = this.ioExecutor;
        ManagedExecutor oldCompute = this.computeExecutor;
        this.ioExecutor = newIo;
        this.computeExecutor = newCompute;
        this.settingsRef.set(newSettings);
        if (schedulerBridge instanceof PaperSchedulerBridge bridge) {
            bridge.updateInterval(newSettings.scheduler().mainCheckIntervalTicks());
        }
        logConfiguration("Reconfiguration des exécutors");
        shutdownAsync("IO", oldIo, current.shutdown());
        shutdownAsync("Compute", oldCompute, current.shutdown());
    }

    private void shutdownAsync(String name, ManagedExecutor executor, CoreConfig.ExecutorSettings.ShutdownSettings settings) {
        if (executor == null) {
            return;
        }
        CompletableFuture.runAsync(() ->
                stopExecutor(name, executor, settings, null));
    }

    public void shutdownGracefully(Duration timeout) {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        CoreConfig.ExecutorSettings current = settingsRef.get();
        Duration remaining = (timeout != null && timeout.compareTo(Duration.ZERO) > 0) ? timeout : null;
        remaining = stopExecutor("IO", ioExecutor, current.shutdown(), remaining);
        stopExecutor("Compute", computeExecutor, current.shutdown(), remaining);
        schedulerBridge.close();
    }

    private Duration stopExecutor(String name,
                                  ManagedExecutor executor,
                                  CoreConfig.ExecutorSettings.ShutdownSettings settings,
                                  Duration remaining) {
        if (executor == null) {
            return remaining;
        }
        executor.shutdown();
        Duration await = clampDuration(Duration.ofSeconds(settings.awaitSeconds()), remaining);
        boolean finished = awaitTermination(executor, await);
        remaining = subtractDuration(remaining, await);
        if (!finished) {
            logger.warn("Arrêt du pool %s: dépassement du délai de %d ms, annulation forcée".formatted(name, await.toMillis()));
            List<Runnable> dropped = executor.shutdownNow();
            Duration force = clampDuration(Duration.ofSeconds(settings.forceCancelSeconds()), remaining);
            finished = awaitTermination(executor, force);
            remaining = subtractDuration(remaining, force);
            if (!dropped.isEmpty()) {
                logger.warn("%d tâches %s annulées lors de l'arrêt".formatted(dropped.size(), name));
            }
            if (!finished) {
                logger.warn("Le pool %s n'a pas terminé proprement".formatted(name));
            }
        }
        PoolSnapshot snapshot = executor.snapshot();
        logger.info("Pool %s arrêté: soumis=%d terminés=%d rejetés=%d file=%d".formatted(
                name,
                snapshot.submittedTasks(),
                snapshot.completedTasks(),
                snapshot.rejectedTasks(),
                snapshot.queuedTasks()));
        return remaining;
    }

    private Duration clampDuration(Duration requested, Duration remaining) {
        if (requested.isNegative()) {
            return Duration.ZERO;
        }
        if (remaining == null) {
            return requested;
        }
        if (remaining.isZero() || remaining.isNegative()) {
            return Duration.ZERO;
        }
        return requested.compareTo(remaining) > 0 ? remaining : requested;
    }

    private Duration subtractDuration(Duration remaining, Duration spent) {
        if (remaining == null || spent.isZero()) {
            return remaining;
        }
        Duration next = remaining.minus(spent);
        if (next.isNegative()) {
            return Duration.ZERO;
        }
        return next;
    }

    private boolean awaitTermination(ManagedExecutor executor, Duration duration) {
        if (duration.isZero()) {
            return executor.isTerminated();
        }
        try {
            return executor.awaitTermination(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Interruption lors de l'arrêt du pool " + executor.name(), exception);
            return executor.isTerminated();
        }
    }

    private <T> CompletableFuture<T> toCompletableFuture(CompletionStage<? extends T> stage) {
        if (stage instanceof CompletableFuture<? extends T> future) {
            return (CompletableFuture<T>) future;
        }
        CompletableFuture<T> mirror = new CompletableFuture<>();
        stage.whenComplete((value, throwable) -> {
            if (throwable != null) {
                mirror.completeExceptionally(throwable);
            } else {
                mirror.complete(value);
            }
        });
        return mirror;
    }

    private Throwable unwrapCompletion(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Override
    public void close() {
        shutdownGracefully(null);
    }

    private abstract static class ManagedExecutor implements Executor {

        private final String name;
        private final boolean virtual;
        private final int configuredSize;
        private final NexusLogger logger;
        private final Consumer<ManagedExecutor> afterSubmit;
        private final LongAdder submitted = new LongAdder();
        private final LongAdder completed = new LongAdder();
        private final LongAdder rejected = new LongAdder();
        private final LongAdder totalExecutionNanos = new LongAdder();
        private final AtomicInteger running = new AtomicInteger();

        ManagedExecutor(String name,
                        boolean virtual,
                        int configuredSize,
                        NexusLogger logger,
                        Consumer<ManagedExecutor> afterSubmit) {
            this.name = name;
            this.virtual = virtual;
            this.configuredSize = configuredSize;
            this.logger = logger;
            this.afterSubmit = afterSubmit;
        }

        String name() {
            return name;
        }

        @Override
        public final void execute(Runnable command) {
            Objects.requireNonNull(command, "command");
            Runnable wrapped = () -> {
                running.incrementAndGet();
                long start = System.nanoTime();
                try {
                    command.run();
                } finally {
                    long duration = System.nanoTime() - start;
                    totalExecutionNanos.add(duration);
                    running.decrementAndGet();
                    completed.increment();
                }
            };
            submitted.increment();
            try {
                doExecute(wrapped);
                if (afterSubmit != null) {
                    afterSubmit.accept(this);
                }
            } catch (RejectedExecutionException exception) {
                rejected.increment();
                logger.warn("Tâche rejetée par le pool " + name + ": " + exception.getMessage());
                throw exception;
            }
        }

        abstract void doExecute(Runnable runnable);

        abstract void shutdown();

        abstract List<Runnable> shutdownNow();

        abstract boolean awaitTermination(Duration duration) throws InterruptedException;

        abstract boolean isShutdown();

        abstract boolean isTerminated();

        abstract int queueSize();

        abstract int poolSize();

        int configuredSize() {
            return configuredSize;
        }

        PoolSnapshot snapshot() {
            long completedTasks = completed.sum();
            double averageMillis = completedTasks == 0L
                    ? 0.0d
                    : (double) totalExecutionNanos.sum() / completedTasks / 1_000_000.0d;
            return new PoolSnapshot(
                    name,
                    virtual,
                    configuredSize,
                    poolSize(),
                    running.get(),
                    queueSize(),
                    submitted.sum(),
                    completedTasks,
                    rejected.sum(),
                    averageMillis,
                    Instant.now());
        }

        int activeTasks() {
            return running.get();
        }
    }

    private static final class PooledManagedExecutor extends ManagedExecutor {

        private final ThreadPoolExecutor delegate;

        PooledManagedExecutor(String name,
                              ThreadPoolExecutor delegate,
                              int configuredSize,
                              NexusLogger logger,
                              Consumer<ManagedExecutor> afterSubmit) {
            super(name, false, configuredSize, logger, afterSubmit);
            this.delegate = delegate;
        }

        @Override
        void doExecute(Runnable runnable) {
            delegate.execute(runnable);
        }

        @Override
        void shutdown() {
            delegate.shutdown();
        }

        @Override
        List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        boolean awaitTermination(Duration duration) throws InterruptedException {
            long millis = Math.max(0L, duration.toMillis());
            return delegate.awaitTermination(millis, TimeUnit.MILLISECONDS);
        }

        @Override
        boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        int queueSize() {
            return delegate.getQueue().size();
        }

        @Override
        int poolSize() {
            return delegate.getPoolSize();
        }
    }

    private static final class VirtualManagedExecutor extends ManagedExecutor {

        private final ExecutorService delegate;

        VirtualManagedExecutor(String name,
                               ExecutorService delegate,
                               NexusLogger logger,
                               Consumer<ManagedExecutor> afterSubmit) {
            super(name, true, -1, logger, afterSubmit);
            this.delegate = delegate;
        }

        @Override
        void doExecute(Runnable runnable) {
            delegate.execute(runnable);
        }

        @Override
        void shutdown() {
            delegate.shutdown();
        }

        @Override
        List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        boolean awaitTermination(Duration duration) throws InterruptedException {
            long millis = Math.max(0L, duration.toMillis());
            return delegate.awaitTermination(millis, TimeUnit.MILLISECONDS);
        }

        @Override
        boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        int queueSize() {
            return 0;
        }

        @Override
        int poolSize() {
            return activeTasks();
        }
    }

    public record PoolStats(PoolSnapshot io, PoolSnapshot compute) {
    }

    public record PoolSnapshot(String name,
                               boolean virtual,
                               int configuredThreads,
                               int poolSize,
                               int activeTasks,
                               int queuedTasks,
                               long submittedTasks,
                               long completedTasks,
                               long rejectedTasks,
                               double averageExecutionMillis,
                               Instant snapshotTime) {
    }
}
