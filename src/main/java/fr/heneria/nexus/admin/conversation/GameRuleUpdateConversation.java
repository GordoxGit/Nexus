package fr.heneria.nexus.admin.conversation;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Conversation de mise à jour d'une règle de jeu.
 */
public class GameRuleUpdateConversation {

    private final UUID adminId;
    private final String key;
    private final boolean doubleValue;
    private final Consumer<Player> reopen;

    public GameRuleUpdateConversation(UUID adminId, String key, boolean doubleValue, Consumer<Player> reopen) {
        this.adminId = adminId;
        this.key = key;
        this.doubleValue = doubleValue;
        this.reopen = reopen;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getKey() {
        return key;
    }

    public boolean isDoubleValue() {
        return doubleValue;
    }

    public Consumer<Player> getReopen() {
        return reopen;
    }
}
