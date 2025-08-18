package io.github.gordoxgit.henebrain;

import org.bukkit.plugin.java.JavaPlugin;

public final class Henebrain extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Henebrain a démarré avec succès !");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Henebrain s'est arrêté.");
    }
}
