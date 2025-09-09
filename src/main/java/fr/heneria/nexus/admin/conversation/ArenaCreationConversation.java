package fr.heneria.nexus.admin.conversation;

import java.util.UUID;

/**
 * Représente l'état d'une conversation de création d'arène.
 */
public class ArenaCreationConversation {

    private final UUID adminId;
    private ConversationStep currentStep;
    private String arenaName;
    private int maxPlayers;

    public ArenaCreationConversation(UUID adminId) {
        this.adminId = adminId;
        this.currentStep = ConversationStep.AWAITING_ARENA_NAME;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public ConversationStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(ConversationStep currentStep) {
        this.currentStep = currentStep;
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
