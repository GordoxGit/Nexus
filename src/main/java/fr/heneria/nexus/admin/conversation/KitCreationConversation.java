package fr.heneria.nexus.admin.conversation;

import java.util.UUID;

/**
 * Représente une conversation de création de kit.
 */
public class KitCreationConversation {

    private final UUID adminId;

    public KitCreationConversation(UUID adminId) {
        this.adminId = adminId;
    }

    public UUID getAdminId() {
        return adminId;
    }
}
