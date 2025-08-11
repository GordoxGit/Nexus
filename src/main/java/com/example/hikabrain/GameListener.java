package com.example.hikabrain;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Tag;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class GameListener implements Listener {
/* ---- SetBroke tool ---- */
    public static org.bukkit.inventory.ItemStack brokeSelectorItem() {
        org.bukkit.inventory.ItemStack shovel = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WOODEN_SHOVEL);
        org.bukkit.inventory.meta.ItemMeta meta = shovel.getItemMeta();
        if (meta == null) return shovel;
        meta.setDisplayName(org.bukkit.ChatColor.YELLOW + "setbroke");
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(HikaBrainPlugin.get(), "hb_setbroke"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
        shovel.setItemMeta(meta);
        return shovel;
    }
    private static boolean isBrokeSelector(org.bukkit.inventory.ItemStack it) {
        if (it == null) return false;
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        if (m == null) return false;
        java.lang.Byte b = m.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(HikaBrainPlugin.get(), "hb_setbroke"),
                org.bukkit.persistence.PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }


    private final GameManager game;
    public GameListener(GameManager game) { this.game = game; }
    private boolean notAllowedWorld(Player p) { return !game.isWorldAllowed(p.getWorld()); }

    public static ItemStack bedSelectorItem() {
        ItemStack hoe = new ItemStack(Material.STONE_HOE);
        ItemMeta meta = hoe.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Set" + ChatColor.BLUE + "Bed");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(HikaBrainPlugin.get(), "hb_setbed"), PersistentDataType.BYTE, (byte)1);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "Clic droit: lit ROUGE");
        lore.add(ChatColor.GRAY + "Clic gauche: lit BLEU");
        meta.setLore(lore);
        hoe.setItemMeta(meta);
        return hoe;
    }
    private static boolean isBedSelector(ItemStack it) {
        if (it == null) return false;
        ItemMeta m = it.getItemMeta();
        if (m == null) return false;
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        Byte b = pdc.get(new NamespacedKey(HikaBrainPlugin.get(), "hb_setbed"), PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }
    private Block normalizeToBedFoot(Block b) {
        if (b.getBlockData() instanceof Bed) {
            Bed data = (Bed) b.getBlockData();
            if (data.getPart() == Bed.Part.HEAD) {
                return b.getLocation().subtract(data.getFacing().getModX(), 0, data.getFacing().getModZ()).getBlock();
            }
        }
        return b;
    }
    private Block bedHeadFromFoot(Block foot) {
        if (!(foot.getBlockData() instanceof Bed)) return foot;
        Bed data = (Bed) foot.getBlockData();
        return foot.getLocation().add(data.getFacing().getModX(), 0, data.getFacing().getModZ()).getBlock();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getItem() == null || !isBedSelector(e.getItem())) return;
        if (!(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK)) return;
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("hikabrain.admin")) { p.sendMessage(ChatColor.RED + "Permission: hikabrain.admin"); return; }
        if (notAllowedWorld(p)) { p.sendMessage(ChatColor.RED + "Actif uniquement dans: " + HikaBrainPlugin.get().allowedWorldsPretty()); return; }

        Block b = e.getClickedBlock();
        if (!b.getType().name().endsWith("_BED")) { p.sendMessage(ChatColor.RED + "Clique un lit."); return; }
        b = normalizeToBedFoot(b);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (game.setBed(Team.RED, b.getLocation())) p.sendMessage(ChatColor.GREEN + "Lit ROUGE enregistré.");
        } else {
            if (game.setBed(Team.BLUE, b.getLocation())) p.sendMessage(ChatColor.GREEN + "Lit BLEU enregistré.");
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || game.arena() == null || !game.arena().isActive()) return;
        if (notAllowedWorld(e.getPlayer())) return;
        if (game.isFrozen()) { e.setTo(e.getFrom()); return; }
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (e.getTo().getY() < w.getMinHeight()+1) {
            p.setHealth(0.0);
            return;
        }
        Team t = game.teamOf(p);
        if (t == Team.SPECTATOR) return;
        game.checkScore(p, t, e.getTo());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (game.arena() == null || !game.arena().isActive()) return;
        if (notAllowedWorld(e.getPlayer())) return;
        if (!game.canBuild(e.getBlock().getLocation())) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas construire ici."); return; }
        if (e.getBlock().getType() != Material.SANDSTONE) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Blocs autorisés: grès seulement."); return; }

        // Forbid placing ON or one block ABOVE selected beds (both foot and head tiles)
        Block placed = e.getBlock();
        if (game.arena().bedRed() != null) {
            Block foot = game.arena().bedRed().getBlock();
            Block head = bedHeadFromFoot(foot);
            if (sameColumn(placed, foot) && (placed.getY()==foot.getY() || placed.getY()==foot.getY()+1)) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas construire sur le lit ROUGE."); return; }
            if (sameColumn(placed, head) && (placed.getY()==head.getY() || placed.getY()==head.getY()+1)) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas construire sur le lit ROUGE."); return; }
        }
        if (game.arena().bedBlue() != null) {
            Block foot = game.arena().bedBlue().getBlock();
            Block head = bedHeadFromFoot(foot);
            if (sameColumn(placed, foot) && (placed.getY()==foot.getY() || placed.getY()==foot.getY()+1)) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas construire sur le lit BLEU."); return; }
            if (sameColumn(placed, head) && (placed.getY()==head.getY() || placed.getY()==head.getY()+1)) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas construire sur le lit BLEU."); return; }
        }

        game.trackPlaced(e.getBlock().getLocation());
        Team t = game.teamOf(e.getPlayer());
        if (t != Team.SPECTATOR) game.restock(e.getPlayer(), t);
    }

    private boolean sameColumn(Block a, Block b) { return a.getX()==b.getX() && a.getZ()==b.getZ() && a.getWorld().equals(b.getWorld()); }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (game.arena() == null || !game.arena().isActive()) return;
        if (notAllowedWorld(e.getPlayer())) return;
        if (game.inBrokeRegion(e.getBlock().getLocation())) { e.setCancelled(false); return; }
        if (!game.canBuild(e.getBlock().getLocation())) { e.setCancelled(true); return; }
        if (!game.wasPlaced(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler public void onHunger(FoodLevelChangeEvent e) { e.setCancelled(true); }

    @EventHandler public void onQuit(PlayerQuitEvent e) { game.leave(e.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDeathMessage(null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (game.arena() == null || !game.arena().isActive()) return;
        Player p = e.getPlayer();
        Team t = game.teamOf(p);
        if (t == Team.RED && game.arena().spawnRed() != null) e.setRespawnLocation(game.arena().spawnRed());
        else if (t == Team.BLUE && game.arena().spawnBlue() != null) e.setRespawnLocation(game.arena().spawnBlue());
        org.bukkit.Bukkit.getScheduler().runTaskLater(HikaBrainPlugin.get(), () -> game.restock(p, t), 1L);
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (game.arena() == null) return;
        if (notAllowedWorld(e.getPlayer())) return;
        Block foot = normalizeToBedFoot(e.getBed());
        if (game.arena().bedRed()!=null && foot.getLocation().equals(game.arena().bedRed())) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas dormir sur le lit ROUGE de l'arène."); return; }
        if (game.arena().bedBlue()!=null && foot.getLocation().equals(game.arena().bedBlue())) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.RED + "Tu ne peux pas dormir sur le lit BLEU de l'arène."); }
    }

    private boolean ensureWorldForBroke(World w, Player p) {
        if (!HikaBrainPlugin.get().isWorldAllowed(w)) {
            p.sendMessage(ChatColor.RED + "Monde non autorisé pour setbroke. Autorisés: " + HikaBrainPlugin.get().allowedWorldsPretty());
            return false;
        }
        return true;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        if (isBrokeSelector(e.getPlayer().getInventory().getItemInMainHand())) {
            // Bloque l'animation et la casse lors de la sélection
            e.setCancelled(true);
        }
    }
}
