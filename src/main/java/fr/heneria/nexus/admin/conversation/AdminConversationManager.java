package fr.heneria.nexus.admin.conversation;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.gui.admin.ArenaListGui;
import fr.heneria.nexus.gui.admin.shop.ShopCategoryGui;
import fr.heneria.nexus.admin.placement.AdminPlacementManager;
import fr.heneria.nexus.shop.manager.ShopManager;
import fr.heneria.nexus.shop.repository.ShopRepository;
import fr.heneria.nexus.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les conversations de création d'arène pour les administrateurs.
 */
public class AdminConversationManager {

    private static AdminConversationManager instance;

    public static void init(ArenaManager arenaManager, ShopManager shopManager, ShopRepository shopRepository, JavaPlugin plugin) {
        instance = new AdminConversationManager(arenaManager, shopManager, shopRepository, plugin);
    }

    public static AdminConversationManager getInstance() {
        return instance;
    }

    private final ArenaManager arenaManager;
    private final ShopManager shopManager;
    private final ShopRepository shopRepository;
    private final JavaPlugin plugin;
    private final Map<UUID, ArenaCreationConversation> conversations = new ConcurrentHashMap<>();
    private final Map<UUID, PriceUpdateConversation> priceConversations = new ConcurrentHashMap<>();

    private AdminConversationManager(ArenaManager arenaManager, ShopManager shopManager, ShopRepository shopRepository, JavaPlugin plugin) {
        this.arenaManager = arenaManager;
        this.shopManager = shopManager;
        this.shopRepository = shopRepository;
        this.plugin = plugin;
    }

    /**
     * Démarre une nouvelle conversation de création d'arène pour l'admin donné.
     */
    public void startConversation(Player admin) {
        UUID id = admin.getUniqueId();
        if (conversations.containsKey(id) || priceConversations.containsKey(id)) {
            admin.sendMessage("Une conversation est déjà en cours.");
            return;
        }

        ArenaCreationConversation conversation = new ArenaCreationConversation(id);
        conversations.put(id, conversation);
        admin.sendMessage("Entrez le nom de la nouvelle arène (ou 'annuler').");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (conversations.containsKey(id)) {
                admin.sendMessage("Création d'arène annulée (timeout).");
                cancelConversation(admin);
                new ArenaListGui(arenaManager, this, AdminPlacementManager.getInstance()).open(admin);
            }
        }, 5 * 60 * 20L); // 5 minutes
    }

    /**
     * Démarre une conversation de modification de prix pour un item.
     */
    public void startPriceConversation(Player admin, ShopItem item) {
        UUID id = admin.getUniqueId();
        if (priceConversations.containsKey(id) || conversations.containsKey(id)) {
            admin.sendMessage("Une conversation est déjà en cours.");
            return;
        }
        PriceUpdateConversation conv = new PriceUpdateConversation(id, item);
        priceConversations.put(id, conv);
        admin.sendMessage("Entrez le nouveau prix pour cet item (ou 'annuler').");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (priceConversations.containsKey(id)) {
                admin.sendMessage("Modification de prix annulée (timeout).");
                cancelConversation(admin);
                new ShopCategoryGui(shopManager, item.getCategory()).open(admin);
            }
        }, 5 * 60 * 20L);
    }

    /**
     * Traite une réponse envoyée par l'administrateur.
     */
    public void handleResponse(Player admin, String message) {
        UUID id = admin.getUniqueId();
        if (conversations.containsKey(id)) {
            ArenaCreationConversation conversation = conversations.get(id);
            if ("annuler".equalsIgnoreCase(message)) {
                admin.sendMessage("Création d'arène annulée.");
                cancelConversation(admin);
                new ArenaListGui(arenaManager, this, AdminPlacementManager.getInstance()).open(admin);
                return;
            }

            switch (conversation.getCurrentStep()) {
                case AWAITING_ARENA_NAME:
                    if (arenaManager.getArena(message) != null) {
                        admin.sendMessage("Ce nom d'arène est déjà utilisé. Essayez encore.");
                        return;
                    }
                    conversation.setArenaName(message);
                    conversation.setCurrentStep(ConversationStep.AWAITING_MAX_PLAYERS);
                    admin.sendMessage("Entrez le nombre maximum de joueurs.");
                    break;
                case AWAITING_MAX_PLAYERS:
                    int maxPlayers;
                    try {
                        maxPlayers = Integer.parseInt(message);
                        if (maxPlayers <= 0) {
                            admin.sendMessage("Le nombre doit être un entier positif. Réessayez.");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        admin.sendMessage("Le nombre doit être un entier positif. Réessayez.");
                        return;
                    }
                    conversation.setMaxPlayers(maxPlayers);
                    arenaManager.createArena(conversation.getArenaName(), conversation.getMaxPlayers());
                    admin.sendMessage("Arène créée avec succès.");
                    cancelConversation(admin);
                    new ArenaListGui(arenaManager, this, AdminPlacementManager.getInstance()).open(admin);
                    break;
            }
            return;
        }

        PriceUpdateConversation priceConv = priceConversations.get(id);
        if (priceConv == null) {
            return;
        }

        if ("annuler".equalsIgnoreCase(message)) {
            admin.sendMessage("Modification de prix annulée.");
            cancelConversation(admin);
            new ShopCategoryGui(shopManager, priceConv.getItem().getCategory()).open(admin);
            return;
        }

        int newPrice;
        try {
            newPrice = Integer.parseInt(message);
            if (newPrice < 0) {
                admin.sendMessage("Le prix doit être un entier positif. Réessayez.");
                return;
            }
        } catch (NumberFormatException e) {
            admin.sendMessage("Le prix doit être un entier positif. Réessayez.");
            return;
        }

        shopManager.updateItemPrice(priceConv.getItem(), newPrice);
        shopRepository.saveItem(priceConv.getItem());
        admin.sendMessage("Prix mis à jour.");
        cancelConversation(admin);
        new ShopCategoryGui(shopManager, priceConv.getItem().getCategory()).open(admin);
    }

    /**
     * Annule la conversation pour l'administrateur donné.
     */
    public void cancelConversation(Player admin) {
        UUID id = admin.getUniqueId();
        conversations.remove(id);
        priceConversations.remove(id);
    }

    /**
     * Vérifie si un joueur est actuellement dans une conversation.
     */
    public boolean isInConversation(Player admin) {
        UUID id = admin.getUniqueId();
        return conversations.containsKey(id) || priceConversations.containsKey(id);
    }
}
