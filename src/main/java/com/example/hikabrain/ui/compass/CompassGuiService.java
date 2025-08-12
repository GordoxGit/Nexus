package com.example.hikabrain.ui.compass;

import com.example.hikabrain.Arena;
import com.example.hikabrain.HikaBrainPlugin;
import com.example.hikabrain.Team;
import com.example.hikabrain.arena.ArenaRegistry;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Handles lobby compass GUI with mode categories and arena list. */
public class CompassGuiService {
    /** Inventory holder marker to detect our menus. */
    public static class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final HikaBrainPlugin plugin;
    private final NamespacedKey guiKey;
    private final NamespacedKey catKey;
    private final NamespacedKey arenaKey;
    private final Holder holder = new Holder();

    public CompassGuiService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.guiKey = new NamespacedKey(plugin, "hb_gui");
        this.catKey = new NamespacedKey(plugin, "hb_cat");
        this.arenaKey = new NamespacedKey(plugin, "hb_arena");
    }

    /** Open the main mode selection menu. */
    public void openModeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "Choix du mode");
        addMode(inv, 10, 1);
        addMode(inv, 12, 2);
        addMode(inv, 14, 3);
        addMode(inv, 16, 4);
        inv.setItem(53, closeItem());
        p.openInventory(inv);
    }

    /** Open arena list for given team size. */
    public void openArenaList(Player p, int teamSize) {
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.AQUA + "Arènes " + ChatColor.GRAY + "(" + ChatColor.WHITE + teamSize + "v" + teamSize + ChatColor.GRAY + ")");
        int slot = 10;
        for (String name : plugin.arenaRegistry().list(teamSize)) {
            ItemStack it = new ItemStack(Material.MAP);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(ChatColor.AQUA + name);
                PersistentDataContainer pdc = m.getPersistentDataContainer();
                pdc.set(guiKey, PersistentDataType.STRING, "list");
                pdc.set(arenaKey, PersistentDataType.STRING, name);
                it.setItemMeta(m);
            }
            inv.setItem(slot, it);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // skip edges for readability
            if (slot >= 44) break;
        }
        inv.setItem(45, backItem());
        inv.setItem(53, closeItem());
        p.openInventory(inv);
    }

    /** Attempt to join an arena selected from the GUI. */
    public void attemptJoin(Player p, String arenaName) {
        ArenaRegistry.State state = plugin.arenaRegistry().state(arenaName);
        if (state != ArenaRegistry.State.WAITING && state != ArenaRegistry.State.STARTING) {
            actionBar(p, ChatColor.GRAY + "En cours");
            return;
        }
        try {
            if (plugin.game().arena() == null || !plugin.game().arena().name().equalsIgnoreCase(arenaName)) {
                plugin.game().loadArena(arenaName);
            }
            Arena arena = plugin.game().arena();
            int total = arena.players().get(Team.RED).size() + arena.players().get(Team.BLUE).size();
            if (total >= arena.teamSize() * 2) {
                actionBar(p, ChatColor.RED + "Arène pleine");
                return;
            }
            plugin.game().join(p, null);
        } catch (Exception ex) {
            actionBar(p, ChatColor.RED + "Erreur arène");
        }
    }

    private void addMode(Inventory inv, int slot, int teamSize) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.AQUA + "" + teamSize + "v" + teamSize);
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(guiKey, PersistentDataType.STRING, "mode");
            pdc.set(catKey, PersistentDataType.INTEGER, teamSize);
            it.setItemMeta(m);
        }
        inv.setItem(slot, it);
    }

    private ItemStack backItem() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.YELLOW + "Retour modes");
            m.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "back");
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack closeItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.RED + "Fermer");
            m.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "close");
            it.setItemMeta(m);
        }
        return it;
    }

    private void actionBar(Player p, String msg) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    public NamespacedKey guiKey() { return guiKey; }
    public NamespacedKey catKey() { return catKey; }
    public NamespacedKey arenaKey() { return arenaKey; }
}
