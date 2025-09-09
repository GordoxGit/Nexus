package fr.heneria.nexus.shop.model;

import org.bukkit.Material;

/**
 * Représente un item de boutique configurable.
 */
public class ShopItem {

    private int id;
    private final String category;
    private final Material material;
    private int price;
    private boolean enabled;
    private String displayName;
    private String loreLines;

    public ShopItem(int id, String category, Material material, int price, boolean enabled, String displayName, String loreLines) {
        this.id = id;
        this.category = category;
        this.material = material;
        this.price = price;
        this.enabled = enabled;
        this.displayName = displayName;
        this.loreLines = loreLines;
    }

    public ShopItem(String category, Material material, int price, boolean enabled, String displayName, String loreLines) {
        this(0, category, material, price, enabled, displayName, loreLines);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public Material getMaterial() {
        return material;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLoreLines() {
        return loreLines;
    }

    public void setLoreLines(String loreLines) {
        this.loreLines = loreLines;
    }
}
