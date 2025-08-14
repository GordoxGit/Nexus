package com.example.hikabrain.protection;

import com.example.hikabrain.Cuboid;
import com.example.hikabrain.HikaBrainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProtectionService {
    private final HikaBrainPlugin plugin;
    private final Map<String, Cuboid> regions = new HashMap<>();
    private final Set<UUID> protectMode = new HashSet<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final File file;
    private final NamespacedKey actionKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey toolKey;
    public static class MenuHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private final MenuHolder holder = new MenuHolder();

    public ProtectionService(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "protection.yml");
        this.actionKey = new NamespacedKey(plugin, "hb_paction");
        this.nameKey = new NamespacedKey(plugin, "hb_pname");
        this.toolKey = new NamespacedKey(plugin, "hb_protection_tool");
        loadRegions();
    }

    public void loadRegions() {
        regions.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("regions");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(name);
                if (r == null) continue;
                World w = Bukkit.getWorld(r.getString("world", ""));
                if (w == null) continue;
                Location a = new Location(w, r.getInt("x1"), r.getInt("y1"), r.getInt("z1"));
                Location b = new Location(w, r.getInt("x2"), r.getInt("y2"), r.getInt("z2"));
                regions.put(name, new Cuboid(a, b));
            }
        }
    }

    public void saveRegions() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection sec = yml.createSection("regions");
        for (Map.Entry<String, Cuboid> en : regions.entrySet()) {
            Cuboid c = en.getValue();
            ConfigurationSection r = sec.createSection(en.getKey());
            r.set("world", c.world().getName());
            r.set("x1", c.x1()); r.set("y1", c.y1()); r.set("z1", c.z1());
            r.set("x2", c.x2()); r.set("y2", c.y2()); r.set("z2", c.z2());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save protection regions: " + e.getMessage());
        }
    }

    public boolean isProtected(Location location) {
        for (Cuboid c : regions.values()) {
            if (c.contains(location)) return true;
        }
        return false;
    }

    public void addRegion(String name, Cuboid cuboid) {
        regions.put(name, cuboid);
    }

    public Map<String, Cuboid> regions() { return regions; }

    public void removeRegion(String name) { regions.remove(name); }

    /** Open GUI listing protected regions for management. */
    public void openListMenu(Player player) {
        loadRegions();
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_GRAY + "Gestion des Zones Protégées");

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int slot = 10;
        for (Map.Entry<String, Cuboid> en : regions.entrySet()) {
            if (slot >= 54) break;
            String name = en.getKey();
            Cuboid c = en.getValue();

            ItemStack info = new ItemStack(Material.BEACON);
            ItemMeta im = info.getItemMeta();
            if (im != null) {
                im.setDisplayName(ChatColor.AQUA + name);
                im.setLore(Arrays.asList(
                        ChatColor.GRAY + "(" + c.x1() + "," + c.y1() + "," + c.z1() + ")",
                        ChatColor.GRAY + "(" + c.x2() + "," + c.y2() + "," + c.z2() + ")"
                ));
                info.setItemMeta(im);
            }

            ItemStack edit = new ItemStack(Material.ANVIL);
            ItemMeta em = edit.getItemMeta();
            if (em != null) {
                em.setDisplayName(ChatColor.YELLOW + "Modifier");
                em.setLore(Arrays.asList(ChatColor.GRAY + "Redéfinir cette zone."));
                em.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "edit");
                em.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
                edit.setItemMeta(em);
            }

            ItemStack del = new ItemStack(Material.BARRIER);
            ItemMeta dm = del.getItemMeta();
            if (dm != null) {
                dm.setDisplayName(ChatColor.RED + "Supprimer");
                dm.setLore(Arrays.asList(ChatColor.GRAY + "Supprimer cette zone."));
                dm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "delete");
                dm.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
                del.setItemMeta(dm);
            }

            inv.setItem(slot, info);
            inv.setItem(slot + 1, edit);
            inv.setItem(slot + 2, del);
            slot += 9;
        }
        player.openInventory(inv);
    }

    public boolean isInProtectMode(Player player) {
        return protectMode.contains(player.getUniqueId());
    }

    public void enableProtectMode(Player player) {
        protectMode.add(player.getUniqueId());
    }

    public void disableProtectMode(Player player) {
        UUID id = player.getUniqueId();
        protectMode.remove(id);
        pos1.remove(id);
        pos2.remove(id);
    }

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc);
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc);
    }

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }

    /** Remove any existing HikaBrain selection tool from the player's inventory. */
    public void removeSelectionTool(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.SHEARS) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE)) {
                player.getInventory().remove(item);
            }
        }
    }

    /** Give the selection tool, ensuring there is only one instance. */
    public void giveSelectionTool(Player player) {
        removeSelectionTool(player);

        ItemStack tool = new ItemStack(Material.SHEARS);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aOutil de Sélection");
            meta.setLore(Arrays.asList("§7Clic gauche = Pos1", "§7Clic droit = Pos2"));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            tool.setItemMeta(meta);
        }
        player.getInventory().addItem(tool);
    }
}
