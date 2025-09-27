package com.heneria.nexus.service.core;

import com.heneria.nexus.audit.AuditActionType;
import com.heneria.nexus.audit.AuditEntry;
import com.heneria.nexus.audit.AuditService;
import com.heneria.nexus.api.EconomyException;
import com.heneria.nexus.api.EconomyService;
import com.heneria.nexus.api.EconomyTransaction;
import com.heneria.nexus.api.PurchaseResult;
import com.heneria.nexus.api.ShopService;
import com.heneria.nexus.config.EconomyConfig;
import com.heneria.nexus.db.repository.PlayerClassRepository;
import com.heneria.nexus.db.repository.PlayerCosmeticRepository;
import com.heneria.nexus.util.NexusLogger;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;

/**
 * Default implementation orchestrating shop purchases with the economy backend.
 */
public final class ShopServiceImpl implements ShopService {

    private final NexusLogger logger;
    private final EconomyService economyService;
    private final PlayerClassRepository playerClassRepository;
    private final PlayerCosmeticRepository playerCosmeticRepository;
    private final AtomicReference<EconomyConfig.ShopSettings> shopSettings;
    private final AuditService auditService;

    public ShopServiceImpl(NexusLogger logger,
                           EconomyService economyService,
                           EconomyConfig economyConfig,
                           PlayerClassRepository playerClassRepository,
                           PlayerCosmeticRepository playerCosmeticRepository,
                           AuditService auditService) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.playerClassRepository = Objects.requireNonNull(playerClassRepository, "playerClassRepository");
        this.playerCosmeticRepository = Objects.requireNonNull(playerCosmeticRepository, "playerCosmeticRepository");
        this.shopSettings = new AtomicReference<>(Objects.requireNonNull(economyConfig, "economyConfig").shop());
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    @Override
    public CompletableFuture<PurchaseResult> purchaseClass(Player player, String classId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(classId, "classId");
        String trimmedClassId = classId.trim();
        if (trimmedClassId.isEmpty()) {
            logger.warn("Tentative d'achat d'une classe avec un identifiant vide par " + player.getName());
            return CompletableFuture.completedFuture(PurchaseResult.ERROR);
        }
        UUID playerId = player.getUniqueId();
        return playerClassRepository.isUnlocked(playerId, trimmedClassId)
                .thenCompose(alreadyOwned -> {
                    if (alreadyOwned) {
                        return CompletableFuture.completedFuture(PurchaseResult.ALREADY_OWNED);
                    }
                    EconomyConfig.ClassEntry classEntry = resolveClassEntry(trimmedClassId);
                    if (classEntry == null) {
                        logger.error("Aucun tarif défini pour la classe '" + trimmedClassId + "'.");
                        return CompletableFuture.completedFuture(PurchaseResult.ERROR);
                    }
                    long cost = classEntry.cost();
                    return economyService.getBalance(playerId)
                            .thenCompose(balance -> {
                                if (balance < cost) {
                                    return CompletableFuture.completedFuture(PurchaseResult.INSUFFICIENT_FUNDS);
                                }
                                return executeClassPurchase(player, playerId, trimmedClassId, cost);
                            });
                })
                .exceptionally(throwable -> handleUnexpectedFailure(player, "classe", trimmedClassId, throwable))
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<PurchaseResult> purchaseCosmetic(Player player, String cosmeticId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cosmeticId, "cosmeticId");
        String trimmedCosmeticId = cosmeticId.trim();
        if (trimmedCosmeticId.isEmpty()) {
            logger.warn("Tentative d'achat d'un cosmétique avec un identifiant vide par " + player.getName());
            return CompletableFuture.completedFuture(PurchaseResult.ERROR);
        }
        UUID playerId = player.getUniqueId();
        return playerCosmeticRepository.isUnlocked(playerId, trimmedCosmeticId)
                .thenCompose(alreadyOwned -> {
                    if (alreadyOwned) {
                        return CompletableFuture.completedFuture(PurchaseResult.ALREADY_OWNED);
                    }
                    EconomyConfig.CosmeticEntry cosmeticEntry = resolveCosmeticEntry(trimmedCosmeticId);
                    if (cosmeticEntry == null) {
                        logger.error("Aucun tarif défini pour le cosmétique '" + trimmedCosmeticId + "'.");
                        return CompletableFuture.completedFuture(PurchaseResult.ERROR);
                    }
                    long cost = cosmeticEntry.cost();
                    return economyService.getBalance(playerId)
                            .thenCompose(balance -> {
                                if (balance < cost) {
                                    return CompletableFuture.completedFuture(PurchaseResult.INSUFFICIENT_FUNDS);
                                }
                                return executeCosmeticPurchase(player, playerId, trimmedCosmeticId, cosmeticEntry);
                            });
                })
                .exceptionally(throwable -> handleUnexpectedFailure(player, "cosmétique", trimmedCosmeticId, throwable))
                .toCompletableFuture();
    }

