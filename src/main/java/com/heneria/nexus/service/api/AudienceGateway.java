package com.heneria.nexus.service.api;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Gateway returning Adventure audiences backed by Paper's audience provider.
 */
public interface AudienceGateway {

    /** Returns the adventure audience associated with the provided player. */
    Optional<Audience> player(Player player);

    /** Returns the adventure audience associated with a player UUID. */
    Optional<Audience> player(UUID playerId);

    /** Returns the adventure audience representing a scoreboard team. */
    Optional<Audience> team(String teamName);

    /** Returns the adventure audience representing the entire server. */
    Audience server();

    /** Returns the console audience. */
    Audience console();

    /** Returns the adventure audience for a given world. */
    Optional<Audience> world(World world);

    /** Returns an aggregated audience from the provided audiences. */
    Audience of(Iterable<? extends Audience> audiences);
}
