package com.example.hikabrain.ui.compass;

import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Simple arena category selector GUI opened via lobby compass. */
public class CompassGuiService {
    private final HikaBrainPlugin plugin;

    public CompassGuiService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.AQUA + "Choix du mode");
        add(inv,0,"1v1");
        add(inv,1,"2v2");
        add(inv,2,"3v3");
        add(inv,3,"4v4");
        p.openInventory(inv);
    }

    private void add(Inventory inv, int slot, String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.AQUA + name);
            it.setItemMeta(m);
        }
        inv.setItem(slot, it);
    }
}
