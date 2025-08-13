package com.example.hikabrain.listener;

import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class JoinListener implements Listener {

    private final HikaBrainPlugin plugin;
    private final NamespacedKey key;

    public JoinListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "nav_compass");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("compass.enabled", true)) return;
        if (!cfg.getBoolean("compass.give-on-join", true)) return;

        Player p = e.getPlayer();
        if (!plugin.isWorldAllowed(p.getWorld())) return;

        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                PersistentDataContainer pdc = m.getPersistentDataContainer();
                if (pdc.has(key, PersistentDataType.BYTE)) return; // already has compass
            }
        }

        Material mat = Material.matchMaterial(cfg.getString("compass.material", "CLOCK"));
        if (mat == null) mat = Material.CLOCK;
        ItemStack compass = new ItemStack(mat);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(meta);
        }

        int slot = cfg.getInt("compass.slot", 8);
        if (slot >= 0 && slot < inv.getSize() && inv.getItem(slot) == null) {
            inv.setItem(slot, compass);
            return;
        }
        int empty = inv.firstEmpty();
        if (empty != -1) {
            inv.setItem(empty, compass);
        } else {
            p.getWorld().dropItemNaturally(p.getLocation(), compass);
        }
    }
}
