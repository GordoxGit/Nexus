package com.heneria.nexus.watchdog;

import com.heneria.nexus.service.LifecycleAware;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Local watchdog protecting the server from long running asynchronous tasks.
 */
public interface WatchdogService extends LifecycleAware {

    <T> CompletableFuture<T> monitor(String taskName, Duration timeout, Supplier<CompletableFuture<T>> taskSupplier);

    void registerFallback(String taskName, Consumer<Throwable> fallbackAction);

    WatchdogStatistics statistics();

    List<WatchdogReport> recentReports();

    record WatchdogStatistics(long monitoredTasks, long timedOutTasks, double averageDurationMillis) {
    }
}
