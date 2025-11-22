package fr.heneria.nexus.classes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

@Getter
@RequiredArgsConstructor
public abstract class NexusClass {
    private final String name;
    private final String description;
    private final double maxHealth;
    private final float baseSpeed;

    public abstract void onEquip(Player player);
    public abstract void onAbility(Player player);
    public abstract void onPassive();
}
