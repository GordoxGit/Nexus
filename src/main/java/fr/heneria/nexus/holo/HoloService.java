package fr.heneria.nexus.holo;

import fr.heneria.nexus.NexusPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.*;

public class HoloService {

    private final NexusPlugin plugin;
    private final Map<UUID, TextDisplay> holograms = new HashMap<>();
    private final Map<UUID, List<Component>> hologramLines = new HashMap<>();

    public HoloService(NexusPlugin plugin) {
        this.plugin = plugin;
    }

    public UUID createHologram(Location location, List<Component> lines) {
        List<Component> linesCopy = new ArrayList<>(lines);
        Component content = buildContent(linesCopy);

        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(content);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0)); // Semi-transparent dark background
            entity.setShadowed(true);
            entity.setPersistent(false); // Don't save to disk
            entity.setViewRange(100.0f);
            entity.setSeeThrough(true);
        });

        holograms.put(display.getUniqueId(), display);
        hologramLines.put(display.getUniqueId(), linesCopy);
        return display.getUniqueId();
    }

    public void updateLine(UUID holoId, int lineIndex, Component text) {
        if (!holograms.containsKey(holoId) || !hologramLines.containsKey(holoId)) {
            return;
        }

        List<Component> lines = hologramLines.get(holoId);
        if (lineIndex >= 0 && lineIndex < lines.size()) {
            lines.set(lineIndex, text);
            TextDisplay display = holograms.get(holoId);
            if (display != null && display.isValid()) {
                display.text(buildContent(lines));
            } else {
                // Cleanup if entity is gone
                removeHologram(holoId);
            }
        }
    }

    public void removeHologram(UUID holoId) {
        TextDisplay display = holograms.remove(holoId);
        hologramLines.remove(holoId);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    public void removeAll() {
        for (TextDisplay display : holograms.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        holograms.clear();
        hologramLines.clear();
    }

    private Component buildContent(List<Component> lines) {
        Component content = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            content = content.append(lines.get(i));
            if (i < lines.size() - 1) {
                content = content.append(Component.newline());
            }
        }
        return content;
    }
}
