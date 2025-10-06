package com.heneria.nexus.security;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.redis.RedisService;
import com.heneria.nexus.util.NexusLogger;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Distributed rate limiter protecting cross-server communication channels.
 */
public final class NetworkRateLimiter {

    private static final String KEY_PREFIX = "nexus:ratelimit:network";
    private static final long WINDOW_EXPIRY_SECONDS = 5L;
    private static final long REDIS_WARNING_INTERVAL_MS = 30_000L;
    private static final long LOG_THROTTLE_INTERVAL_MS = 1_000L;
    private static final String FALLBACK_SOURCE = "unknown";
    private static final String INCREMENT_SCRIPT =
            "local current = redis.call('INCR', KEYS[1]);" +
                    " if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end" +
                    " return current;";

    private final NexusLogger logger;
    private final RedisService redisService;
    private final AtomicReference<Settings> settingsRef;
    private final ConcurrentMap<String, Long> throttledLogs = new ConcurrentHashMap<>();
    private final AtomicLong lastRedisWarning = new AtomicLong();
    private final AtomicReference<String> scriptSha = new AtomicReference<>();

    public NetworkRateLimiter(NexusLogger logger, RedisService redisService, CoreConfig coreConfig) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.redisService = Objects.requireNonNull(redisService, "redisService");
        Objects.requireNonNull(coreConfig, "coreConfig");
        this.settingsRef = new AtomicReference<>(toSettings(coreConfig.securitySettings()));
    }

    public void applySettings(CoreConfig.SecuritySettings securitySettings) {
        Objects.requireNonNull(securitySettings, "securitySettings");
        Settings settings = toSettings(securitySettings);
        settingsRef.set(settings);
        if (!settings.enabled) {
            throttledLogs.clear();
        }
    }

    public CompletableFuture<Boolean> isAllowed(String sourceServerId, String channel) {
        if (channel == null || channel.isBlank()) {
            return CompletableFuture.completedFuture(true);
        }
        Settings settings = settingsRef.get();
        if (!settings.enabled || settings.limits.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        String normalizedChannel = normalize(channel);
        Integer limit = settings.limits.get(normalizedChannel);
        if (limit == null || limit <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        String source = normalizeSource(sourceServerId);
        if (!redisService.isOperational()) {
            maybeLogRedisWarning(settings.failOpen);
            return CompletableFuture.completedFuture(settings.failOpen);
        }
        long timestamp = Instant.now().getEpochSecond();
        String key = buildKey(normalizedChannel, source, timestamp);
        return redisService.execute(jedis -> incrementWindow(jedis, key))
                .handle((count, throwable) -> handleResult(settings, normalizedChannel, source, limit, count, throwable));
    }

    private Boolean handleResult(Settings settings,
                                 String channel,
                                 String source,
                                 int limit,
                                 Long count,
                                 Throwable throwable) {
        if (throwable != null || count == null) {
            maybeLogRedisWarning(settings.failOpen);
            return settings.failOpen;
        }
        if (count <= limit) {
            return true;
        }
        throttleLog(channel, source, limit, count);
        return false;
    }

    private long incrementWindow(Jedis jedis, String key) {
        String sha = scriptSha.get();
        if (sha == null) {
            sha = jedis.scriptLoad(INCREMENT_SCRIPT);
            scriptSha.set(sha);
        }
        try {
            Object result = jedis.evalsha(sha, Collections.singletonList(key),
                    Collections.singletonList(String.valueOf(WINDOW_EXPIRY_SECONDS)));
            if (result instanceof Long value) {
                return value;
            }
            if (result instanceof Number number) {
                return number.longValue();
            }
            return 0L;
        } catch (JedisDataException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("NOSCRIPT")) {
                scriptSha.compareAndSet(sha, null);
                return incrementWindow(jedis, key);
            }
            throw exception;
        }
    }

    private void throttleLog(String channel, String source, int limit, long count) {
        String key = channel + '|' + source;
        long now = System.currentTimeMillis();
        throttledLogs.compute(key, (k, last) -> {
            if (last == null || now - last >= LOG_THROTTLE_INTERVAL_MS) {
                logger.warn(String.format(
                        "Limite réseau dépassée sur %s par %s (%d messages dans la fenêtre, limite=%d)",
                        channel, source, count, limit));
                return now;
            }
            return last;
        });
    }

    private void maybeLogRedisWarning(boolean failOpen) {
        long now = System.currentTimeMillis();
        long last = lastRedisWarning.get();
        if (now - last < REDIS_WARNING_INTERVAL_MS) {
            return;
        }
        if (lastRedisWarning.compareAndSet(last, now)) {
            if (failOpen) {
                logger.warn("Redis indisponible pour le NetworkRateLimiter — fail-open activé");
            } else {
                logger.warn("Redis indisponible pour le NetworkRateLimiter — tout le trafic est bloqué");
            }
        }
    }

    private Settings toSettings(CoreConfig.SecuritySettings securitySettings) {
        CoreConfig.SecuritySettings.NetworkRateLimitSettings config =
                securitySettings.networkRateLimitSettings();
        Map<String, Integer> normalized = new LinkedHashMap<>();
        config.limits().forEach((channel, limit) -> {
            if (channel == null) {
                return;
            }
            String normalizedChannel = normalize(channel);
            if (normalizedChannel.isEmpty()) {
                return;
            }
            if (limit == null || limit <= 0) {
                return;
            }
            normalized.put(normalizedChannel, limit);
        });
        return new Settings(config.enabled(), config.failOpen(),
                Collections.unmodifiableMap(new LinkedHashMap<>(normalized)));
    }

    private String buildKey(String channel, String source, long timestamp) {
        return KEY_PREFIX + ':' + channel + ':' + source + ':' + timestamp;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeSource(String sourceServerId) {
        String normalized = normalize(sourceServerId);
        if (normalized.isEmpty()) {
            return FALLBACK_SOURCE;
        }
        return normalized;
    }

    private record Settings(boolean enabled, boolean failOpen, Map<String, Integer> limits) {
    }
}
