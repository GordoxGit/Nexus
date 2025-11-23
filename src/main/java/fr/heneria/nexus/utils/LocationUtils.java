package fr.heneria.nexus.utils;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class LocationUtils {

    public static void saveLocation(ConfigurationSection section, Location location) {
        if (section == null || location == null) return;
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }
}
