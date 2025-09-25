package com.heneria.nexus.service.core;

import com.heneria.nexus.config.CoreConfig;
import com.heneria.nexus.concurrent.ExecutorManager;
import com.heneria.nexus.api.EconomyException;
import com.heneria.nexus.api.EconomyService;
import com.heneria.nexus.api.EconomyTransaction;
import com.heneria.nexus.api.EconomyTransferResult;
import com.heneria.nexus.util.NexusLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Economy service backed by Vault.
 */
public final class VaultEconomyService implements EconomyService {

    private final NexusLogger logger;
    private final ExecutorManager executorManager;
    private final Economy vaultEconomy;
    private final AtomicReference<CoreConfig.DegradedModeSettings> degradedSettings = new AtomicReference<>();

    public VaultEconomyService(NexusLogger logger,
                               ExecutorManager executorManager,
                               Economy vaultEconomy,
                               CoreConfig config) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executorManager = Objects.requireNonNull(executorManager, "executorManager");
        this.vaultEconomy = Objects.requireNonNull(vaultEconomy, "vaultEconomy");
        this.degradedSettings.set(Objects.requireNonNull(config, "config").degradedModeSettings());
        this.logger.info("Intégration Vault activée pour l'économie Nexus");
    }

    @Override
    public CompletionStage<Long> getBalance(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        return supplyEconomy(() -> toLong(vaultEconomy.getBalance(resolve(accountId))));
    }

    @Override
    public CompletionStage<Long> credit(UUID accountId, long amount, String reason) {
        Objects.requireNonNull(accountId, "accountId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        return supplyEconomy(() -> {
            OfflinePlayer player = resolve(accountId);
            EconomyResponse response = vaultEconomy.depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                throw new EconomyException("Échec du crédit Vault : " + failureMessage(response));
            }
            return toLong(response.balance);
        });
    }

    @Override
    public CompletionStage<Long> debit(UUID accountId, long amount, String reason) {
        Objects.requireNonNull(accountId, "accountId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        return supplyEconomy(() -> {
            OfflinePlayer player = resolve(accountId);
            EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount);
            if (!response.transactionSuccess()) {
                throw new EconomyException("Échec du débit Vault : " + failureMessage(response));
            }
            return toLong(response.balance);
        });
    }

    @Override
    public CompletionStage<EconomyTransferResult> transfer(UUID from, UUID to, long amount, String reason) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new EconomyException("Montant négatif"));
        }
        return supplyEconomy(() -> {
            OfflinePlayer source = resolve(from);
            OfflinePlayer target = resolve(to);
            EconomyResponse withdrawal = vaultEconomy.withdrawPlayer(source, amount);
            if (!withdrawal.transactionSuccess()) {
                throw new EconomyException("Échec du débit Vault : " + failureMessage(withdrawal));
            }
            EconomyResponse deposit = vaultEconomy.depositPlayer(target, amount);
            if (!deposit.transactionSuccess()) {
                EconomyResponse rollback = vaultEconomy.depositPlayer(source, amount);
                if (!rollback.transactionSuccess()) {
                    logger.warn("Rollback Vault impossible après un transfert raté : " + failureMessage(rollback));
                }
                throw new EconomyException("Échec du crédit Vault : " + failureMessage(deposit));
            }
            long fromBalance = toLong(vaultEconomy.getBalance(source));
            long toBalance = toLong(vaultEconomy.getBalance(target));
            return new EconomyTransferResult(fromBalance, toBalance);
        });
    }

    @Override
    public EconomyTransaction beginTransaction() {
        return new VaultEconomyTransaction();
    }

    @Override
    public void applyDegradedModeSettings(CoreConfig.DegradedModeSettings settings) {
        degradedSettings.set(Objects.requireNonNull(settings, "settings"));
    }

    @Override
    public boolean isDegraded() {
        return false;
    }

    private <T> CompletionStage<T> supplyEconomy(EconomyCallable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executorManager.compute().execute(() -> {
            try {
                future.complete(callable.call());
            } catch (EconomyException exception) {
                future.completeExceptionally(exception);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private CompletionStage<Void> runEconomy(EconomyRunnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executorManager.compute().execute(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (EconomyException exception) {
                future.completeExceptionally(exception);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private OfflinePlayer resolve(UUID uniqueId) {
        return Bukkit.getOfflinePlayer(uniqueId);
    }

    private long toLong(double value) {
        return Math.round(value);
    }

    private String failureMessage(EconomyResponse response) {
        if (response == null) {
            return "raison inconnue";
        }
        if (response.errorMessage != null && !response.errorMessage.isBlank()) {
            return response.errorMessage;
        }
        return response.type != null ? response.type.name() : "raison inconnue";
    }

    private final class VaultEconomyTransaction implements EconomyTransaction {

        private final List<TransactionOperation> operations = new ArrayList<>();
        private boolean closed;

        @Override
        public synchronized void credit(UUID account, long amount, String reason) {
            Objects.requireNonNull(account, "account");
            if (amount < 0L) {
                throw new IllegalArgumentException("Montant négatif");
            }
            ensureOpen();
            operations.add(new TransactionOperation(OperationType.CREDIT, account, amount, reason));
        }

        @Override
        public synchronized void debit(UUID account, long amount, String reason) throws EconomyException {
            Objects.requireNonNull(account, "account");
            if (amount < 0L) {
                throw new EconomyException("Montant négatif");
            }
            ensureOpen();
            operations.add(new TransactionOperation(OperationType.DEBIT, account, amount, reason));
        }

        @Override
        public CompletionStage<Void> commit() {
            List<TransactionOperation> snapshot;
            synchronized (this) {
                if (closed) {
                    return CompletableFuture.completedFuture(null);
                }
                closed = true;
                snapshot = List.copyOf(operations);
            }
            return runEconomy(() -> applyOperations(snapshot));
        }

        @Override
        public synchronized void rollback() {
            closed = true;
            operations.clear();
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Transaction déjà terminée");
            }
        }

        private void applyOperations(List<TransactionOperation> ops) throws EconomyException {
            List<TransactionOperation> applied = new ArrayList<>();
            try {
                for (TransactionOperation operation : ops) {
                    execute(operation);
                    applied.add(operation);
                }
            } catch (EconomyException exception) {
                rollbackApplied(applied);
                throw exception;
            } catch (RuntimeException exception) {
                rollbackApplied(applied);
                throw exception;
            }
        }

        private void execute(TransactionOperation operation) throws EconomyException {
            OfflinePlayer player = resolve(operation.account());
            if (operation.type() == OperationType.CREDIT) {
                EconomyResponse response = vaultEconomy.depositPlayer(player, operation.amount());
                if (!response.transactionSuccess()) {
                    throw new EconomyException("Échec du crédit Vault : " + failureMessage(response));
                }
                return;
            }
            EconomyResponse response = vaultEconomy.withdrawPlayer(player, operation.amount());
            if (!response.transactionSuccess()) {
                throw new EconomyException("Échec du débit Vault : " + failureMessage(response));
            }
        }

        private void rollbackApplied(List<TransactionOperation> applied) {
            for (int index = applied.size() - 1; index >= 0; index--) {
                TransactionOperation operation = applied.get(index);
                OfflinePlayer player = resolve(operation.account());
                try {
                    if (operation.type() == OperationType.CREDIT) {
                        EconomyResponse response = vaultEconomy.withdrawPlayer(player, operation.amount());
                        if (!response.transactionSuccess()) {
                            logger.warn("Rollback Vault impossible pour " + operation.account() + " : " + failureMessage(response));
                        }
                    } else {
                        EconomyResponse response = vaultEconomy.depositPlayer(player, operation.amount());
                        if (!response.transactionSuccess()) {
                            logger.warn("Rollback Vault impossible pour " + operation.account() + " : " + failureMessage(response));
                        }
                    }
                } catch (Throwable throwable) {
                    logger.warn("Erreur lors du rollback Vault pour " + operation.account(), throwable);
                }
            }
        }
    }

    private record TransactionOperation(OperationType type, UUID account, long amount, String reason) {
    }

    private enum OperationType {
        CREDIT,
        DEBIT
    }

    @FunctionalInterface
    private interface EconomyCallable<T> {
        T call() throws EconomyException;
    }

    @FunctionalInterface
    private interface EconomyRunnable {
        void run() throws EconomyException;
    }
}
