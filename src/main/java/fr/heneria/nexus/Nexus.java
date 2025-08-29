package fr.heneria.nexus;

import org.bukkit.plugin.java.JavaPlugin;

public final class Nexus extends JavaPlugin {

    @Override
    public void onEnable() {
        // Logique d'initialisation du plugin
        getLogger().info("Le plugin Nexus a été activé avec succès !");
    }

    @Override
    public void onDisable() {
        // Logique de désactivation du plugin
        getLogger().info("Le plugin Nexus a été désactivé.");
    }
}
