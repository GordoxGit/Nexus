package com.example.hikabrain.listener;

import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InteractListener implements Listener {

    private final HikaBrainPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public InteractListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "nav_compass");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("compass.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("compass.open-on-right-click", true)) return;

        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack it = e.getItem();
        if (it == null || it.getType() == Material.AIR) return;
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        if (!m.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("compass.cooldown-ms", 200L);
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < cd) return;
        lastUse.put(p.getUniqueId(), now);

        plugin.compassGui().openModeMenu(p);
        e.setCancelled(true);
    }
}
