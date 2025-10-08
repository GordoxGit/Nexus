package fr.heneria.nexus.core.config;

import fr.heneria.nexus.NexusPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration des messages (messages.yml).
 * Gère tous les messages du plugin avec support MiniMessage.
 *
 * @author GordoxGit
 * @version 1.0.0
 * @since 1.0.0-ALPHA
 */
public class MessagesConfig extends BaseConfig {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Component> componentCache = new HashMap<>();

    private String prefix;

    public MessagesConfig(NexusPlugin plugin) {
        super(plugin, "messages.yml");
    }

    @Override
    public void validate() throws ConfigException {
        requireKey("version");
        requireKey("prefix");

        // Charger le préfixe
        prefix = config.getString("prefix");

        // Valider que les sections principales existent
        requireKey("general");
        requireKey("connection");
        requireKey("errors");

        logger.info("✓ Messages validés, préfixe : " + stripFormatting(prefix));
    }

    @Override
    protected void setDefaults() {
        config.set("version", 1);
        config.set("prefix", "<gradient:#00D4FF:#0080FF><bold>NEXUS</bold></gradient> <dark_gray>»</dark_gray> ");
    }

    /**
     * Récupère un message brut sans préfixe.
     *
     * @param key Clé du message (ex: "general.reload-success")
     * @return Message brut ou clé si absent
     */
    public String getRaw(String key) {
        return config.getString(key, "§c[Message manquant: " + key + "]");
    }

    /**
     * Récupère un message avec préfixe.
     *
     * @param key Clé du message
     * @return Message complet avec préfixe
     */
    public String get(String key) {
        return prefix + getRaw(key);
    }

    /**
     * Récupère un message avec préfixe et remplace les placeholders.
     *
     * @param key Clé du message
     * @param placeholders Map de placeholders (ex: "player" -> "GordoxGit")
     * @return Message avec placeholders remplacés
     */
    public String get(String key, Map<String, String> placeholders) {
        String message = get(key);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return message;
    }

    /**
     * Récupère un Component Adventure avec préfixe (MiniMessage).
     * Utilise un cache pour éviter les parsing répétés.
     *
     * @param key Clé du message
     * @return Component Adventure
     */
    public Component getComponent(String key) {
        return componentCache.computeIfAbsent(key, k -> {
            String message = get(k);
            return miniMessage.deserialize(message);
        });
    }

    /**
     * Récupère un Component avec placeholders.
     *
     * @param key Clé du message
     * @param placeholders Map de placeholders
     * @return Component Adventure
     */
    public Component getComponent(String key, Map<String, String> placeholders) {
        String message = get(key, placeholders);
        return miniMessage.deserialize(message);
    }

    /**
     * Nettoie le cache de components.
     * À appeler après un reload.
     */
    public void clearCache() {
        componentCache.clear();
        logger.info("Cache de messages nettoyé");
    }

    @Override
    public void reload() throws ConfigException {
        super.reload();
        clearCache();
    }

    /**
     * Retire le formatting MiniMessage pour les logs.
     *
     * @param text Texte avec MiniMessage
     * @return Texte brut
     */
    private String stripFormatting(String text) {
        return text.replaceAll("<[^>]+>", "");
    }
}
