package fr.heneria.nexus.game.kit.manager;

import fr.heneria.nexus.game.kit.model.Kit;
import fr.heneria.nexus.game.kit.repository.KitRepository;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les kits de départ disponibles.
 */
public class KitManager {

    private static KitManager instance;

    private final KitRepository repository;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();

    private KitManager(KitRepository repository) {
        this.repository = repository;
    }

    public static void init(KitRepository repository) {
        instance = new KitManager(repository);
    }

    public static KitManager getInstance() {
        return instance;
    }

    public Map<String, Kit> getAllKits() {
        return Collections.unmodifiableMap(kits);
    }

    /**
     * Charge les kits depuis la base de données. Crée des kits par défaut si aucun n'existe.
     */
    public void loadKits() {
        kits.clear();
        kits.putAll(repository.loadAllKits());
        if (kits.isEmpty()) {
            createDefaultKits();
        }
    }

    private void createDefaultKits() {
        ItemStack[] solo = new ItemStack[41];
        solo[36] = enchant(new ItemStack(Material.LEATHER_HELMET), Enchantment.PROTECTION, 2);
        solo[37] = enchant(new ItemStack(Material.IRON_CHESTPLATE), Enchantment.PROTECTION, 2);
        solo[38] = enchant(new ItemStack(Material.IRON_LEGGINGS), Enchantment.PROTECTION, 2);
        solo[39] = enchant(new ItemStack(Material.LEATHER_BOOTS), Enchantment.PROTECTION, 2);
        solo[0] = enchant(new ItemStack(Material.STONE_SWORD), Enchantment.SHARPNESS, 2);
        solo[1] = new ItemStack(Material.BOW);
        saveKit(new Kit("Solo", solo));

        ItemStack[] team = new ItemStack[41];
        team[36] = enchant(new ItemStack(Material.LEATHER_HELMET), Enchantment.PROTECTION, 2);
        team[37] = enchant(new ItemStack(Material.LEATHER_CHESTPLATE), Enchantment.PROTECTION, 2);
        team[38] = enchant(new ItemStack(Material.LEATHER_LEGGINGS), Enchantment.PROTECTION, 2);
        team[39] = enchant(new ItemStack(Material.LEATHER_BOOTS), Enchantment.PROTECTION, 2);
        team[0] = enchant(new ItemStack(Material.WOODEN_SWORD), Enchantment.SHARPNESS, 2);
        saveKit(new Kit("Equipe", team));
    }

    private ItemStack enchant(ItemStack item, Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Kit getKit(String name) {
        return kits.get(name);
    }

    public void saveKit(Kit kit) {
        kits.put(kit.getName(), kit);
        repository.saveKit(kit);
    }

    public void deleteKit(String kitName) {
        kits.remove(kitName);
        repository.deleteKit(kitName);
    }

    public void applyKit(Player player, Kit kit) {
        if (player == null || kit == null) {
            return;
        }
        ItemStack[] contents = cloneContents(kit.getContents());
        player.getInventory().setContents(contents);
    }

    private ItemStack[] cloneContents(ItemStack[] original) {
        ItemStack[] clone = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                clone[i] = original[i].clone();
            }
        }
        return clone;
    }
}
