package com.heneria.nexus.service.core;

import com.heneria.nexus.api.service.TimerService;
import com.heneria.nexus.scheduler.GamePhase;
import com.heneria.nexus.scheduler.RingScheduler;
import java.util.EnumSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link TimerService} backed by the {@link RingScheduler}.
 */
public final class TimerServiceImpl implements TimerService {

    private static final long DEFAULT_INTERVAL_TICKS = 5L;
    private static final String TASK_ID = "timer-service";

    private final RingScheduler ringScheduler;
    private final ConcurrentMap<UUID, ConcurrentMap<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final AtomicBoolean tickerRegistered = new AtomicBoolean();

    public TimerServiceImpl(RingScheduler ringScheduler) {
        this.ringScheduler = Objects.requireNonNull(ringScheduler, "ringScheduler");
    }

    @Override
    public CompletableFuture<Void> start() {
        if (tickerRegistered.compareAndSet(false, true)) {
            ringScheduler.registerTask(TASK_ID, DEFAULT_INTERVAL_TICKS,
                    EnumSet.allOf(GamePhase.class), this::purgeExpiredCooldowns);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (tickerRegistered.compareAndSet(true, false)) {
            ringScheduler.unregisterTask(TASK_ID);
        }
        cooldowns.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void setCooldown(UUID owner, String key, long durationMillis) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        if (durationMillis <= 0L) {
            clearCooldown(owner, key);
            return;
        }
        long now = System.currentTimeMillis();
        long expiry = safeAdd(now, durationMillis);
        cooldowns.compute(owner, (uuid, current) -> {
            ConcurrentMap<String, Long> map = current;
            if (map == null) {
                map = new ConcurrentHashMap<>();
            }
            map.put(key, expiry);
            return map;
        });
    }

    @Override
    public boolean isOnCooldown(UUID owner, String key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        ConcurrentMap<String, Long> map = cooldowns.get(owner);
        if (map == null) {
            return false;
        }
        Long expiry = map.get(key);
        if (expiry == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (expiry > now) {
            return true;
        }
        removeEntry(owner, map, key, expiry);
        return false;
    }

    @Override
    public OptionalLong getRemainingMillis(UUID owner, String key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        ConcurrentMap<String, Long> map = cooldowns.get(owner);
        if (map == null) {
            return OptionalLong.empty();
        }
        Long expiry = map.get(key);
        if (expiry == null) {
            return OptionalLong.empty();
        }
        long now = System.currentTimeMillis();
        if (expiry <= now) {
            removeEntry(owner, map, key, expiry);
            return OptionalLong.empty();
        }
        return OptionalLong.of(expiry - now);
    }

    @Override
    public void clearCooldown(UUID owner, String key) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        ConcurrentMap<String, Long> map = cooldowns.get(owner);
        if (map == null) {
            return;
        }
        map.remove(key);
        cleanupOwner(owner, map);
    }

    private void purgeExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.forEach((owner, map) -> {
            map.entrySet().removeIf(entry -> entry.getValue() <= now);
            cleanupOwner(owner, map);
        });
    }

    private void removeEntry(UUID owner, ConcurrentMap<String, Long> map, String key, long expectedExpiry) {
        map.remove(key, expectedExpiry);
        cleanupOwner(owner, map);
    }

    private void cleanupOwner(UUID owner, ConcurrentMap<String, Long> map) {
        if (map.isEmpty()) {
            cooldowns.remove(owner, map);
        }
    }

    private long safeAdd(long base, long delta) {
        if (delta > 0 && base > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        if (delta < 0 && base < Long.MIN_VALUE - delta) {
            return Long.MIN_VALUE;
        }
        return base + delta;
    }
}
