package fr.heneria.nexus.gui;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.utils.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import org.bukkit.Location;
import lombok.Getter;
import lombok.Setter;

public class SetupGUI implements InventoryHolder {

    private final NexusPlugin plugin;
    @Getter
    private final String mapId;
    private final Inventory inventory;

    @Getter @Setter
    private Location blueSpawn;
    @Getter @Setter
    private Location redSpawn;
    @Getter @Setter
    private Location blueNexus;
    @Getter @Setter
    private Location redNexus;

    public SetupGUI(NexusPlugin plugin, String mapId) {
        this.plugin = plugin;
        this.mapId = mapId;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Setup: " + mapId));
        initializeItems();
    }

    private void initializeItems() {
        // Slot 11: Spawn Bleu
        inventory.setItem(11, new ItemBuilder(Material.BLUE_WOOL)
                .name(Component.text("Définir Spawn Bleu", NamedTextColor.BLUE))
                .lore(Component.text("Clic pour définir le spawn de l'équipe Bleue", NamedTextColor.GRAY))
                .build());

        // Slot 13: Nexus
        inventory.setItem(13, new ItemBuilder(Material.BEACON)
                .name(Component.text("Définir Nexus", NamedTextColor.AQUA))
                .lore(Component.text("Clic Gauche: Nexus Bleu", NamedTextColor.BLUE), Component.text("Clic Droit: Nexus Rouge", NamedTextColor.RED))
                .build());

        // Slot 15: Spawn Rouge
        inventory.setItem(15, new ItemBuilder(Material.RED_WOOL)
                .name(Component.text("Définir Spawn Rouge", NamedTextColor.RED))
                .lore(Component.text("Clic pour définir le spawn de l'équipe Rouge", NamedTextColor.GRAY))
                .build());

        // Slot 22: Sauvegarder
        inventory.setItem(22, new ItemBuilder(Material.EMERALD)
                .name(Component.text("Sauvegarder & Recharger", NamedTextColor.GREEN))
                .lore(Component.text("Appliquer les modifications", NamedTextColor.GRAY))
                .build());

        // Slot 24: Ajouter Cellule
        inventory.setItem(24, new ItemBuilder(Material.GOLD_BLOCK)
                .name(Component.text("Ajouter une Cellule", NamedTextColor.GOLD))
                .lore(Component.text("Clic pour ajouter une zone de capture ici", NamedTextColor.GRAY))
                .build());
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }


    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
