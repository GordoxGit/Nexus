package fr.heneria.nexus.classes;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClassManager {
    private final Map<UUID, NexusClass> playerClasses = new HashMap<>();
    private final Map<String, NexusClass> availableClasses = new HashMap<>();

    public ClassManager() {
        registerClass(new Vanguard());
    }

    private void registerClass(NexusClass nexusClass) {
        availableClasses.put(nexusClass.getName().toLowerCase(), nexusClass);
    }

    public void equipClass(Player player, String className) {
        NexusClass nexusClass = availableClasses.get(className.toLowerCase());
        if (nexusClass != null) {
            playerClasses.put(player.getUniqueId(), nexusClass);
            nexusClass.onEquip(player);
        }
    }

    public NexusClass getClass(Player player) {
        return playerClasses.get(player.getUniqueId());
    }

    public boolean hasClass(Player player) {
        return playerClasses.containsKey(player.getUniqueId());
    }
}
