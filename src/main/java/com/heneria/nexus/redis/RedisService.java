package com.heneria.nexus.redis;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.security.ChannelSecurityManager;
import com.heneria.nexus.service.LifecycleAware;
import com.heneria.nexus.util.MessageFacade;
import com.heneria.nexus.util.NamedThreadFactory;
import com.heneria.nexus.util.NexusLogger;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Manages the lifecycle of the Redis connection and pub/sub listeners.
 */
public final class RedisService implements LifecycleAware {

    private static final long SUBSCRIPTION_RETRY_DELAY_MS = 2_000L;
    private static final long INITIAL_RECONNECT_DELAY_SECONDS = 2L;
    private static final long MAX_RECONNECT_DELAY_SECONDS = 60L;

    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final JavaPlugin plugin;
    private final MessageFacade messageFacade;
    private final ChannelSecurityManager channelSecurityManager;
    private final AtomicReference<CoreConfig.RedisSettings> settingsRef;
    private final AtomicReference<CoreConfig.DegradedModeSettings> degradedSettings;
    private final ScheduledExecutorService reconnectScheduler;
    private final AtomicReference<JedisPool> poolRef = new AtomicReference<>();
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISABLED);
    private final AtomicReference<Throwable> lastError = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> reconnectFuture = new AtomicReference<>();
    private final ConcurrentMap<Long, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionIds = new AtomicLong();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicBoolean degradedNotified = new AtomicBoolean();

    public RedisService(NexusLogger logger,
                        ExecutorManager executorManager,
                        JavaPlugin plugin,
                        MessageFacade messageFacade,
                        ChannelSecurityManager channelSecurityManager,
                        CoreConfig coreConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageFacade = Objects.requireNonNull(messageFacade, "messageFacade");
        this.channelSecurityManager = Objects.requireNonNull(channelSecurityManager, "channelSecurityManager");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.settingsRef = new AtomicReference<>(coreConfig.redisSettings());
        this.degradedSettings = new AtomicReference<>(coreConfig.degradedModeSettings());
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("Nexus-Redis-Reconnect", true, logger));
    }

    @Override
    public CompletableFuture<Void> start() {
        started.set(true);
        CoreConfig.RedisSettings settings = settingsRef.get();
        if (settings == null || !settings.enabled()) {
            updateState(ConnectionState.DISABLED);
            logger.info("Intégration Redis désactivée.");
            return CompletableFuture.completedFuture(null);
        }
        updateState(ConnectionState.CONNECTING);
        return executorManager.runIo(() -> connect(settings));
    }

    @Override
    public CompletableFuture<Void> stop() {
        started.set(false);
        cancelReconnect();
        subscriptions.values().forEach(Subscription::shutdown);
        subscriptions.clear();
        closePool();
        reconnectScheduler.shutdownNow();
        updateState(ConnectionState.DISABLED);
        return CompletableFuture.completedFuture(null);
    }

    public void applySettings(CoreConfig.RedisSettings settings) {
        Objects.requireNonNull(settings, "settings");
        CoreConfig.RedisSettings previous = settingsRef.getAndSet(settings);
        if (!started.get()) {
            return;
        }
        if (!settings.enabled()) {
            logger.info("Redis désactivé via la configuration.");
            cancelReconnect();
            subscriptions.values().forEach(Subscription::shutdown);
            subscriptions.clear();
            closePool();
            lastError.set(null);
            reconnectAttempts.set(0);
            updateState(ConnectionState.DISABLED);
            return;
        }
        if (previous == null || !previous.equals(settings) || state.get() != ConnectionState.CONNECTED) {
            updateState(ConnectionState.CONNECTING);
            executorManager.runIo(() -> connect(settings));
        }
    }

    public void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings) {
        Objects.requireNonNull(settings, "settings");
        degradedSettings.set(settings);
    }

    public boolean isOperational() {
        return state.get() == ConnectionState.CONNECTED && poolRef.get() != null;
    }

    public boolean isEnabled() {
        CoreConfig.RedisSettings settings = settingsRef.get();
        return settings != null && settings.enabled();
    }

    public ConnectionState state() {
        return state.get();
    }

    @Override
    public Optional<Throwable> lastError() {
        return Optional.ofNullable(lastError.get());
    }

    public CompletableFuture<Long> publish(String channel, String message) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        if (!isOperational()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return executorManager.supplyIo(() -> {
            JedisPool pool = poolRef.get();
            if (pool == null) {
                throw new IllegalStateException("Redis connection pool unavailable");
            }
            try (Jedis jedis = pool.getResource()) {
                return jedis.publish(channel, message);
            } catch (Exception exception) {
                reportFailure(exception);
                throw exception;
            }
        });
    }

    public <T> CompletableFuture<T> execute(Function<Jedis, T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (!isOperational()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return executorManager.supplyIo(() -> {
            JedisPool pool = poolRef.get();
            if (pool == null) {
                throw new IllegalStateException("Redis connection pool unavailable");
            }
            try (Jedis jedis = pool.getResource()) {
                return operation.apply(jedis);
            } catch (Exception exception) {
                reportFailure(exception);
                throw exception;
            }
        });
    }

    public RedisSubscription subscribe(String channel, RedisMessageListener listener) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(listener, "listener");
        CoreConfig.RedisSettings settings = settingsRef.get();
        if (settings == null || !settings.enabled()) {
            logger.debug(() -> "Abonnement Redis ignoré (désactivé) pour " + channel);
            return new RedisSubscription(null);
        }
        long id = subscriptionIds.incrementAndGet();
        Subscription subscription = new Subscription(id, channel, listener);
        subscriptions.put(id, subscription);
        if (isOperational()) {
            subscription.ensureRunning();
        }
        return new RedisSubscription(subscription);
    }

    public RedisDiagnostics diagnostics() {
        int activeSubscriptions = (int) subscriptions.values().stream()
                .filter(subscription -> !subscription.isClosed())
                .count();
        JedisPool pool = poolRef.get();
        Optional<RedisDiagnostics.PoolMetrics> metrics = Optional.ofNullable(pool)
                .map(current -> new RedisDiagnostics.PoolMetrics(current.getNumActive(),
                        current.getNumIdle(), current.getNumWaiters()))
                .filter(stats -> state.get() == ConnectionState.CONNECTED);
        Optional<String> lastErrorMessage = Optional.ofNullable(lastError.get())
                .map(this::formatErrorMessage);
        return new RedisDiagnostics(state.get(), activeSubscriptions, metrics, lastErrorMessage);
    }

    private void connect(CoreConfig.RedisSettings settings) {
        if (!started.get()) {
            return;
        }
        if (settings == null || !settings.enabled()) {
            updateState(ConnectionState.DISABLED);
            return;
        }
        updateState(ConnectionState.CONNECTING);
        JedisPool newPool = null;
        try {
            newPool = createPool(settings);
            try (Jedis jedis = newPool.getResource()) {
                jedis.ping();
            }
            JedisPool previous = poolRef.getAndSet(newPool);
            if (previous != null) {
                previous.close();
            }
            reconnectAttempts.set(0);
            updateState(ConnectionState.CONNECTED);
            lastError.set(null);
            logger.info(() -> "Connexion Redis établie (%s:%d)".formatted(settings.host(), settings.port()));
            restartSubscriptions();
        } catch (Exception exception) {
            if (newPool != null) {
                newPool.close();
            }
            reportFailure(exception);
        }
    }

    private JedisPool createPool(CoreConfig.RedisSettings settings) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        int timeout = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, settings.timeoutMs()));
        String password = settings.password();
        if (password != null && password.isBlank()) {
            password = null;
        }
        return new JedisPool(poolConfig, settings.host(), settings.port(), timeout, password);
    }

    private void restartSubscriptions() {
        subscriptions.values().forEach(Subscription::ensureRunning);
    }

    private void reportFailure(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        Throwable previous = lastError.getAndSet(throwable);
        closePool();
        if (previous == null
                || !Objects.equals(previous.getClass(), throwable.getClass())
                || !Objects.equals(previous.getMessage(), throwable.getMessage())) {
            logger.warn("Connexion Redis indisponible : " + formatErrorMessage(throwable), throwable);
        } else {
            logger.debug(() -> "Redis toujours indisponible : " + formatErrorMessage(throwable));
        }
        if (!(throwable instanceof JedisConnectionException)) {
            logger.debug(() -> "Erreur Redis non liée à la connexion détectée : " + throwable.getClass().getSimpleName());
        }
        updateState(ConnectionState.DEGRADED);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!started.get()) {
            return;
        }
        CoreConfig.RedisSettings settings = settingsRef.get();
        if (settings == null || !settings.enabled()) {
            return;
        }
        ScheduledFuture<?> existing = reconnectFuture.get();
        if (existing != null && !existing.isDone()) {
            return;
        }
        int attempt = reconnectAttempts.incrementAndGet();
        long delaySeconds = computeBackoffSeconds(attempt);
        final AtomicReference<ScheduledFuture<?>> taskRef = new AtomicReference<>();
        Runnable reconnectTask = () -> {
            try {
                CoreConfig.RedisSettings current = settingsRef.get();
                if (current == null || !current.enabled() || !started.get()) {
                    return;
                }
                updateState(ConnectionState.CONNECTING);
                executorManager.runIo(() -> connect(current));
            } finally {
                reconnectFuture.compareAndSet(taskRef.get(), null);
            }
        };
        ScheduledFuture<?> task = null;
        task = reconnectScheduler.schedule(reconnectTask, delaySeconds, TimeUnit.SECONDS);
        taskRef.set(task);
        reconnectFuture.set(task);
    }

    private void cancelReconnect() {
        ScheduledFuture<?> future = reconnectFuture.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        reconnectAttempts.set(0);
    }

    private void closePool() {
        JedisPool pool = poolRef.getAndSet(null);
        if (pool != null) {
            pool.close();
        }
    }

    private void removeSubscription(Subscription subscription) {
        if (subscription == null) {
            return;
        }
        subscriptions.remove(subscription.id());
        subscription.shutdown();
    }

    private String formatErrorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    public enum ConnectionState {
        DISABLED,
        CONNECTING,
        CONNECTED,
        DEGRADED
    }

    private long computeBackoffSeconds(int attempt) {
        if (attempt <= 0) {
            return INITIAL_RECONNECT_DELAY_SECONDS;
        }
        long delay = INITIAL_RECONNECT_DELAY_SECONDS;
        if (attempt > 1) {
            long multiplier = 1L << Math.min(attempt - 1, 30);
            delay = Math.min(MAX_RECONNECT_DELAY_SECONDS, INITIAL_RECONNECT_DELAY_SECONDS * multiplier);
        }
        return Math.min(MAX_RECONNECT_DELAY_SECONDS, delay);
    }

    private void updateState(ConnectionState newState) {
        ConnectionState previous = state.getAndSet(newState);
        if (previous != newState) {
            handleStateTransition(previous, newState);
        }
    }

    private void handleStateTransition(ConnectionState previous, ConnectionState current) {
        if (current == ConnectionState.DEGRADED) {
            if (degradedNotified.compareAndSet(false, true)) {
                logger.warn("Redis indisponible — mode dégradé activé.");
                notifyAdministrators("alerts.redis.degraded");
            } else {
                logger.debug("Redis toujours en mode dégradé.");
            }
        } else if (current == ConnectionState.CONNECTED) {
            if (previous == ConnectionState.DEGRADED) {
                logger.info("Redis reconnecté — mode dégradé désactivé.");
                notifyAdministrators("alerts.redis.recovered");
            }
            degradedNotified.set(false);
        } else if (current == ConnectionState.DISABLED) {
            degradedNotified.set(false);
        }
    }

    private void notifyAdministrators(String messageKey) {
        CoreConfig.DegradedModeSettings settings = degradedSettings.get();
        if (settings == null || !settings.enabled() || !settings.banner()) {
            return;
        }
        Component component = messageFacade.render(messageKey);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.hasPermission("nexus.admin.notify")) {
                    player.sendActionBar(component);
                }
            }
        });
    }

    public record RedisDiagnostics(ConnectionState state,
                                   int activeSubscriptions,
                                   Optional<PoolMetrics> poolMetrics,
                                   Optional<String> lastError) {

        public record PoolMetrics(int active, int idle, int waiters) {
        }
    }

    public static final class RedisSubscription implements AutoCloseable {

        private final Subscription delegate;

        private RedisSubscription(Subscription delegate) {
            this.delegate = delegate;
        }

        public boolean active() {
            return delegate != null && !delegate.isClosed();
        }

        @Override
        public void close() {
            if (delegate != null) {
                delegate.service.removeSubscription(delegate);
            }
        }
    }

    private final class Subscription {

        private final long id;
        private final String channel;
        private final RedisMessageListener listener;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<Void>> taskRef = new AtomicReference<>();
        private volatile JedisPubSub pubSub;
        private final RedisService service = RedisService.this;

        Subscription(long id, String channel, RedisMessageListener listener) {
            this.id = id;
            this.channel = channel;
            this.listener = listener;
        }

        long id() {
            return id;
        }

        boolean isClosed() {
            return closed.get();
        }

        void ensureRunning() {
            if (closed.get()) {
                return;
            }
            CompletableFuture<Void> current = taskRef.get();
            if (current != null && !current.isDone()) {
                return;
            }
            CompletableFuture<Void> task = executorManager.runIo(this::runLoop)
                    .whenComplete((ignored, throwable) -> taskRef.compareAndSet(task, null));
            taskRef.set(task);
        }

        private void runLoop() {
            while (!closed.get() && started.get()) {
                CoreConfig.RedisSettings settings = settingsRef.get();
                if (settings == null || !settings.enabled()) {
                    sleepQuietly(SUBSCRIPTION_RETRY_DELAY_MS);
                    continue;
                }
                JedisPool pool = poolRef.get();
                if (pool == null) {
                    sleepQuietly(SUBSCRIPTION_RETRY_DELAY_MS);
                    continue;
                }
                try (Jedis jedis = pool.getResource()) {
                    JedisPubSub subscriber = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (!channelSecurityManager.isChannelAllowed(channel)) {
                                logger.warn(String.format(
                                        "Message Redis ignoré sur un canal non autorisé: %s (subscription=%d)",
                                        channel, id));
                                return;
                            }
                            try {
                                listener.onMessage(channel, message);
                            } catch (Throwable throwable) {
                                logger.error("Erreur lors du traitement d'un message Redis", throwable);
                            }
                        }
                    };
                    pubSub = subscriber;
                    jedis.subscribe(subscriber, channel);
                } catch (Exception exception) {
                    if (closed.get()) {
                        break;
                    }
                    reportFailure(exception);
                    sleepQuietly(SUBSCRIPTION_RETRY_DELAY_MS);
                } finally {
                    pubSub = null;
                }
            }
        }

        void shutdown() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            JedisPubSub subscriber = pubSub;
            if (subscriber != null) {
                try {
                    subscriber.unsubscribe();
                } catch (Exception ignored) {
                    // no-op
                }
            }
            CompletableFuture<Void> current = taskRef.getAndSet(null);
            if (current != null) {
                current.cancel(true);
            }
        }

        private void sleepQuietly(long delayMs) {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
