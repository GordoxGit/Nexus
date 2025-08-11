package com.example.hikabrain;

import org.bukkit.Location;
import org.bukkit.World;

public class Cuboid {
    private final World world;
    private final int x1, y1, z1, x2, y2, z2;

    public Cuboid(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null)
            throw new IllegalArgumentException("Cuboid corners must be valid");
        if (!a.getWorld().equals(b.getWorld()))
            throw new IllegalArgumentException("Corners must be in the same world");
        world = a.getWorld();
        x1 = Math.min(a.getBlockX(), b.getBlockX());
        y1 = Math.min(a.getBlockY(), b.getBlockY());
        z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        x2 = Math.max(a.getBlockX(), b.getBlockX());
        y2 = Math.max(a.getBlockY(), b.getBlockY());
        z2 = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    public boolean contains(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().equals(world)) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public World world() { return world; }
    public int x1() { return x1; } public int y1() { return y1; } public int z1() { return z1; }
    public int x2() { return x2; } public int y2() { return y2; } public int z2() { return z2; }
}
