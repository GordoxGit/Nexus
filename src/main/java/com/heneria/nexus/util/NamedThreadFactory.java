package com.heneria.nexus.util;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory producing clearly named threads for profiling purposes.
 */
public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean daemon;
    private final AtomicInteger counter = new AtomicInteger(1);
    private final NexusLogger logger;

    public NamedThreadFactory(String prefix, boolean daemon, NexusLogger logger) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.daemon = daemon;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String name = prefix + "-" + counter.getAndIncrement();
        Thread thread = Thread.ofPlatform().name(name).daemon(daemon).unstarted(runnable);
        thread.setUncaughtExceptionHandler((t, throwable) ->
                logger.error("Thread " + t.getName() + " terminated unexpectedly", throwable));
        return thread;
    }
}
