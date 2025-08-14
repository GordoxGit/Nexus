package com.example.hikabrain;

import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import com.example.hikabrain.protection.ProtectionService;

import java.util.List;

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
    private final AdminModeService admin;
    private final ProtectionService protectionService;
    public GameListener(GameManager game, AdminModeService admin, ProtectionService protectionService) {
        this.game = game;
        this.admin = admin;
        this.protectionService = protectionService;
    }
    private boolean notAllowedWorld(Player p) { return !game.isWorldAllowed(p.getWorld()); }

    private static final NamespacedKey GUI_KEY = new NamespacedKey(HikaBrainPlugin.get(), "hb_gui");
    private static final NamespacedKey CAT_KEY = new NamespacedKey(HikaBrainPlugin.get(), "hb_cat");
    private static final NamespacedKey ARENA_KEY = new NamespacedKey(HikaBrainPlugin.get(), "hb_arena");
    private boolean isAllowedWorld(World w) {
        return HikaBrainPlugin.get().isWorldAllowed(w);
    }

    private static final NamespacedKey COMPASS_KEY = new NamespacedKey(HikaBrainPlugin.get(), "hb_compass");
    private static boolean isLobbyCompass(ItemStack it) {
        if (it == null) return false;
        ItemMeta m = it.getItemMeta();
        if (m == null) return false;
        Byte b = m.getPersistentDataContainer().get(COMPASS_KEY, PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }

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
    public void onProtectionToolInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (!protectionService.isInProtectMode(player)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() != Material.SHEARS) return;
        e.setCancelled(true);
        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            protectionService.setPos1(player, clickedBlock.getLocation());
            player.sendMessage("§aPosition 1 définie !");
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            protectionService.setPos2(player, clickedBlock.getLocation());
            player.sendMessage("§aPosition 2 définie !");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedInteract(PlayerInteractEvent e) {
        Action act = e.getAction();
        if (!(act == Action.RIGHT_CLICK_BLOCK || act == Action.LEFT_CLICK_BLOCK)) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        if (!(Tag.BEDS.isTagged(clicked.getType()) || clicked.getBlockData() instanceof Bed)) return;

        EquipmentSlot hand = e.getHand();
        ItemStack item = hand == EquipmentSlot.OFF_HAND ?
                e.getPlayer().getInventory().getItemInOffHand() :
                e.getPlayer().getInventory().getItemInMainHand();
        if (admin.isEnabled(e.getPlayer()) && !isBedSelector(item)) return;

        if (isBedSelector(item)) {
            Player p = e.getPlayer();
            if (!p.hasPermission("hikabrain.admin")) { p.sendMessage(ChatColor.RED + "Permission: hikabrain.admin"); deny(e); return; }
            if (notAllowedWorld(p)) { p.sendMessage(ChatColor.RED + "Actif uniquement dans: " + HikaBrainPlugin.get().allowedWorldsPretty()); deny(e); return; }
            Block b = normalizeToBedFoot(clicked);
            if (act == Action.RIGHT_CLICK_BLOCK) {
                if (game.setBed(Team.RED, b.getLocation())) p.sendMessage(ChatColor.GREEN + "Lit ROUGE enregistré.");
            } else {
                if (game.setBed(Team.BLUE, b.getLocation())) p.sendMessage(ChatColor.GREEN + "Lit BLEU enregistré.");
            }
            deny(e);
            return;
        }

        deny(e);
    }

    private void deny(PlayerInteractEvent e) {
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (HikaBrainPlugin.get().isWorldAllowed(p.getWorld())) {
            Bukkit.getScheduler().runTaskLater(HikaBrainPlugin.get(), () -> {
                if (p.isOnline()) {
                    HikaBrainPlugin.get().lobbyService().apply(p);
                }
            }, 1L);
        } else {
            HikaBrainPlugin.get().scoreboard().hide(p);
            HikaBrainPlugin.get().tablist().remove(p);
            admin.disable(p);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (HikaBrainPlugin.get().isWorldAllowed(p.getWorld()) && (game.arena() == null || !game.arena().isActive())) {
            HikaBrainPlugin.get().lobbyService().apply(p);
        } else {
            HikaBrainPlugin.get().scoreboard().hide(p);
            HikaBrainPlugin.get().tablist().remove(p);
            admin.disable(p);
        }
    }

    @EventHandler
    public void onLobbyRespawn(PlayerRespawnEvent e) {
        if (game.arena() == null || !game.arena().isActive()) {
            if (HikaBrainPlugin.get().isWorldAllowed(e.getPlayer().getWorld())) {
                HikaBrainPlugin.get().lobbyService().apply(e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();

        if (game.isFrozen()) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                p.teleport(from);
            }
            return;
        }

        if (game.arena() == null || !game.arena().isActive()) return;
        if (notAllowedWorld(p)) return;
        World w = p.getWorld();
        if (e.getTo().getY() < w.getMinHeight() + 1) {
            p.setHealth(0.0);
            return;
        }
        Team t = game.teamOf(p);
        if (t == Team.SPECTATOR) return;
        game.checkScore(p, t, e.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Player player = (Player) e.getEntity();
        if (game.arena() != null && game.arena().isActive() && game.teamOf(player) != Team.SPECTATOR) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) return;
        if (admin.isEnabled(e.getPlayer())) return;
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
        if (e.isCancelled()) return;
        if (admin.isEnabled(e.getPlayer())) return;
        if (isBrokeSelector(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            return;
        }
        Block b = e.getBlock();
        boolean isBed = Tag.BEDS.isTagged(b.getType());
        if (isBed && !isBedSelector(e.getPlayer().getInventory().getItemInMainHand())) {
            if (game.arena() == null || !game.arena().isActive()) {
                return; // allow breaking any beds outside active games
            }
            Block foot = normalizeToBedFoot(b);
            if (game.arena().bedRed() != null && foot.getLocation().equals(game.arena().bedRed())) { e.setCancelled(true); return; }
            if (game.arena().bedBlue() != null && foot.getLocation().equals(game.arena().bedBlue())) { e.setCancelled(true); return; }
        }
        if (game.arena() != null && game.arena().isActive()
                && game.inBrokeRegion(e.getBlock().getLocation())) {
            e.setCancelled(false);
            return;
        }
        if (game.arena() == null || !game.arena().isActive()) return;
        if (notAllowedWorld(e.getPlayer())) return;
        if (!game.canBuild(e.getBlock().getLocation())) { e.setCancelled(true); return; }
        if (!game.wasPlaced(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreakInProtectedZone(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (player.isOp() || player.hasPermission("hikabrain.admin.bypass")) return;
        if (protectionService.isProtected(e.getBlock().getLocation())) {
            e.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas modifier cette zone.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlaceInProtectedZone(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (player.isOp() || player.hasPermission("hikabrain.admin.bypass")) return;
        if (protectionService.isProtected(e.getBlock().getLocation())) {
            e.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas construire ici.");
        }
    }

    @EventHandler public void onHunger(FoodLevelChangeEvent e) { e.setCancelled(true); }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        admin.disable(e.getPlayer());
        game.leave(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (game.arena() != null && game.arena().isActive() && game.teamOf(player) != Team.SPECTATOR) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setDeathMessage(null);

            // Planifier le respawn pour le prochain tick serveur pour éviter tout conflit.
            Bukkit.getScheduler().runTask(HikaBrainPlugin.get(), () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                }
            });
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (game.arena() == null || !game.arena().isActive()) return;
        Player p = e.getPlayer();
        Team t = game.teamOf(p);
        if (t == Team.RED && game.arena().spawnRed() != null) e.setRespawnLocation(game.arena().spawnRed());
        else if (t == Team.BLUE && game.arena().spawnBlue() != null) e.setRespawnLocation(game.arena().spawnBlue());
        Bukkit.getScheduler().runTaskLater(HikaBrainPlugin.get(), () -> game.restock(p, t), 1L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemDrop(PlayerDropItemEvent e) {
        Player player = e.getPlayer();

        if (game.arena() != null && game.arena().isActive() && game.teamOf(player) != Team.SPECTATOR) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompassClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof com.example.hikabrain.ui.compass.CompassGuiService.Holder) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (e.isShiftClick() || e.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP
                    || e.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) return;
            if (!e.isLeftClick()) return;
            ItemStack it = e.getCurrentItem();
            if (it == null) return;
            ItemMeta m = it.getItemMeta();
            if (m == null) return;
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            String gui = pdc.get(GUI_KEY, PersistentDataType.STRING);
            Player p = (Player) e.getWhoClicked();
            if (gui == null) return;
            switch (gui) {
                case "mode":
                    Integer cat = pdc.get(CAT_KEY, PersistentDataType.INTEGER);
                    if (cat != null) HikaBrainPlugin.get().compassGui().openArenaList(p, cat);
                    break;
                case "list":
                    String arena = pdc.get(ARENA_KEY, PersistentDataType.STRING);
                    if (arena != null) HikaBrainPlugin.get().compassGui().attemptJoin(p, arena);
                    break;
                case "back":
                    HikaBrainPlugin.get().compassGui().openModeMenu(p);
                    break;
                case "close":
                    p.closeInventory();
                    break;
                case "leave":
                    HikaBrainPlugin.get().game().leave(p);
                    break;
            }
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if (admin.isEnabled(p)) return;
        if (!isAllowedWorld(p.getWorld())) return;
        if (game.arena() != null && game.arena().isActive()) return;
        if (isLobbyCompass(e.getCurrentItem()) || isLobbyCompass(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompassDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof com.example.hikabrain.ui.compass.CompassGuiService.Holder) {
            e.setCancelled(true);
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if (admin.isEnabled(p)) return;
        if (!isAllowedWorld(p.getWorld())) return;
        if (game.arena() != null && game.arena().isActive()) return;
        if (isLobbyCompass(e.getOldCursor())) { e.setCancelled(true); return; }
        for (ItemStack it : e.getNewItems().values()) {
            if (isLobbyCompass(it)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof com.example.hikabrain.ui.compass.CompassGuiService.Holder) {
            HikaBrainPlugin.get().compassGui().cancelUpdateTask((Player) e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeaveItemInteract(PlayerInteractEvent e) {
        // S'assurer qu'on gère bien les deux types de clics droits
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (e.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BARRIER) return;

        // Vérifier si c'est bien notre item spécifique via son tag persistant
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(new NamespacedKey(HikaBrainPlugin.get(), "hb_leave_item"), PersistentDataType.BYTE)) {
            return;
        }

        // ÉTAPE CRUCIALE : Annuler l'événement pour empêcher le placement du bloc
        e.setCancelled(true);

        // Exécuter l'action de quitter la file
        game.leave(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCompassInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!isAllowedWorld(p.getWorld())) return;
        if (!isLobbyCompass(e.getItem())) return;
        // Cancel to prevent default item behaviour (e.g. compass sound).
        e.setCancelled(true);
        HikaBrainPlugin.get().compassGui().openModeMenu(p);
        long tick = System.currentTimeMillis();
        p.setMetadata("hb_menu_click", new FixedMetadataValue(HikaBrainPlugin.get(), tick));
        Bukkit.getScheduler().runTaskLater(HikaBrainPlugin.get(), () -> p.removeMetadata("hb_menu_click", HikaBrainPlugin.get()), 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompassTeleport(PlayerTeleportEvent e) {
        List<MetadataValue> list = e.getPlayer().getMetadata("hb_menu_click");
        if (list.isEmpty()) return;
        long tick = System.currentTimeMillis();
        long last = list.get(0).asLong();
        if (tick - last < 250) {
            PlayerTeleportEvent.TeleportCause c = e.getCause();
            if (c == PlayerTeleportEvent.TeleportCause.UNKNOWN || c == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent e) {
        Player p = e.getPlayer();
        if (admin.isEnabled(p)) return;
        if (game.arena() == null || !game.arena().isActive()) return;
        if (notAllowedWorld(p)) return;
        if (isBedSelector(p.getInventory().getItemInMainHand()) || isBedSelector(p.getInventory().getItemInOffHand())) {
            e.setUseBed(Event.Result.DENY);
            e.setCancelled(true);
            return;
        }
        Block foot = normalizeToBedFoot(e.getBed());
        if (game.arena().bedRed() != null && foot.getLocation().equals(game.arena().bedRed())) {
            e.setUseBed(Event.Result.DENY);
            e.setCancelled(true);
            return;
        }
        if (game.arena().bedBlue() != null && foot.getLocation().equals(game.arena().bedBlue())) {
            e.setUseBed(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractSetBroke(PlayerInteractEvent e) {
        EquipmentSlot hand = e.getHand();
        ItemStack item = hand == EquipmentSlot.OFF_HAND ?
                e.getPlayer().getInventory().getItemInOffHand() :
                e.getPlayer().getInventory().getItemInMainHand();
        if (!isBrokeSelector(item)) return;
        Action act = e.getAction();
        if (!(act == Action.LEFT_CLICK_BLOCK || act == Action.RIGHT_CLICK_BLOCK)) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        if (!ensureWorldForBroke(b.getWorld(), p)) return;
        if (act == Action.LEFT_CLICK_BLOCK) {
            game.saveBrokePoint("broke.pos1", b.getLocation());
            p.sendMessage(ChatColor.YELLOW + "setbroke pos1: " + b.getX() + "," + b.getY() + "," + b.getZ());
        } else {
            game.saveBrokePoint("broke.pos2", b.getLocation());
            p.sendMessage(ChatColor.YELLOW + "setbroke pos2: " + b.getX() + "," + b.getY() + "," + b.getZ());
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
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
