package com.heneria.nexus.service.core;

import com.heneria.nexus.api.EconomyException;
import com.heneria.nexus.api.EconomyService;
import com.heneria.nexus.api.EconomyTransaction;
import com.heneria.nexus.api.EconomyTransferResult;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.db.repository.EconomyRepository;
import com.heneria.nexus.util.NexusLogger;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Default implementation using a MariaDB repository with in-memory fallback.
 */
public final class EconomyServiceImpl implements EconomyService {

    private final NexusLogger logger;
    private final DbProvider dbProvider;
    private final ExecutorManager executorManager;
    private final EconomyRepository economyRepository;
    private final ConcurrentHashMap<UUID, BalanceEntry> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicBoolean forcedFallback = new AtomicBoolean();
    private final AtomicReference<CoreConfig.DegradedModeSettings> degradedSettings = new AtomicReference<>();

    public EconomyServiceImpl(NexusLogger logger,
                              DbProvider dbProvider,
                              ExecutorManager executorManager,
                              CoreConfig config,
                              EconomyRepository economyRepository) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.economyRepository = Objects.requireNonNull(economyRepository, "economyRepository");
        this.degradedSettings.set(config.degradedModeSettings());
    }

    @Override
    public CompletableFuture<Void> start() {
        return executorManager.runIo(() -> refreshDegradedState());
    }

    @Override
    public CompletionStage<Long> getBalance(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        boolean providerDegraded = dbProvider.isDegraded();
        refreshDegradedState(providerDegraded);
        if (providerDegraded) {
            return executorManager.supplyIo(() -> getBalanceFromFallback(accountId));
        }
        if (forcedFallback.get()) {
            triggerHealthProbe();
            return executorManager.supplyIo(() -> getBalanceFromFallback(accountId));
        }
        return economyRepository.getBalance(accountId)
                .thenApply(balance -> {
                    clearForcedFallback();
                    updateFallbackBalance(accountId, balance);
                    return balance;
                })
                .exceptionallyCompose(throwable -> fallbackBalance(accountId, throwable));
    }

    @Override
    public CompletionStage<Long> credit(UUID accountId, long amount, String reason) {
        Objects.requireNonNull(accountId, "accountId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        boolean providerDegraded = dbProvider.isDegraded();
        refreshDegradedState(providerDegraded);
        if (providerDegraded) {
            return executorManager.supplyIo(() -> applyDeltaInFallback(accountId, amount));
        }
        if (forcedFallback.get()) {
            triggerHealthProbe();
            return executorManager.supplyIo(() -> applyDeltaInFallback(accountId, amount));
        }
        return economyRepository.addToBalance(accountId, amount)
                .thenApply(balance -> {
                    clearForcedFallback();
                    updateFallbackBalance(accountId, balance);
                    logger.debug(() -> "Crédit " + amount + " pour " + accountId + " (" + reason + ")");
                    return balance;
                })
                .exceptionallyCompose(throwable -> fallbackCredit(accountId, amount, throwable));
    }

    @Override
    public CompletionStage<Long> debit(UUID accountId, long amount, String reason) {
        Objects.requireNonNull(accountId, "accountId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        boolean providerDegraded = dbProvider.isDegraded();
        refreshDegradedState(providerDegraded);
        if (providerDegraded) {
            return executorManager.supplyIo(() -> applyDeltaInFallback(accountId, -amount))
                    .exceptionallyCompose(this::propagateEconomyFailure);
        }
        if (forcedFallback.get()) {
            triggerHealthProbe();
            return executorManager.supplyIo(() -> applyDeltaInFallback(accountId, -amount))
                    .exceptionallyCompose(this::propagateEconomyFailure);
        }
        return economyRepository.addToBalance(accountId, -amount)
                .thenApply(balance -> {
                    clearForcedFallback();
                    updateFallbackBalance(accountId, balance);
                    logger.debug(() -> "Débit " + amount + " pour " + accountId + " (" + reason + ")");
                    return balance;
                })
                .exceptionallyCompose(throwable -> fallbackDebit(accountId, amount, throwable));
    }

    @Override
    public CompletionStage<EconomyTransferResult> transfer(UUID from, UUID to, long amount, String reason) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        boolean providerDegraded = dbProvider.isDegraded();
        refreshDegradedState(providerDegraded);
        if (providerDegraded) {
            return executorManager.supplyIo(() -> performFallbackTransfer(from, to, amount))
                    .exceptionallyCompose(this::propagateEconomyFailure);
        }
        if (forcedFallback.get()) {
            triggerHealthProbe();
            return executorManager.supplyIo(() -> performFallbackTransfer(from, to, amount))
                    .exceptionallyCompose(this::propagateEconomyFailure);
        }
        return economyRepository.transfer(from, to, amount)
                .thenApply(result -> {
                    clearForcedFallback();
                    updateFallbackBalance(from, result.fromBalance());
                    updateFallbackBalance(to, result.toBalance());
                    logger.debug(() -> "Transfert " + amount + " de " + from + " vers " + to + " (" + reason + ")");
                    return result;
                })
                .exceptionallyCompose(throwable -> fallbackTransfer(from, to, amount, throwable));
    }

    @Override
    public EconomyTransaction beginTransaction() {
        return new RepositoryAwareTransaction();
    }

    @Override
    public void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings) {
        degradedSettings.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public boolean isDegraded() {
        return degraded.get();
    }

    private boolean refreshDegradedState() {
        return refreshDegradedState(dbProvider.isDegraded());
    }

    private boolean refreshDegradedState(boolean providerDegraded) {
        boolean fallback = providerDegraded || forcedFallback.get();
        boolean previous = degraded.getAndSet(fallback);
        if (fallback && !previous) {
            CoreConfig.DegradedModeSettings settings = degradedSettings.get();
            if (settings.banner()) {
                logger.warn("Mode dégradé activé pour l'EconomyService : stockage en mémoire");
            }
        }
        if (!fallback && previous) {
            logger.info("EconomyService repassé en mode persistant");
        }
        return fallback;
    }

    private long getBalanceFromFallback(UUID accountId) {
        BalanceEntry entry = balances.get(accountId);
        return entry != null ? entry.balance() : 0L;
    }

    private long applyDeltaInFallback(UUID accountId, long delta) {
        synchronized (balances) {
            BalanceEntry entry = balances.getOrDefault(accountId, new BalanceEntry(0L, Instant.now()));
            long next = entry.balance() + delta;
            if (next < 0L) {
                throw new IllegalStateException("Solde insuffisant");
            }
            BalanceEntry updated = new BalanceEntry(next, Instant.now());
            balances.put(accountId, updated);
            return updated.balance();
        }
    }

    private EconomyTransferResult performFallbackTransfer(UUID from, UUID to, long amount) {
        synchronized (balances) {
            long fromBalance = balances.getOrDefault(from, new BalanceEntry(0L, Instant.now())).balance();
            long toBalance = balances.getOrDefault(to, new BalanceEntry(0L, Instant.now())).balance();
            if (fromBalance < amount) {
                throw new IllegalStateException("Solde insuffisant");
            }
            fromBalance -= amount;
            toBalance += amount;
            balances.put(from, new BalanceEntry(fromBalance, Instant.now()));
            balances.put(to, new BalanceEntry(toBalance, Instant.now()));
            return new EconomyTransferResult(fromBalance, toBalance);
        }
    }

    private void updateFallbackBalance(UUID accountId, long balance) {
        balances.put(accountId, new BalanceEntry(balance, Instant.now()));
    }

    private CompletableFuture<Long> fallbackBalance(UUID accountId, Throwable throwable) {
        return handleRepositoryFailure("lecture du solde", throwable,
                () -> executorManager.supplyIo(() -> getBalanceFromFallback(accountId)));
    }

    private CompletableFuture<Long> fallbackCredit(UUID accountId, long amount, Throwable throwable) {
        return handleRepositoryFailure("crédit", throwable,
                () -> executorManager.supplyIo(() -> applyDeltaInFallback(accountId, amount)));
    }

    private CompletableFuture<Long> fallbackDebit(UUID accountId, long amount, Throwable throwable) {
        return handleRepositoryFailure("débit", throwable,
                () -> executorManager.supplyIo(() -> applyDeltaInFallback(accountId, -amount)));
    }

    private CompletableFuture<EconomyTransferResult> fallbackTransfer(UUID from, UUID to, long amount, Throwable throwable) {
        return handleRepositoryFailure("transfert", throwable,
                () -> executorManager.supplyIo(() -> performFallbackTransfer(from, to, amount)));
    }

    private CompletableFuture<Void> fallbackTransaction(Map<UUID, Long> deltas, Throwable throwable) {
        return handleRepositoryFailure("transaction", throwable,
                () -> executorManager.runIo(() -> applyDeltasFallback(deltas)));
    }

    private void applyDeltasFallback(Map<UUID, Long> deltas) {
        synchronized (balances) {
            for (Map.Entry<UUID, Long> entry : deltas.entrySet()) {
                long delta = entry.getValue();
                if (delta == 0L) {
                    continue;
                }
                BalanceEntry current = balances.getOrDefault(entry.getKey(), new BalanceEntry(0L, Instant.now()));
                long next = current.balance() + delta;
                if (next < 0L) {
                    throw new IllegalStateException("Solde insuffisant pour " + entry.getKey());
                }
                balances.put(entry.getKey(), new BalanceEntry(next, Instant.now()));
            }
        }
    }

    private void activateFallback(String action, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (forcedFallback.compareAndSet(false, true)) {
            logger.warn("Échec lors de la %s, bascule en mode mémoire".formatted(action), cause);
        } else {
            logger.debug(() -> "Échec lors de la %s : %s".formatted(action, cause != null ? cause.getMessage() : "inconnu"));
        }
        refreshDegradedState();
        triggerHealthProbe();
    }

    private void clearForcedFallback() {
        if (forcedFallback.compareAndSet(true, false)) {
            refreshDegradedState();
        }
    }

    private <T> CompletableFuture<T> propagateEconomyFailure(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof IllegalStateException) {
            return CompletableFuture.failedFuture(new EconomyException(cause.getMessage()));
        }
        return CompletableFuture.failedFuture(cause);
    }

    private <T> CompletableFuture<T> handleRepositoryFailure(String action,
                                                             Throwable throwable,
                                                             Supplier<CompletableFuture<T>> fallbackSupplier) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof IllegalStateException) {
            return CompletableFuture.failedFuture(new EconomyException(cause.getMessage()));
        }
        activateFallback(action, cause);
        return fallbackSupplier.get().exceptionallyCompose(this::propagateEconomyFailure);
    }

    private void triggerHealthProbe() {
        if (!forcedFallback.get()) {
            return;
        }
        dbProvider.checkHealth(executorManager.io())
                .thenAccept(healthy -> {
                    if (healthy && forcedFallback.compareAndSet(true, false)) {
                        refreshDegradedState();
                    }
                });
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completion && completion.getCause() != null) {
            return unwrap(completion.getCause());
        }
        if (throwable instanceof ExecutionException execution && execution.getCause() != null) {
            return unwrap(execution.getCause());
        }
        return throwable;
    }

    private record BalanceEntry(long balance, Instant updatedAt) {
    }

    private final class RepositoryAwareTransaction implements EconomyTransaction {

        private final Map<UUID, Long> deltas = new ConcurrentHashMap<>();
        private volatile boolean closed;

        @Override
        public void credit(UUID account, long amount, String reason) {
            Objects.requireNonNull(account, "account");
            if (amount < 0L) {
                throw new IllegalArgumentException("Montant négatif");
            }
            deltas.merge(account, amount, Long::sum);
        }

        @Override
        public void debit(UUID account, long amount, String reason) throws EconomyException {
            Objects.requireNonNull(account, "account");
            if (amount < 0L) {
                throw new EconomyException("Montant négatif");
            }
            deltas.merge(account, -amount, Long::sum);
        }

        @Override
        public CompletionStage<Void> commit() {
            if (closed) {
                return CompletableFuture.completedFuture(null);
            }
            closed = true;
            if (deltas.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            boolean providerDegraded = dbProvider.isDegraded();
            refreshDegradedState(providerDegraded);
            if (providerDegraded) {
                return executorManager.runIo(() -> applyDeltasFallback(deltas))
                        .exceptionallyCompose(EconomyServiceImpl.this::propagateEconomyFailure);
            }
            if (forcedFallback.get()) {
                triggerHealthProbe();
                return executorManager.runIo(() -> applyDeltasFallback(deltas))
                        .exceptionallyCompose(EconomyServiceImpl.this::propagateEconomyFailure);
            }
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (Map.Entry<UUID, Long> entry : deltas.entrySet()) {
                long delta = entry.getValue();
                if (delta == 0L) {
                    continue;
                }
                chain = chain.thenCompose(ignored -> economyRepository.addToBalance(entry.getKey(), delta)
                        .thenAccept(balance -> updateFallbackBalance(entry.getKey(), balance)));
            }
            CompletableFuture<Void> persistent = chain.thenRun(EconomyServiceImpl.this::clearForcedFallback);
            return persistent.exceptionallyCompose(throwable -> fallbackTransaction(deltas, throwable));
        }

        @Override
        public void rollback() {
            closed = true;
            deltas.clear();
        }
    }
}
