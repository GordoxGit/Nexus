package fr.heneria.nexus.game.kit.model;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * Représente un kit d'équipement de base pour un joueur.
 */
public class Kit {

    private final String name;
    private ItemStack[] contents;

    public Kit(String name, ItemStack[] contents) {
        this.name = name;
        this.contents = contents != null ? Arrays.copyOf(contents, contents.length) : new ItemStack[0];
    }

    public String getName() {
        return name;
    }

    public ItemStack[] getContents() {
        return Arrays.copyOf(contents, contents.length);
    }

    public void setContents(ItemStack[] contents) {
        this.contents = Arrays.copyOf(contents, contents.length);
    }
}
