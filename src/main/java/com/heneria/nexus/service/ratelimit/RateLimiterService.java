package com.heneria.nexus.service.ratelimit;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.ratelimit.RateLimitResult;
import com.heneria.nexus.service.LifecycleAware;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Exposes rate limiter checks backed by MariaDB.
 */
public interface RateLimiterService extends LifecycleAware {

    CompletableFuture<RateLimitResult> check(UUID playerUuid, String actionKey, Duration cooldown);

    void applyConfiguration(CoreConfig coreConfig);
}
