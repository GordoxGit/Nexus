package fr.heneria.nexus.listeners;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.gui.SetupGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {

    private final NexusPlugin plugin;

    public GuiListener(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!(event.getInventory().getHolder() instanceof SetupGUI gui)) return;

        event.setCancelled(true);

        int slot = event.getSlot();
        String mapId = gui.getMapId();
        Location loc = player.getLocation();

        switch (slot) {
            case 11: // Blue Spawn
                gui.setBlueSpawn(loc);
                player.sendMessage(Component.text("Spawn Bleu enregistré en mémoire (N'oubliez pas de sauvegarder).", NamedTextColor.BLUE));
                break;
            case 13: // Nexus
                Location nexusLoc = loc.clone().subtract(0, 1, 0); // Block under player
                nexusLoc.setYaw(0);
                nexusLoc.setPitch(0);

                if (event.isLeftClick()) {
                    gui.setBlueNexus(nexusLoc);
                    player.sendMessage(Component.text("Nexus Bleu enregistré en mémoire (N'oubliez pas de sauvegarder).", NamedTextColor.BLUE));
                } else if (event.isRightClick()) {
                    gui.setRedNexus(nexusLoc);
                    player.sendMessage(Component.text("Nexus Rouge enregistré en mémoire (N'oubliez pas de sauvegarder).", NamedTextColor.RED));
                }
                break;
            case 15: // Red Spawn
                gui.setRedSpawn(loc);
                player.sendMessage(Component.text("Spawn Rouge enregistré en mémoire (N'oubliez pas de sauvegarder).", NamedTextColor.RED));
                break;
            case 22: // Save & Reload
                // Save individually without reloading
                if (gui.getBlueSpawn() != null) {
                    plugin.getMapManager().getMapConfig().saveMapLocation(mapId, "teams.BLUE.spawnLocation", gui.getBlueSpawn(), false);
                }
                if (gui.getRedSpawn() != null) {
                    plugin.getMapManager().getMapConfig().saveMapLocation(mapId, "teams.RED.spawnLocation", gui.getRedSpawn(), false);
                }
                if (gui.getBlueNexus() != null) {
                    plugin.getMapManager().getMapConfig().saveMapLocation(mapId, "nexus.BLUE.location", gui.getBlueNexus(), false);
                }
                if (gui.getRedNexus() != null) {
                    plugin.getMapManager().getMapConfig().saveMapLocation(mapId, "nexus.RED.location", gui.getRedNexus(), false);
                }

                // Reload once at the end
                plugin.getMapManager().getMapConfig().load();

                player.sendMessage(Component.text("Configuration sauvegardée et rechargée.", NamedTextColor.GREEN));
                player.closeInventory();
                break;
        }
    }
}
