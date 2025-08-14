package com.example.hikabrain.ui.protection;

import com.example.hikabrain.Cuboid;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.protection.ProtectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Arrays;

/** GUI to list and manage protected regions. */
public class ProtectionGuiService {
    public static class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final HikaBrainPlugin plugin;
    private final ProtectionService protection;
    private final NamespacedKey actionKey;
    private final NamespacedKey nameKey;
    private final Holder holder = new Holder();

    public ProtectionGuiService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.protection = plugin.protection();
        this.actionKey = new NamespacedKey(plugin, "hb_paction");
        this.nameKey = new NamespacedKey(plugin, "hb_pname");
    }

    /** Open GUI listing all protected regions. */
    public void openProtectionListGui(Player p) {
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "Zones protégées");
        int slot = 10;
        for (Map.Entry<String, Cuboid> en : protection.regions().entrySet()) {
            if (slot >= 54) break;
            String name = en.getKey();
            Cuboid c = en.getValue();

            ItemStack zone = new ItemStack(Material.BEACON);
            ItemMeta zm = zone.getItemMeta();
            if (zm != null) {
                zm.setDisplayName(ChatColor.AQUA + name);
                zm.setLore(Arrays.asList(
                        ChatColor.GRAY + "(" + c.x1() + "," + c.y1() + "," + c.z1() + ")",
                        ChatColor.GRAY + "(" + c.x2() + "," + c.y2() + "," + c.z2() + ")"
                ));
                zm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "noop");
                zm.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
                zone.setItemMeta(zm);
            }

            ItemStack edit = new ItemStack(Material.ANVIL);
            ItemMeta em = edit.getItemMeta();
            if (em != null) {
                em.setDisplayName(ChatColor.YELLOW + "Modifier");
                em.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "edit");
                em.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
                edit.setItemMeta(em);
            }

            ItemStack del = new ItemStack(Material.BARRIER);
            ItemMeta dm = del.getItemMeta();
            if (dm != null) {
                dm.setDisplayName(ChatColor.RED + "Supprimer");
                dm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "delete");
                dm.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
                del.setItemMeta(dm);
            }

            inv.setItem(slot, zone);
            inv.setItem(slot + 1, edit);
            inv.setItem(slot + 2, del);
            slot += 9;
        }
        p.openInventory(inv);
    }

    /** Handle clicks inside the protection list GUI. */
    public void handleClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!e.isLeftClick()) return;
        ItemStack it = e.getCurrentItem();
        if (it == null) return;
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        String action = m.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String name = m.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        if (action == null || name == null) return;
        Player p = (Player) e.getWhoClicked();
        switch (action) {
            case "edit" -> {
                protection.enableProtectMode(p);
                ItemStack tool = new ItemStack(Material.SHEARS);
                ItemMeta meta = tool.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§aOutil de Sélection");
                    meta.setLore(Arrays.asList("§7Clic gauche = Pos1", "§7Clic droit = Pos2"));
                    tool.setItemMeta(meta);
                }
                p.getInventory().addItem(tool);
                p.sendMessage(ChatColor.GREEN + "Sélectionnez la nouvelle zone puis /hb confirm " + name);
                p.closeInventory();
            }
            case "delete" -> {
                protection.removeRegion(name);
                protection.saveRegions();
                p.sendMessage(ChatColor.RED + "Zone supprimée: " + name);
                openProtectionListGui(p);
            }
        }
    }
}
