package fr.heneria.nexus.game.kit.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Représente un kit d'équipement de base pour un joueur.
 */
public class Kit {

    private final String name;
    private final List<ItemStack> items;

    public Kit(String name, List<ItemStack> items) {
        this.name = name;
        this.items = new ArrayList<>(items);
    }

    public String getName() {
        return name;
    }

    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }
}
