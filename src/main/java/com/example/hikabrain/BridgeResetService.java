package com.example.hikabrain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles snapshot and reset of the broke bridge region.
 */
public class BridgeResetService {

    private final HikaBrainPlugin plugin;
    private final GameManager game;

    private Location pos1, pos2;
    private String arenaName;
    private List<SavedBlock> snapshot = new ArrayList<>();

    public BridgeResetService(HikaBrainPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    /** Initialize from current config and optionally regenerate snapshot. */
    public void init(String arena) {
        this.arenaName = arena;
        this.pos1 = game.readBrokePoint("broke.pos1");
        this.pos2 = game.readBrokePoint("broke.pos2");
        if (pos1 == null || pos2 == null) return;
        if (plugin.getConfig().getBoolean("broke.reset.snapshotOnLoad", true)) snapshot();
        else loadSnapshot();
    }

    /** Force snapshot regeneration. */
    public void snapshot() {
        if (pos1 == null || pos2 == null) return;
        snapshot.clear();
        World w = pos1.getWorld();
        if (w == null || pos2.getWorld() == null || !w.equals(pos2.getWorld())) return;
        int batch = plugin.getConfig().getInt("broke.reset.batchPerTick", 1500);
        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int total = (x2-x1+1)*(y2-y1+1)*(z2-z1+1);
        long start = System.currentTimeMillis();
        plugin.getLogger().info("Snapshot broke region start (" + total + " blocs)");

        new BukkitRunnable() {
            int x=x1, z=z1, y=y1, count=0;
            @Override public void run() {
                int processed=0;
                while (processed < batch && x<=x2) {
                    Block b = w.getBlockAt(x,y,z);
                    snapshot.add(new SavedBlock(b.getType().name(), b.getBlockData().getAsString()));
                    count++; processed++;
                    y++;
                    if (y>y2) { y=y1; z++; if (z>z2) { z=z1; x++; } }
                }
                if (plugin.getConfig().getBoolean("debug", false))
                    plugin.getLogger().fine("Snapshot " + count + "/" + total);
                if (x> x2) {
                    cancel();
                    long elapsed = System.currentTimeMillis() - start;
                    plugin.getLogger().info("Snapshot broke region done in " + elapsed + " ms");
                    saveSnapshotAsync();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void saveSnapshotAsync() {
        if (arenaName == null) return;
        List<SavedBlock> data = new ArrayList<>(snapshot);
        File file = new File(plugin.getDataFolder(), "arena/" + arenaName + "/broke.snapshot");
        file.getParentFile().mkdirs();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                out.writeInt(data.size());
                for (SavedBlock sb : data) {
                    out.writeUTF(sb.material);
                    out.writeUTF(sb.data);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write broke snapshot: " + e.getMessage());
            }
        });
    }

    private void loadSnapshot() {
        if (arenaName == null) return;
        File file = new File(plugin.getDataFolder(), "arena/" + arenaName + "/broke.snapshot");
        if (!file.exists()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                int size = in.readInt();
                List<SavedBlock> tmp = new ArrayList<>(size);
                for (int i=0; i<size; i++) tmp.add(new SavedBlock(in.readUTF(), in.readUTF()));
                snapshot = tmp;
                plugin.getLogger().info("Loaded broke snapshot: " + size + " blocs");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load broke snapshot: " + e.getMessage());
            }
        });
    }

    public boolean hasSnapshot() { return !snapshot.isEmpty(); }

    public void reset(Runnable done) {
        if (pos1 == null || pos2 == null || snapshot.isEmpty()) { if (done != null) done.run(); return; }
        int batch = plugin.getConfig().getInt("broke.reset.batchPerTick", 1500);
        World w = pos1.getWorld();
        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int total = snapshot.size();
        long start = System.currentTimeMillis();
        plugin.getLogger().info("Reset broke region start (" + total + " blocs)");

        new BukkitRunnable() {
            int x=x1, z=z1, y=y1, idx=0, count=0;
            @Override public void run() {
                int processed=0;
                while (processed < batch && idx < snapshot.size()) {
                    SavedBlock sb = snapshot.get(idx++);
                    Block block = w.getBlockAt(x,y,z);
                    block.setType(Material.valueOf(sb.material), false);
                    block.setBlockData(Bukkit.createBlockData(sb.data), false);
                    count++; processed++;
                    y++;
                    if (y>y2) { y=y1; z++; if (z>z2) { z=z1; x++; } }
                }
                if (plugin.getConfig().getBoolean("debug", false))
                    plugin.getLogger().fine("Reset " + count + "/" + total);
                if (idx >= snapshot.size()) {
                    cancel();
                    if (game.arena() != null)
                        game.arena().placedBlocks().removeIf(l -> game.inBrokeRegion(l));
                    long elapsed = System.currentTimeMillis() - start;
                    plugin.getLogger().info("Reset broke region done in " + elapsed + " ms");
                    if (done != null) Bukkit.getScheduler().runTask(plugin, done);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static class SavedBlock {
        final String material;
        final String data;
        SavedBlock(String material, String data) { this.material = material; this.data = data; }
    }
}

