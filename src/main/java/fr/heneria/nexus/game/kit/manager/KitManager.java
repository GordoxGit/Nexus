package fr.heneria.nexus.game.kit.manager;

import fr.heneria.nexus.game.kit.model.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les kits de départ disponibles.
 */
public class KitManager {

    private static KitManager instance;

    private final Map<String, Kit> kits = new ConcurrentHashMap<>();

    private KitManager() {
    }

    public static KitManager getInstance() {
        if (instance == null) {
            instance = new KitManager();
        }
        return instance;
    }

    /**
     * Charge les kits par défaut en mémoire.
     */
    public void loadKits() {
        kits.clear();

        // Kit Solo
        List<ItemStack> soloItems = new ArrayList<>();
        soloItems.add(enchant(new ItemStack(Material.LEATHER_HELMET), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        soloItems.add(enchant(new ItemStack(Material.IRON_CHESTPLATE), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        soloItems.add(enchant(new ItemStack(Material.IRON_LEGGINGS), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        soloItems.add(enchant(new ItemStack(Material.LEATHER_BOOTS), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        soloItems.add(enchant(new ItemStack(Material.STONE_SWORD), Enchantment.DAMAGE_ALL, 2));
        soloItems.add(new ItemStack(Material.BOW));
        kits.put("Solo", new Kit("Solo", soloItems));

        // Kit Équipe
        List<ItemStack> teamItems = new ArrayList<>();
        teamItems.add(enchant(new ItemStack(Material.LEATHER_HELMET), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        teamItems.add(enchant(new ItemStack(Material.LEATHER_CHESTPLATE), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        teamItems.add(enchant(new ItemStack(Material.LEATHER_LEGGINGS), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        teamItems.add(enchant(new ItemStack(Material.LEATHER_BOOTS), Enchantment.PROTECTION_ENVIRONMENTAL, 2));
        teamItems.add(enchant(new ItemStack(Material.WOODEN_SWORD), Enchantment.DAMAGE_ALL, 2));
        kits.put("Equipe", new Kit("Equipe", teamItems));
    }

    private ItemStack enchant(ItemStack item, Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        return item;
    }

    public Kit getKit(String name) {
        return kits.get(name);
    }

    public void applyKit(Player player, Kit kit) {
        if (player == null || kit == null) {
            return;
        }
        player.getInventory().clear();
        for (ItemStack item : kit.getItems()) {
            player.getInventory().addItem(item.clone());
        }
    }
}
