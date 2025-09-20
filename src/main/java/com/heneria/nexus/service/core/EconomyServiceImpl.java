package com.heneria.nexus.service.core;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.db.DbProvider;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.service.api.EconomyException;
import com.heneria.nexus.service.api.EconomyService;
import com.heneria.nexus.service.api.EconomyTransaction;
import com.heneria.nexus.service.api.EconomyTransferResult;
import com.heneria.nexus.util.NexusLogger;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation storing balances in memory with write-behind hooks.
 */
public final class EconomyServiceImpl implements EconomyService {

    private final NexusLogger logger;
    private final DbProvider dbProvider;
    private final ExecutorManager executorManager;
    private final ConcurrentHashMap<UUID, BalanceEntry> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicReference<CoreConfig.DegradedModeSettings> degradedSettings = new AtomicReference<>();

    public EconomyServiceImpl(NexusLogger logger, DbProvider dbProvider, ExecutorManager executorManager, CoreConfig config) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dbProvider = Objects.requireNonNull(dbProvider, "dbProvider");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.degradedSettings.set(config.degradedModeSettings());
    }

    @Override
    public CompletableFuture<Void> start() {
        return executorManager.runIo(() -> refreshDegradedState());
    }

    @Override
    public CompletionStage<Long> getBalance(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        return executorManager.supplyIo(() -> {
            refreshDegradedState();
            BalanceEntry entry = balances.get(accountId);
            return entry != null ? entry.balance() : 0L;
        });
    }

    @Override
    public CompletionStage<Long> credit(UUID accountId, long amount, String reason) {
        Objects.requireNonNull(accountId, "accountId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        return executorManager.supplyIo(() -> {
            boolean fallback = refreshDegradedState();
            BalanceEntry updated = balances.compute(accountId, (id, entry) -> {
                long current = entry != null ? entry.balance() : 0L;
                return new BalanceEntry(current + amount, Instant.now());
            });
            if (!fallback) {
                logger.debug(() -> "Écriture async crédit " + amount + " pour " + accountId + " (" + reason + ")");
            }
            return updated.balance();
        });
    }

    @Override
    public CompletionStage<Long> debit(UUID accountId, long amount, String reason) {
        Objects.requireNonNull(accountId, "accountId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        return executorManager.supplyIo(() -> {
            boolean fallback = refreshDegradedState();
            BalanceEntry updated = balances.compute(accountId, (id, entry) -> {
                long current = entry != null ? entry.balance() : 0L;
                long next = current - amount;
                if (next < 0L) {
                    throw new IllegalStateException("Solde insuffisant");
                }
                return new BalanceEntry(next, Instant.now());
            });
            if (!fallback) {
                logger.debug(() -> "Écriture async débit " + amount + " pour " + accountId + " (" + reason + ")");
            }
            return updated.balance();
        }).exceptionallyCompose(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            if (cause instanceof IllegalStateException) {
                return CompletableFuture.failedFuture(new EconomyException(cause.getMessage()));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    @Override
    public CompletionStage<EconomyTransferResult> transfer(UUID from, UUID to, long amount, String reason) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        return executorManager.supplyIo(() -> {
            boolean fallback = refreshDegradedState();
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
                if (!fallback) {
                    logger.debug(() -> "Écriture async transfert " + amount + " de " + from + " vers " + to + " (" + reason + ")");
                }
                return new EconomyTransferResult(fromBalance, toBalance);
            }
        }).exceptionallyCompose(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            if (cause instanceof IllegalStateException) {
                return CompletableFuture.failedFuture(new EconomyException(cause.getMessage()));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    @Override
    public EconomyTransaction beginTransaction() {
        return new InMemoryTransaction();
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
        boolean fallback = dbProvider.isDegraded();
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

    private record BalanceEntry(long balance, Instant updatedAt) {
    }

    private final class InMemoryTransaction implements EconomyTransaction {

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
            return executorManager.runIo(() -> {
                synchronized (balances) {
                    for (Map.Entry<UUID, Long> entry : deltas.entrySet()) {
                        UUID account = entry.getKey();
                        long delta = entry.getValue();
                        BalanceEntry current = balances.getOrDefault(account, new BalanceEntry(0L, Instant.now()));
                        long next = current.balance() + delta;
                        if (next < 0L) {
                            throw new IllegalStateException("Solde insuffisant pour " + account);
                        }
                        balances.put(account, new BalanceEntry(next, Instant.now()));
                    }
                }
            }).exceptionallyCompose(throwable -> {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                if (cause instanceof IllegalStateException) {
                    return CompletableFuture.failedFuture(new EconomyException(cause.getMessage()));
                }
                return CompletableFuture.failedFuture(cause);
            });
        }

        @Override
        public void rollback() {
            closed = true;
            deltas.clear();
        }
    }
}
