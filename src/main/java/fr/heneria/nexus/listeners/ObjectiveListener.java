package fr.heneria.nexus.listeners;

import fr.heneria.nexus.NexusPlugin;
import fr.heneria.nexus.game.GameState;
import fr.heneria.nexus.game.objective.NexusCore;
import fr.heneria.nexus.game.objective.ObjectiveManager;
import fr.heneria.nexus.game.team.GameTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ObjectiveListener implements Listener {

    private final NexusPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 500; // 0.5 seconds

    public ObjectiveListener(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    // --- CARRIER CONSTRAINTS ---

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getPreviousSlot());

        if (ObjectiveManager.isCellItem(held)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Vous ne pouvez pas changer d'item en portant la Cellule !", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (event.getCurrentItem() == null) return;

        if (ObjectiveManager.isCellItem(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                 player.sendMessage(Component.text("Vous ne pouvez pas déplacer la Cellule !", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;

        if (ObjectiveManager.isCellItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Vous ne pouvez pas jeter la Cellule !", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;

        boolean hadCell = false;
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (ObjectiveManager.isCellItem(item)) {
                it.remove(); // Remove from drops
                hadCell = true;
            }
        }

        if (hadCell) {
            plugin.getServer().broadcast(Component.text("La Cellule a été perdue !", NamedTextColor.RED));
            plugin.getObjectiveManager().triggerCellRespawn(10); // Default 10s or from config?
            // Ideally from config but for now hardcoded 10s as fallback or get from active map?
            // Let's use 10s as per requirement "Timer de respawn".
        }
    }

    // --- INTERACTION ---

    // Handle interaction with BlockDisplay entity (it's an Entity, not a Block)
    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
         if (plugin.getGameManager().getState() != GameState.PLAYING) return;
         if (!(event.getRightClicked() instanceof org.bukkit.entity.BlockDisplay)) return;

         // Find Nexus associated with this entity?
         // We didn't store the entity UUID in NexusCore map easily.
         // But we can check location proximity.
         Location loc = event.getRightClicked().getLocation();

         NexusCore targetNexus = null;
         for (NexusCore nexus : plugin.getObjectiveManager().getNexusList()) {
             if (nexus.getLocation().getWorld().equals(loc.getWorld()) && nexus.getLocation().distanceSquared(loc) < 4) {
                 targetNexus = nexus;
                 break;
             }
         }

         if (targetNexus != null) {
             handleNexusInteract(event.getPlayer(), targetNexus);
         }
    }

    // Also handle left click attack on entity
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.BlockDisplay)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        Location loc = event.getEntity().getLocation();
        NexusCore targetNexus = null;
         for (NexusCore nexus : plugin.getObjectiveManager().getNexusList()) {
             if (nexus.getLocation().getWorld().equals(loc.getWorld()) && nexus.getLocation().distanceSquared(loc) < 4) {
                 targetNexus = nexus;
                 break;
             }
         }

         if (targetNexus != null) {
             event.setCancelled(true); // Don't damage the entity itself

             GameTeam attackerTeam = plugin.getTeamManager().getPlayerTeam(attacker);
             if (attackerTeam == null) return;
             if (targetNexus.getOwner() == attackerTeam) {
                 attacker.sendMessage(Component.text("Vous ne pouvez pas attaquer votre propre Nexus !", NamedTextColor.RED));
                 return;
             }

             long now = System.currentTimeMillis();
             if (cooldowns.containsKey(attacker.getUniqueId())) {
                 long last = cooldowns.get(attacker.getUniqueId());
                 if (now - last < COOLDOWN_MS) {
                     return;
                 }
             }

             targetNexus.damage(1.0, attacker);
             cooldowns.put(attacker.getUniqueId(), now);
         }
    }

    // Fallback for clicking the AIR/BLOCK at the location if the entity hitbox is weird
    // But BlockDisplay usually has interaction.
    // However, if we click the block *under* the nexus or around it?
    // Let's keep block interaction just in case the beacon block is still there?
    // In ObjectiveManager, I removed the block placement. So it's Air.
    // So PlayerInteractEvent will likely be Left Click Air/Block.

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
         if (plugin.getGameManager().getState() != GameState.PLAYING) return;

         // Handle Right Click Air/Block with Cell -> Check distance to Enemy Nexus?
         if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
             if (ObjectiveManager.isCellItem(event.getItem())) {
                 // Check if looking at a Nexus
                 // Raytrace? Or just simple distance check to all nexus
                 for (NexusCore nexus : plugin.getObjectiveManager().getNexusList()) {
                     if (nexus.getLocation().distanceSquared(event.getPlayer().getLocation()) < 9) { // 3 blocks
                         handleNexusInteract(event.getPlayer(), nexus);
                         return;
                     }
                 }
             }
         }
    }

    private void handleNexusInteract(Player player, NexusCore targetNexus) {
        GameTeam playerTeam = plugin.getTeamManager().getPlayerTeam(player);
        if (playerTeam == null) return;

        if (targetNexus.getOwner() == playerTeam) {
            // Cannot overload own nexus
            player.sendMessage(Component.text("Vous ne pouvez pas surcharger votre propre Nexus !", NamedTextColor.RED));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (ObjectiveManager.isCellItem(item)) {
             item.setAmount(item.getAmount() - 1);
             targetNexus.overload();
             player.sendMessage(Component.text("Nexus ennemi surchargé !", NamedTextColor.GOLD));
             plugin.getObjectiveManager().triggerCellRespawn(10);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        Location blockLoc = event.getBlock().getLocation();
        for (NexusCore nexus : plugin.getObjectiveManager().getNexusList()) {
            if (nexus.getLocation().getWorld().equals(blockLoc.getWorld()) && nexus.getLocation().distanceSquared(blockLoc) < 4) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
