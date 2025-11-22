package fr.heneria.nexus.classes;

import fr.heneria.nexus.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Vanguard extends NexusClass {

    public Vanguard() {
        super("Vanguard", "Tank", 26.0, 0.2f);
    }

    @Override
    public void onEquip(Player player) {
        // Set Health
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(getMaxHealth());
        player.setHealth(getMaxHealth());

        // Set Speed (default walk speed is 0.2)
        player.setWalkSpeed(getBaseSpeed());

        // Clear Inventory
        player.getInventory().clear();

        // Set Equipment
        player.getInventory().addItem(new ItemBuilder(Material.IRON_SWORD).build());
        player.getInventory().setHelmet(new ItemBuilder(Material.IRON_HELMET).build());
        player.getInventory().setChestplate(new ItemBuilder(Material.DIAMOND_CHESTPLATE).build());
        player.getInventory().setLeggings(new ItemBuilder(Material.IRON_LEGGINGS).build());
        player.getInventory().setBoots(new ItemBuilder(Material.IRON_BOOTS).build());

        player.sendMessage(Component.text("You have equipped the Vanguard class.", NamedTextColor.GREEN));
    }

    @Override
    public void onAbility(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.0f);
        player.sendMessage(Component.text("Dome activated", NamedTextColor.BLUE));
    }

    @Override
    public void onPassive() {
        // Passive logic
    }
}
