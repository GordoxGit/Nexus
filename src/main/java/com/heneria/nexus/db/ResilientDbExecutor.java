package com.heneria.nexus.db;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.util.NexusLogger;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Wraps {@link DbProvider} calls with retry and circuit breaker policies.
 */
public final class ResilientDbExecutor {

    private static final String RETRY_NAME = "nexus-db-retry";
    private static final String CIRCUIT_BREAKER_NAME = "nexus-db-circuit-breaker";

    private final NexusLogger logger;
    private final DbProvider dbProvider;
    private final Executor ioExecutor;
    private final AtomicReference<Retry> retryRef = new AtomicReference<>();
    private final AtomicReference<CircuitBreaker> circuitBreakerRef = new AtomicReference<>();

    public ResilientDbExecutor(NexusLogger logger,
                               DbProvider dbProvider,
                               Executor ioExecutor,
                               CoreConfig.DatabaseSettings databaseSettings) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        configure(databaseSettings);
    }

    public synchronized void configure(CoreConfig.DatabaseSettings databaseSettings) {
        Objects.requireNonNull(databaseSettings, "databaseSettings");
        CoreConfig.DatabaseSettings.ResilienceSettings resilience = databaseSettings.resilience();
        if (resilience == null) {
            throw new IllegalArgumentException("Resilience settings must not be null");
        }

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(resilience.retry().maxAttempts())
                .intervalFunction(createIntervalFunction(resilience.retry()))
                .retryOnException(this::shouldRetry)
                .build();
        Retry retry = Retry.of(RETRY_NAME, retryConfig);

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold((float) resilience.circuitBreaker().failureRateThreshold())
                .minimumNumberOfCalls(resilience.circuitBreaker().minimumNumberOfCalls())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(Math.toIntExact(Math.max(1L,
                        resilience.circuitBreaker().slidingWindowDuration().getSeconds())))
                .waitDurationInOpenState(resilience.circuitBreaker().waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(resilience.circuitBreaker().permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(this::shouldRecordFailure)
                .ignoreException(throwable -> unwrap(throwable) instanceof OptimisticLockException)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of(CIRCUIT_BREAKER_NAME, circuitBreakerConfig);
        circuitBreaker.getEventPublisher().onStateTransition(this::handleStateTransition);

        retryRef.set(retry);
        circuitBreakerRef.set(circuitBreaker);
    }

    public <T> CompletableFuture<T> execute(DbProvider.QueryTask<T> task) {
        Objects.requireNonNull(task, "task");
        Supplier<CompletionStage<T>> supplier = () -> dbProvider.execute(task, ioExecutor);
        Supplier<CompletionStage<T>> decorated = Decorators.ofCompletionStage(supplier)
                .withCircuitBreaker(circuitBreakerRef.get())
                .withRetry(retryRef.get())
                .decorate();
        try {
            return decorated.get().toCompletableFuture();
        } catch (Throwable throwable) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }
    }

    private IntervalFunction createIntervalFunction(CoreConfig.DatabaseSettings.ResilienceSettings.RetrySettings retrySettings) {
        long initial = retrySettings.initialInterval().toMillis();
        double multiplier = retrySettings.multiplier();
        long max = retrySettings.maxInterval().toMillis();
        return attempt -> {
            double candidate = initial * Math.pow(multiplier, Math.max(0, attempt - 1));
            long value = (long) Math.min(candidate, max);
            return value < 0L ? max : value;
        };
    }

    private boolean shouldRetry(Throwable throwable) {
        Throwable unwrapped = unwrap(throwable);
        if (unwrapped instanceof OptimisticLockException) {
            return false;
        }
        if (unwrapped instanceof IllegalStateException) {
            return false;
        }
        if (unwrapped instanceof SQLException sqlException) {
            return isTransient(sqlException);
        }
        return false;
    }

    private boolean shouldRecordFailure(Throwable throwable) {
        Throwable unwrapped = unwrap(throwable);
        if (unwrapped instanceof OptimisticLockException) {
            return false;
        }
        if (unwrapped instanceof SQLException sqlException) {
            return isTransient(sqlException);
        }
        return false;
    }

    private boolean isTransient(SQLException exception) {
        if (exception instanceof SQLTransientException
                || exception instanceof SQLRecoverableException
                || exception instanceof SQLNonTransientConnectionException
                || exception instanceof SQLTimeoutException) {
            return true;
        }
        String sqlState = exception.getSQLState();
        return sqlState != null && sqlState.startsWith("08");
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            Throwable cause = current.getCause();
            if (cause == null) {
                break;
            }
            current = cause;
        }
        if (current instanceof RuntimeException runtimeException && runtimeException.getCause() != null) {
            Throwable cause = runtimeException.getCause();
            if (cause instanceof SQLException) {
                return cause;
            }
        }
        return current;
    }

    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.State toState = event.getStateTransition().getToState();
        switch (toState) {
            case OPEN -> {
                boolean alreadyDegraded = dbProvider.isDegraded();
                dbProvider.setDegraded(true);
                if (alreadyDegraded) {
                    logger.warn("La connexion à la base de données reste instable, le circuit est OUVERT.");
                } else {
                    logger.warn("La connexion à la base de données est instable, le circuit est maintenant OUVERT. Passage en mode dégradé.");
                }
            }
            case HALF_OPEN -> logger.info("Circuit MariaDB en état SEMI-OUVERT : test de la connectivité.");
            case CLOSED -> {
                if (dbProvider.isDegraded()) {
                    dbProvider.setDegraded(false);
                    logger.info("Circuit MariaDB refermé, retour au mode normal.");
                } else {
                    logger.info("Circuit MariaDB refermé.");
                }
            }
            default -> {
                // No-op
            }
        }
    }
}
