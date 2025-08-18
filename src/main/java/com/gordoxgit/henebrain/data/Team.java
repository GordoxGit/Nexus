package com.gordoxgit.henebrain.data;

import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;

public class Team {
    private String teamName;
    private ChatColor color;
    private List<UUID> members;

    public Team(String teamName, ChatColor color, List<UUID> members) {
        this.teamName = teamName;
        this.color = color;
        this.members = members;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public void setMembers(List<UUID> members) {
        this.members = members;
    }
}