    @Override
    public void applyCatalog(EconomyConfig.ShopSettings shopSettings) {
        this.shopSettings.set(Objects.requireNonNull(shopSettings, "shopSettings"));
    }

    private EconomyConfig.ClassEntry resolveClassEntry(String classId) {
        Map<String, EconomyConfig.ClassEntry> classes = shopSettings.get().classes();
        return classes.get(classId);
    }

    private EconomyConfig.CosmeticEntry resolveCosmeticEntry(String cosmeticId) {
        Map<String, EconomyConfig.CosmeticEntry> cosmetics = shopSettings.get().cosmetics();
        return cosmetics.get(cosmeticId);
    }

    private CompletionStage<PurchaseResult> executeClassPurchase(Player player,
                                                                 UUID playerId,
                                                                 String classId,
                                                                 long cost) {
        EconomyTransaction transaction = economyService.beginTransaction();
        try {
            transaction.debit(playerId, cost, "purchase_class_" + classId);
        } catch (EconomyException exception) {
            transaction.rollback();
            return CompletableFuture.completedFuture(mapEconomyFailure(player, "classe", classId, exception));
        }
        return playerClassRepository.unlock(playerId, classId)
                .thenCompose(ignored -> transaction.commit())
                .thenApply(ignored -> {
                    logPurchase(player, "CLASS", classId, cost);
                    return PurchaseResult.SUCCESS;
                })
                .exceptionallyCompose(throwable -> handleTransactionalFailure(transaction, player, "classe", classId, throwable));
    }

    private CompletionStage<PurchaseResult> executeCosmeticPurchase(Player player,
                                                                    UUID playerId,
                                                                    String cosmeticId,
                                                                    EconomyConfig.CosmeticEntry entry) {
        EconomyTransaction transaction = economyService.beginTransaction();
        try {
            transaction.debit(playerId, entry.cost(), "purchase_cosmetic_" + cosmeticId);
        } catch (EconomyException exception) {
            transaction.rollback();
            return CompletableFuture.completedFuture(mapEconomyFailure(player, "cosmétique", cosmeticId, exception));
        }
        return playerCosmeticRepository.unlock(playerId, cosmeticId, entry.type())
                .thenCompose(ignored -> transaction.commit())
                .thenApply(ignored -> {
                    logPurchase(player, "COSMETIC:" + entry.type().name(), cosmeticId, entry.cost());
                    return PurchaseResult.SUCCESS;
                })
                .exceptionallyCompose(throwable -> handleTransactionalFailure(transaction, player, "cosmétique", cosmeticId, throwable));
    }

    private CompletionStage<PurchaseResult> handleTransactionalFailure(EconomyTransaction transaction,
                                                                       Player player,
                                                                       String itemType,
                                                                       String itemId,
                                                                       Throwable throwable) {
        transaction.rollback();
        Throwable cause = unwrap(throwable);
        if (cause instanceof EconomyException economyException) {
            return CompletableFuture.completedFuture(mapEconomyFailure(player, itemType, itemId, economyException));
        }
        logger.error("Échec de la transaction lors de l'achat de " + itemType + " '" + itemId + "' pour " + player.getName(), cause);
        return CompletableFuture.completedFuture(PurchaseResult.ERROR);
    }

    private PurchaseResult mapEconomyFailure(Player player, String itemType, String itemId, EconomyException exception) {
        if (isInsufficientFunds(exception)) {
            logger.debug(() -> "Fonds insuffisants pour l'achat de " + itemType + " '" + itemId + "' par " + player.getName());
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }
        logger.error("Erreur économie lors de l'achat de " + itemType + " '" + itemId + "' pour " + player.getName(), exception);
        return PurchaseResult.ERROR;
    }

    private PurchaseResult handleUnexpectedFailure(Player player, String itemType, String itemId, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof EconomyException economyException) {
            return mapEconomyFailure(player, itemType, itemId, economyException);
        }
        logger.error("Erreur inattendue lors de l'achat de " + itemType + " '" + itemId + "' pour " + player.getName(), cause);
        return PurchaseResult.ERROR;
    }

    private boolean isInsufficientFunds(EconomyException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalised = message.toLowerCase(Locale.ROOT);
        return normalised.contains("insuffisant") || normalised.contains("insufficient");
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable.getCause() != null && (throwable instanceof java.util.concurrent.CompletionException
                || throwable instanceof java.util.concurrent.ExecutionException)) {
            return unwrap(throwable.getCause());
        }
        return throwable;
    }

    private void logPurchase(Player player, String category, String itemId, long cost) {
        String details = "category=" + category + "; item=" + itemId + "; cost=" + cost;
        auditService.log(new AuditEntry(
                player.getUniqueId(),
                player.getName(),
                AuditActionType.PLAYER_PURCHASE,
                player.getUniqueId(),
                player.getName(),
                details));
    }
}
