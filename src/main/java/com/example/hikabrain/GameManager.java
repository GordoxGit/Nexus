package com.example.hikabrain;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import com.example.hikabrain.ui.FeedbackService;
import com.example.hikabrain.ui.ThemeService;
import com.example.hikabrain.ui.UiService;
import com.example.hikabrain.ui.model.Presets;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GameManager {

    private final HikaBrainPlugin plugin;
    private Arena arena;

    private final BridgeResetService bridgeReset;
    private BukkitRunnable timerTask;
    private int timeRemaining; // seconds
    private int freezeMoveTicks = 0; // block movement & scoring during countdown

    private boolean scoredConfigWarned = false;

    private Location build1, build2;

    public GameManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.bridgeReset = new BridgeResetService(plugin, this);
    }

    public Arena arena() { return arena; }
    public boolean isWorldAllowed(World w) { return plugin.isWorldAllowed(w); }

    public UiService ui() { return plugin.ui(); }
    public ThemeService theme() { return plugin.theme(); }
    public FeedbackService fx() { return plugin.fx(); }

    public Arena createArena(String name, World w) {
        arena = new Arena(name);
        arena.worldName(w.getName());
        return arena;
    }

    public boolean setSpawn(Team t, Location l) {
        if (arena == null) return false;
        if (t == Team.RED) arena.spawnRed(l);
        else if (t == Team.BLUE) arena.spawnBlue(l);
        return true;
    }

    public boolean setBed(Team team, Location l) {
        if (arena == null) return false;
        l = normalizeToBedFoot(l.getBlock()).getLocation();
        if (team == Team.RED) arena.bedRed(l); else if (team == Team.BLUE) arena.bedBlue(l);
        return true;
    }

    public boolean setBuildCorner(int idx, Location l) {
        if (arena == null) return false;
        if (idx==1) build1 = l; else build2 = l;
        if (build1 != null && build2 != null) { arena.buildRegion(new Cuboid(build1, build2)); }
        return true;
    }

    public boolean saveArena() throws IOException {
        if (arena == null) return false;
        File dir = new File(plugin.getDataFolder(), "arenas");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, arena.name() + ".yml");
        arena.saveTo(file);
        return true;
    }

    public boolean loadArena(String name) throws IOException {
        File file = new File(new File(plugin.getDataFolder(), "arenas"), name + ".yml");
        if (!file.exists()) throw new IOException("Arena file not found: " + name);
        arena = Arena.loadFrom(file);
        bridgeReset.init(arena.name());
        String tid = plugin.getConfig().getString("arenas." + name + ".ui.theme", plugin.getConfig().getString("ui.theme", "classic"));
        plugin.theme().applyTheme(arena, tid);
        return true;
    }

    public List<String> listArenas() {
        File dir = new File(plugin.getDataFolder(), "arenas");
        if (!dir.exists()) return java.util.Collections.emptyList();
        String[] ls = dir.list((d,n) -> n.toLowerCase().endsWith(".yml"));
        java.util.List<String> out = new java.util.ArrayList<>();
        if (ls != null) for (String n : ls) out.add(n.substring(0, n.length()-4));
        return out;
    }

    public void setTargetPoints(int n) { if (arena != null) arena.targetPoints(n); }
    public void setTimeLimitMinutes(int m) { if (arena != null) arena.timeLimitMinutes(m); }
    public int timeRemaining() { return timeRemaining; }

    public void join(Player p, Team preferred) {
        if (arena == null) { p.sendMessage(ChatColor.RED + "[HB] Aucune arène chargée."); return; }
        if (!isWorldAllowed(p.getWorld())) { p.sendMessage(ChatColor.RED + "[HB] Monde non autorisé."); return; }
        if (arena.isActive()) { p.sendMessage(ChatColor.RED + "[HB] Partie en cours."); return; }
        Team t = preferred;
        if (t == null) {
            int rc = arena.players().get(Team.RED).size();
            int bc = arena.players().get(Team.BLUE).size();
            t = rc <= bc ? Team.RED : Team.BLUE;
        }
        arena.players().get(t).add(p.getUniqueId());
        p.sendMessage((t==Team.RED?ChatColor.RED:ChatColor.BLUE) + "Tu rejoins " + t.name());
        plugin.scoreboard().show(p, arena);
        plugin.scoreboard().update(arena);
        plugin.tablist().update(arena);
    }

    public void leave(Player p) {
        if (arena == null) return;
        for (Set<UUID> s : arena.players().values()) s.remove(p.getUniqueId());
        plugin.scoreboard().hide(p);
        plugin.tablist().remove(p);
        plugin.scoreboard().update(arena);
        plugin.tablist().update(arena);
        p.sendMessage(ChatColor.GRAY + "Tu as quitté la partie.");
    }

    public boolean isFrozen() { return freezeMoveTicks > 0; }

    private void startFreezeCountdown() {
        freezeMoveTicks = 3*20;
        new BukkitRunnable(){ @Override public void run(){
            if (freezeMoveTicks <= 0 || arena == null || !arena.isActive()) { cancel(); return; }
            freezeMoveTicks--;
        }}.runTaskTimer(plugin, 1L, 1L);
        plugin.ui().showIntroCountdown(arena, 3);
    }

    public void start() {
        if (arena == null || !arena.isConfigured()) {
            Bukkit.broadcastMessage(ChatColor.RED + "[HB] Arène non prête, configure tout puis /hb save.");
            return;
        }
        arena.setActive(true);
        arena.redScore(0); arena.blueScore(0);
        timeRemaining = arena.timeLimitMinutes() * 60;
        plugin.scoreboard().update(arena);
        plugin.tablist().update(arena);

        for (UUID u : arena.players().get(Team.RED)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) { tp(p, arena.spawnRed()); giveKit(p, Team.RED); plugin.scoreboard().show(p, arena); }
        }
        for (UUID u : arena.players().get(Team.BLUE)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) { tp(p, arena.spawnBlue()); giveKit(p, Team.BLUE); plugin.scoreboard().show(p, arena); }
        }

        new BukkitRunnable(){ @Override public void run(){
            if (arena==null || !arena.isActive()) { cancel(); return; }
            for (UUID u: arena.players().get(Team.RED)) { Player p=Bukkit.getPlayer(u); if (p!=null) restock(p, Team.RED); }
            for (UUID u: arena.players().get(Team.BLUE)) { Player p=Bukkit.getPlayer(u); if (p!=null) restock(p, Team.BLUE); }
        }}.runTaskTimer(plugin, 20L, 20L);

        timerTask = new BukkitRunnable() {
            @Override public void run() {
                if (arena == null || !arena.isActive()) { cancel(); return; }
                timeRemaining--; if (timeRemaining < 0) { endByTime(); cancel(); return; }
                plugin.scoreboard().update(arena);
                plugin.tablist().update(arena);
            }
        };
        timerTask.runTaskTimer(plugin, 20L, 20L);

        startFreezeCountdown();
        Bukkit.broadcastMessage(ChatColor.GREEN + "[HB] Partie lancée ! Objectif: " + arena.targetPoints() + " points.");
    }

    private void broadcastTitle(String title, String sub, int fadeIn, int stay, int fadeOut){
        for (UUID u : arena.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendTitle(title, sub, fadeIn, stay, fadeOut);
        }
        for (UUID u : arena.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendTitle(title, sub, fadeIn, stay, fadeOut);
        }
        for (UUID u : arena.players().getOrDefault(Team.SPECTATOR, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendTitle(title, sub, fadeIn, stay, fadeOut);
        }
    }

    private void broadcastSound(Sound s, float vol, float pitch){
        for (UUID u : arena.players().getOrDefault(Team.RED, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) p.playSound(p.getLocation(), s, vol, pitch);
        }
        for (UUID u : arena.players().getOrDefault(Team.BLUE, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) p.playSound(p.getLocation(), s, vol, pitch);
        }
        for (UUID u : arena.players().getOrDefault(Team.SPECTATOR, java.util.Collections.emptySet())) {
            Player p = Bukkit.getPlayer(u); if (p != null) p.playSound(p.getLocation(), s, vol, pitch);
        }
    }

    private void endByTime() {
        arena.setActive(false);
        int r = arena.redScore(), b = arena.blueScore();
        if (r == b) Bukkit.broadcastMessage(ChatColor.YELLOW + "[HB] Temps écoulé : Égalité " + r + "-" + b);
        else if (r > b) Bukkit.broadcastMessage(ChatColor.RED + "[HB] Victoire Rouge " + r + "-" + b);
        else Bukkit.broadcastMessage(ChatColor.BLUE + "[HB] Victoire Bleue " + b + "-" + r);
        endCleanup();
    }

    public void stop(boolean announce) {
        if (arena == null || !arena.isActive()) return;
        arena.setActive(false);
        if (timerTask != null) timerTask.cancel();
        if (announce) endByTime(); else endCleanup();
    }

    private void endCleanup() {
        flushPlacedBlocks();
        plugin.scoreboard().clear();
    }

    private void tp(Player p, Location l) { if (l != null) p.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN); }

    private Enchantment ench(String key, String legacy) {
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (e == null) e = Enchantment.getByName(legacy);
        return e;
    }
    private void addEnchantSafe(ItemStack item, String key, String legacy, int level) {
        Enchantment e = ench(key, legacy);
        if (e != null) item.addUnsafeEnchantment(e, level);
    }

    private void giveKit(Player p, Team t) {
        PlayerInventory inv = p.getInventory();
        inv.clear();

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        addEnchantSafe(sword, "sharpness", "DAMAGE_ALL", 1);
        inv.setItem(0, sword);

        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        addEnchantSafe(pick, "efficiency", "DIG_SPEED", 2);
        inv.setItem(1, pick);

        inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 64));

        inv.setItemInOffHand(new ItemStack(Material.SANDSTONE, 64));

        Color c = (t==Team.RED) ? Color.RED : Color.BLUE;
        ItemStack helm = new ItemStack(Material.LEATHER_HELMET);
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        dye(helm, c); dye(chest, c); dye(legs, c); dye(boots, c);
        inv.setHelmet(helm); inv.setChestplate(chest); inv.setLeggings(legs); inv.setBoots(boots);

        p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20);
    }

    private void dye(ItemStack item, Color c) {
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta m = (LeatherArmorMeta) item.getItemMeta();
            m.setColor(c); item.setItemMeta(m);
        }
    }

    public void restock(Player p, Team t) {
        PlayerInventory inv = p.getInventory();
        inv.setItemInOffHand(new ItemStack(Material.SANDSTONE, 64));
        ItemStack apples = inv.getItem(2);
        if (apples == null || apples.getType() != Material.GOLDEN_APPLE) {
            inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 64));
        } else {
            apples.setAmount(64);
            inv.setItem(2, apples);
        }
    }

    public Team teamOf(Player p) {
        UUID u = p.getUniqueId();
        if (arena.players().get(Team.RED).contains(u)) return Team.RED;
        if (arena.players().get(Team.BLUE).contains(u)) return Team.BLUE;
        return Team.SPECTATOR;
    }

    public int playersInArena(Arena a) {
        if (a == null || a.buildRegion() == null) return 0;
        Cuboid c = a.buildRegion();
        World w = a.world();
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(w))
                .filter(p -> teamOf(p) != Team.SPECTATOR)
                .filter(p -> c.contains(p.getLocation()))
                .count();
    }

    public int teamSize() {
        if (arena == null) return 0;
        return Math.max(arena.players().get(Team.RED).size(), arena.players().get(Team.BLUE).size());
    }

    public boolean canBuild(Location l) { return arena != null && arena.buildRegion() != null && arena.buildRegion().contains(l); }

    public void trackPlaced(Location l) { arena.placedBlocks().add(l.getBlock().getLocation()); }
    public boolean wasPlaced(Location l) { return arena.placedBlocks().contains(l.getBlock().getLocation()); }
    public void flushPlacedBlocks() {
        for (Location l : arena.placedBlocks()) if (l.getBlock().getType() != Material.AIR) l.getBlock().setType(Material.AIR, false);
        arena.placedBlocks().clear();
    }

    // --- scoring helpers
    private Block normalizeToBedFoot(Block b) {
        if (b.getBlockData() instanceof Bed) {
            Bed data = (Bed) b.getBlockData();
            if (data.getPart() == Bed.Part.HEAD) {
                return b.getLocation().subtract(data.getFacing().getModX(), 0, data.getFacing().getModZ()).getBlock();
            }
        }
        return b;
    }
    private Location bedHeadFromFoot(Location foot) {
        Block fb = foot.getBlock();
        if (!(fb.getBlockData() instanceof Bed)) return foot; // fallback
        Bed data = (Bed) fb.getBlockData();
        return foot.clone().add(data.getFacing().getModX(), 0, data.getFacing().getModZ());
    }
    private boolean isOnBedTiles(Location to, Location bedFoot) {
        if (bedFoot == null || to == null || bedFoot.getWorld()==null || to.getWorld()==null) return false;
        if (!to.getWorld().equals(bedFoot.getWorld())) return false;
        Location head = bedHeadFromFoot(bedFoot);
        int px = to.getBlockX(), py = to.getBlockY(), pz = to.getBlockZ();
        int fx = bedFoot.getBlockX(), fy = bedFoot.getBlockY(), fz = bedFoot.getBlockZ();
        int hx = head.getBlockX(), hz = head.getBlockZ(); int hy = head.getBlockY();
        if ((px==fx && pz==fz && (py==fy || py==fy+1)) ||
            (px==hx && pz==hz && (py==hy || py==hy+1))) return true;
        int by = py-1;
        return (px==fx && pz==fz && by==fy) || (px==hx && pz==hz && by==hy);
    }

    public void checkScore(Player p, Team t, Location to) {
        if (isFrozen()) return;
        if (t==Team.RED && arena.bedBlue()!=null) {
            if (isOnBedTiles(to, arena.bedBlue())) { goalScored(p, t); return; }
        } else if (t==Team.BLUE && arena.bedRed()!=null) {
            if (isOnBedTiles(to, arena.bedRed())) { goalScored(p, t); return; }
        }
    }

    public void goalScored(Player scorer, Team t) {
        if (arena == null || !arena.isActive()) return;
        if (isFrozen()) return;
        if (t == Team.RED) arena.redScore(arena.redScore()+1); else if (t == Team.BLUE) arena.blueScore(arena.blueScore()+1);
        plugin.scoreboard().update(arena);
        plugin.tablist().update(arena);
        plugin.fx().playTeamPreset(t, Presets.SCORE_BED);

        String teamName = (t==Team.RED?"Rouge":"Bleue");
        String msg = (t==Team.RED?ChatColor.RED:ChatColor.BLUE) + "L'équipe " + teamName + " a marqué !";
        Bukkit.broadcastMessage(msg);

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("scored");
        if (sec == null && !scoredConfigWarned) {
            plugin.getLogger().info("Missing 'scored' section in config.yml, using defaults.");
            scoredConfigWarned = true;
        }
        String title = null;
        if (sec != null) title = sec.getString(t==Team.RED?"title":"title-blue");
        if (title == null) title = t==Team.RED?"&cÉquipe ROUGE &7marque le point!":"&9Équipe BLEUE &7marque le point!";
        String subtitle = null;
        if (sec != null) subtitle = sec.getString("subtitle");
        if (subtitle == null) subtitle = "&7Score: {red} - {blue}";
        subtitle = subtitle.replace("{red}", String.valueOf(arena.redScore()))
                           .replace("{blue}", String.valueOf(arena.blueScore()));
        int fadeIn = 10, stay = 80, fadeOut = 10;
        if (sec != null) {
            ConfigurationSection times = sec.getConfigurationSection("timings");
            if (times != null) {
                fadeIn = times.getInt("fadeIn", 10);
                stay = times.getInt("stay", 80);
                fadeOut = times.getInt("fadeOut", 10);
            }
        }
        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);
        broadcastTitle(title, subtitle, fadeIn, stay, fadeOut);
        broadcastSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);

        flushPlacedBlocks();
        for (UUID u : arena.players().get(Team.RED)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) { tp(p, arena.spawnRed()); giveKit(p, Team.RED); }
        }
        for (UUID u : arena.players().get(Team.BLUE)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) { tp(p, arena.spawnBlue()); giveKit(p, Team.BLUE); }
        }
        setFrozen(true);
        int delay = fadeIn + stay;
        bridgeReset.reset(() -> {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                setFrozen(false);
                if (arena.redScore() >= arena.targetPoints() || arena.blueScore() >= arena.targetPoints()) {
                    Bukkit.broadcastMessage((arena.redScore() > arena.blueScore()?ChatColor.RED:ChatColor.BLUE) + "[HB] Victoire !");
                    stop(true);
                } else {
                    startFreezeCountdown();
                }
            }, delay);
        });
    }

    public void shutdown() { if (timerTask != null) timerTask.cancel(); }

    /* ---- setbroke storage ---- */
    public void saveBrokePoint(String path, org.bukkit.Location l) {
        if (l == null || l.getWorld() == null) return;
        plugin.getConfig().set(path + ".world", l.getWorld().getName());
        plugin.getConfig().set(path + ".x", l.getBlockX());
        plugin.getConfig().set(path + ".y", l.getBlockY());
        plugin.getConfig().set(path + ".z", l.getBlockZ());
        plugin.saveConfig();
        if (plugin.getConfig().getBoolean("broke.reset.snapshotOnLoad", true)) {
            bridgeReset.init(arena != null ? arena.name() : null);
        }
    }
    public org.bukkit.Location readBrokePoint(String path) {
        org.bukkit.configuration.file.FileConfiguration c = plugin.getConfig();
        if (!c.isSet(path + ".world")) return null;
        String w = c.getString(path + ".world");
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(w);
        if (world == null) return null;
        return new org.bukkit.Location(world, c.getInt(path + ".x"), c.getInt(path + ".y"), c.getInt(path + ".z"));
    }
    public boolean inBrokeRegion(org.bukkit.Location l) {
        org.bukkit.Location a = readBrokePoint("broke.pos1");
        org.bukkit.Location b = readBrokePoint("broke.pos2");
        if (a == null || b == null || l == null || l.getWorld() == null) return false;
        if (!a.getWorld().equals(l.getWorld()) || !b.getWorld().equals(l.getWorld())) return false;
        int x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX());
        int y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY());
        int z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ());
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x>=x1 && x<=x2 && y>=y1 && y<=y2 && z>=z1 && z<=z2;
    }

    public void snapshotBroke() { bridgeReset.snapshot(); }
    public void resetBroke() { setFrozen(true); bridgeReset.reset(() -> setFrozen(false)); }
    public void setFrozen(boolean f) { freezeMoveTicks = f ? Integer.MAX_VALUE : 0; }

}
