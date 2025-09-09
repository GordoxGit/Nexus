package fr.heneria.nexus.admin.conversation;

import fr.heneria.nexus.arena.manager.ArenaManager;
import fr.heneria.nexus.gui.admin.ArenaListGui;
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

    public static void init(ArenaManager arenaManager, JavaPlugin plugin) {
        instance = new AdminConversationManager(arenaManager, plugin);
    }

    public static AdminConversationManager getInstance() {
        return instance;
    }

    private final ArenaManager arenaManager;
    private final JavaPlugin plugin;
    private final Map<UUID, ArenaCreationConversation> conversations = new ConcurrentHashMap<>();

    private AdminConversationManager(ArenaManager arenaManager, JavaPlugin plugin) {
        this.arenaManager = arenaManager;
        this.plugin = plugin;
    }

    /**
     * Démarre une nouvelle conversation de création d'arène pour l'admin donné.
     */
    public void startConversation(Player admin) {
        UUID id = admin.getUniqueId();
        if (conversations.containsKey(id)) {
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
                new ArenaListGui(arenaManager, this).open(admin);
            }
        }, 5 * 60 * 20L); // 5 minutes
    }

    /**
     * Traite une réponse envoyée par l'administrateur.
     */
    public void handleResponse(Player admin, String message) {
        UUID id = admin.getUniqueId();
        ArenaCreationConversation conversation = conversations.get(id);
        if (conversation == null) {
            return;
        }

        if ("annuler".equalsIgnoreCase(message)) {
            admin.sendMessage("Création d'arène annulée.");
            cancelConversation(admin);
            new ArenaListGui(arenaManager, this).open(admin);
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
                new ArenaListGui(arenaManager, this).open(admin);
                break;
        }
    }

    /**
     * Annule la conversation pour l'administrateur donné.
     */
    public void cancelConversation(Player admin) {
        conversations.remove(admin.getUniqueId());
    }

    /**
     * Vérifie si un joueur est actuellement dans une conversation.
     */
    public boolean isInConversation(Player admin) {
        return conversations.containsKey(admin.getUniqueId());
    }
}
