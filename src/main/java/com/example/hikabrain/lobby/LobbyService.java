package com.example.hikabrain.lobby;

import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerTeleportEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

/** Service handling lobby spawn and navigation compass. */
public class LobbyService {
    private final HikaBrainPlugin plugin;
    private final NamespacedKey compassKey;

    public LobbyService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.compassKey = new NamespacedKey(plugin, "hb_compass");
    }

    /** Compass PDC key. */
    public NamespacedKey compassKey() { return compassKey; }

    /** Teleport the player to the configured lobby location if available. */
    public void teleport(Player p) {
        if (plugin.lobby() != null) {
            p.teleport(plugin.lobby(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    /** Give the lobby selector item and clear inventory. */
    public void giveLobbyItem(Player p) {
        p.getInventory().clear();
        // Use a clock as lobby selector instead of the recovery compass.
        ItemStack it = new ItemStack(Material.CLOCK);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            try {
                meta.getClass().getMethod("displayName", Component.class)
                        .invoke(meta, Component.text("Sélecteur d'arène", NamedTextColor.AQUA));
            } catch (Throwable t) {
                meta.setDisplayName(ChatColor.AQUA + "Sélecteur d'arène");
            }
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte)1);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        p.getInventory().setItem(4, it);
    }

    /**
     * Applique l'inventaire et l'UI du lobby SANS téléporter le joueur.
     */
    public void setLobbyMode(Player p) {
        giveLobbyItem(p);
        plugin.scoreboard().showLobby(p);
        plugin.tablist().showLobby(p);
    }

    /**
     * Applique le profil complet du lobby, INCLUANT la téléportation.
     */
    public void apply(Player p) {
        teleport(p);
        setLobbyMode(p);
    }
}
