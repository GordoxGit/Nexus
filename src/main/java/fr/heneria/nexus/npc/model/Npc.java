package fr.heneria.nexus.npc.model;

import org.bukkit.Location;

/**
 * Represents an interactive NPC used in the lobby to open game mode selectors.
 */
public class Npc {

    private int id;
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String clickCommand;

    public Npc(int id, String name, String world, double x, double y, double z, float yaw, float pitch, String clickCommand) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.clickCommand = clickCommand;
    }

    public Npc(String name, String clickCommand) {
        this(0, name, null, 0, 0, 0, 0, 0, clickCommand);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public String getClickCommand() {
        return clickCommand;
    }

    public void setClickCommand(String clickCommand) {
        this.clickCommand = clickCommand;
    }

    public Location toLocation() {
        if (world == null) return null;
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public void setLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }
}

