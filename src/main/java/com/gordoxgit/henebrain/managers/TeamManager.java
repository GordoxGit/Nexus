package com.gordoxgit.henebrain.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.gordoxgit.henebrain.Henebrain;
import com.gordoxgit.henebrain.data.Arena;
import com.gordoxgit.henebrain.data.Team;
import com.gordoxgit.henebrain.game.Game;

/**
 * Handles team creation and balancing for games.
 */
public class TeamManager {
    private final Henebrain plugin;

    public TeamManager(Henebrain plugin) {
        this.plugin = plugin;
    }

    /**
     * Balances players across teams in a round-robin fashion.
     */
    public void balanceTeams(Game game) {
        Arena arena = plugin.getArenaManager().getArena(game.getArenaName());
        if (arena == null) {
            return;
        }

        List<Team> teams = new ArrayList<>();
        ChatColor[] colors = ChatColor.values();
        int colorIndex = 0;
        for (String name : arena.getTeamSpawns().keySet()) {
            teams.add(new Team(name, colors[colorIndex++ % colors.length], new ArrayList<>()));
        }

        List<Player> players = new ArrayList<>(game.getPlayers().values());
        for (int i = 0; i < players.size(); i++) {
            Team team = teams.get(i % teams.size());
            team.getMembers().add(players.get(i).getUniqueId());
        }

        game.setTeams(teams);
    }
}

