package fr.heneria.nexus.admin.conversation;

import fr.heneria.nexus.npc.NpcManager;
import fr.heneria.nexus.gui.admin.npc.NpcListGui;

import java.util.UUID;

/**
 * Conversation state for creating an NPC.
 */
public class NpcCreationConversation {

    private final UUID adminId;
    private final NpcManager npcManager;
    private final NpcListGui reopenGui;
    private NpcConversationStep step = NpcConversationStep.AWAITING_NAME;
    private String name;

    public NpcCreationConversation(UUID adminId, NpcManager npcManager, NpcListGui reopenGui) {
        this.adminId = adminId;
        this.npcManager = npcManager;
        this.reopenGui = reopenGui;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public NpcListGui getReopenGui() {
        return reopenGui;
    }

    public NpcConversationStep getStep() {
        return step;
    }

    public void setStep(NpcConversationStep step) {
        this.step = step;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
