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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

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
    private final Set<UUID> joiningNow = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastGuiClickTick = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> refreshTasks = new ConcurrentHashMap<>();

    public CompassGuiService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.guiKey = new NamespacedKey(plugin, "hb_gui");
        this.catKey = new NamespacedKey(plugin, "hb_cat");
        this.arenaKey = new NamespacedKey(plugin, "hb_arena");
    }

    /** Open the main mode selection menu. */
    public void openModeMenu(Player p) {
        if (!plugin.isWorldAllowed(p.getWorld())) return;
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "Choix du mode");
        ItemStack filler = fillerItem();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        addMode(inv, 10, 1, Material.IRON_SWORD);
        addMode(inv, 12, 2, Material.DIAMOND_SWORD);
        addMode(inv, 14, 3, Material.NETHERITE_SWORD);
        addMode(inv, 16, 4, Material.BOW);
        inv.setItem(53, closeItem());
        p.openInventory(inv);
    }

    /** Open arena list for given team size. */
    public void openArenaList(Player p, int teamSize) {
        if (!plugin.isWorldAllowed(p.getWorld())) return;
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.AQUA + "Arènes " + ChatColor.GRAY + "(" + ChatColor.WHITE + teamSize + "v" + teamSize + ChatColor.GRAY + ")");
        ItemStack filler = fillerItem();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(45, backItem());
        inv.setItem(53, closeItem());
        fillArenaListItems(inv, teamSize, p, filler);
        p.openInventory(inv);

        cancelUpdateTask(p);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel();
                    refreshTasks.remove(p.getUniqueId());
                    return;
                }
                fillArenaListItems(inv, teamSize, p, filler);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        refreshTasks.put(p.getUniqueId(), task);
    }

    /** Attempt to join an arena selected from the GUI. */
    public void attemptJoin(Player p, String arenaName) {
        long tick = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        Long last = lastGuiClickTick.get(id);
        if (last != null && tick - last < 250) return;
        lastGuiClickTick.put(id, tick);
        if (!joiningNow.add(id)) return;
        actionBar(p, ChatColor.GRAY + "En cours...");
        try {
            if (plugin.game().arena() != null && plugin.game().teamOf(p) != Team.SPECTATOR) {
                return; // already in arena
            }
            ArenaRegistry.State state = plugin.arenaRegistry().state(arenaName);
            if (state != ArenaRegistry.State.WAITING && state != ArenaRegistry.State.STARTING) {
                actionBar(p, ChatColor.RED + "Arène indisponible");
                return;
            }
            try {
                if (plugin.game().arena() == null || !plugin.game().arena().name().equalsIgnoreCase(arenaName)) {
                    plugin.game().loadArena(arenaName);
                }
                Arena arena = plugin.game().arena();
                int total = arena.players().get(Team.RED).size() + arena.players().get(Team.BLUE).size();
                if (arena.isActive() || total >= arena.teamSize() * 2) {
                    actionBar(p, ChatColor.RED + "Arène indisponible");
                    return;
                }
                plugin.game().join(p, null);
                if (plugin.game().teamOf(p) != Team.SPECTATOR) {
                    actionBar(p, ChatColor.GREEN + "Rejoint");
                } else {
                    actionBar(p, ChatColor.RED + "Arène indisponible");
                }
            } catch (Exception ex) {
                actionBar(p, ChatColor.RED + "Erreur arène");
            }
        } finally {
            joiningNow.remove(id);
        }
    }

    private void addMode(Inventory inv, int slot, int teamSize, Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.AQUA + "" + teamSize + "v" + teamSize);
            Arena a = plugin.game().arena();
            int count = 0;
            if (a != null && a.teamSize() == teamSize) {
                count = a.players().get(Team.RED).size() + a.players().get(Team.BLUE).size();
            }
            List<String> lore = new ArrayList<>();
            lore.add("§a" + count + " joueurs en " + teamSize + "v" + teamSize);
            m.setLore(lore);
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(guiKey, PersistentDataType.STRING, "mode");
            pdc.set(catKey, PersistentDataType.INTEGER, teamSize);
            it.setItemMeta(m);
        }
        inv.setItem(slot, it);
    }

    private ItemStack fillerItem() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = filler.getItemMeta();
        if (m != null) {
            m.setDisplayName(" ");
            filler.setItemMeta(m);
        }
        return filler;
    }

    private void fillArenaListItems(Inventory inv, int teamSize, Player p, ItemStack filler) {
        int slot = 10;
        while (slot < 44) {
            inv.setItem(slot, filler);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }
        slot = 10;
        for (String name : plugin.arenaRegistry().list(teamSize)) {
            Arena a = plugin.game().arena() != null && plugin.game().arena().name().equalsIgnoreCase(name) ? plugin.game().arena() : null;
            int currentPlayers = (a != null) ? a.players().get(Team.RED).size() + a.players().get(Team.BLUE).size() : 0;
            int maxPlayers = teamSize * 2;
            boolean isActive = (a != null) && a.isActive();

            List<String> lore = new ArrayList<>();
            lore.add("§7Map: §b" + name);
            lore.add(" ");
            lore.add("§7Joueurs: " + (isActive ? "§c" : "§a") + currentPlayers + "§7/§c" + maxPlayers);
            lore.add("§7État: " + (isActive ? "§cEn cours" : "§aEn attente"));
            lore.add(" ");
            lore.add("§e► Cliquez pour rejoindre");

            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            if (m != null) {
                m.setDisplayName(ChatColor.AQUA + name);
                m.setLore(lore);
                if (!isActive) {
                    Enchantment glow = Enchantment.getByName("DURABILITY");
                    if (glow != null) m.addEnchant(glow, 1, true);
                    m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                PersistentDataContainer pdc = m.getPersistentDataContainer();
                pdc.set(guiKey, PersistentDataType.STRING, "list");
                pdc.set(arenaKey, PersistentDataType.STRING, name);
                it.setItemMeta(m);
            }
            inv.setItem(slot, it);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
            if (slot >= 44) break;
        }

        if (plugin.game().arena() != null && plugin.game().teamOf(p) != Team.SPECTATOR && !plugin.game().arena().isActive()) {
            ItemStack leave = new ItemStack(Material.BARRIER);
            ItemMeta lm = leave.getItemMeta();
            if (lm != null) {
                lm.setDisplayName("§cQuitter la file");
                lm.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, "leave");
                leave.setItemMeta(lm);
            }
            inv.setItem(49, leave);
        } else {
            inv.setItem(49, filler);
        }
    }

    public void cancelUpdateTask(Player p) {
        BukkitTask task = refreshTasks.remove(p.getUniqueId());
        if (task != null) task.cancel();
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
