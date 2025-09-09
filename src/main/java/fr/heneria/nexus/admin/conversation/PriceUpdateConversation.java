package fr.heneria.nexus.admin.conversation;

import fr.heneria.nexus.shop.model.ShopItem;

import java.util.UUID;

/**
 * Conversation de modification de prix pour un item de boutique.
 */
public class PriceUpdateConversation {

    private final UUID adminId;
    private final ShopItem item;

    public PriceUpdateConversation(UUID adminId, ShopItem item) {
        this.adminId = adminId;
        this.item = item;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public ShopItem getItem() {
        return item;
    }
}
