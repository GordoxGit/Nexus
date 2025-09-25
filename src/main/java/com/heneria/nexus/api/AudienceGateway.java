package com.heneria.nexus.api;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Gateway returning Adventure audiences backed by Paper's audience provider.
 */
public interface AudienceGateway {

    /**
     * Returns the adventure audience associated with the provided player.
     *
     * @param player online player to resolve
     * @return optional containing the player audience when available
     */
    Optional<Audience> player(Player player);

    /**
     * Returns the adventure audience associated with a player UUID.
     *
     * @param playerId unique identifier of the player
     * @return optional containing the player audience when available
     */
    Optional<Audience> player(UUID playerId);

    /**
     * Returns the adventure audience representing a scoreboard team.
     *
     * @param teamName name of the team to resolve
     * @return optional containing the team audience when available
     */
    Optional<Audience> team(String teamName);

    /**
     * Returns the adventure audience representing the entire server.
     *
     * @return broadcast audience targeting every connected player
     */
    Audience server();

    /**
     * Returns the console audience.
     *
     * @return audience bound to the server console
     */
    Audience console();

    /**
     * Returns the adventure audience for a given world.
     *
     * @param world world whose audience should be returned
     * @return optional containing the world audience when available
     */
    Optional<Audience> world(World world);

    /**
     * Returns an aggregated audience from the provided audiences.
     *
     * @param audiences audiences to aggregate
     * @return composed audience forwarding messages to all inputs
     */
    Audience of(Iterable<? extends Audience> audiences);
}
