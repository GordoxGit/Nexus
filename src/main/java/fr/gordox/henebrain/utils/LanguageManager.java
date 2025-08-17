package fr.gordox.henebrain.utils;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import fr.gordox.henebrain.Henebrain;

public class LanguageManager {
    private final Henebrain plugin;
    private FileConfiguration messages;
    private File messagesFile;

    public LanguageManager(Henebrain plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public void reloadMessages() {
        String lang = plugin.getConfigManager().getString("language-file", "fr_FR.yml");
        File langDir = new File(plugin.getDataFolder(), "languages");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        messagesFile = new File(langDir, lang);
        if (!messagesFile.exists()) {
            plugin.saveResource("languages/" + lang, false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String key, String... placeholders) {
        String msg = messages.getString(key, key);
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        return msg;
    }
}
