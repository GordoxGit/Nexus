package fr.heneria.nexus.admin.conversation;

import java.util.UUID;

/**
 * Conversation de recherche d'un joueur pour la gestion des sanctions.
 */
public class SanctionSearchConversation {

    private final UUID adminId;

    public SanctionSearchConversation(UUID adminId) {
        this.adminId = adminId;
    }

    public UUID getAdminId() {
        return adminId;
    }
}
