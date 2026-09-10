package com.github.beng420.kung.skyblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class SkyBlockSidebar {
    private SkyBlockSidebar() {}

    /** Coalesces the packets making up one sidebar update before reading its text. */
    public static final class Pending {
        private final Set<String> owners = new HashSet<>();
        private final Set<String> teams = new HashSet<>();
        private boolean dirty;

        public void score(String owner) { owners.add(owner); changed(); }
        public void team(String name, Collection<String> members, boolean hasText) {
            owners.addAll(members);
            if (hasText) teams.add(name);
            changed();
        }
        public void changed() { dirty = true; }
        public void reset() { owners.clear(); teams.clear(); dirty = false; }

        /** Null means no packets arrived; an empty list means the server removed the sidebar. */
        public List<String> poll(Scoreboard scoreboard) {
            if (!dirty) return null;
            dirty = false;
            return lines(scoreboard, owners, teams);
        }
    }

    public static List<String> lines(Scoreboard scoreboard, Set<String> freshOwners) {
        return lines(scoreboard, freshOwners, null);
    }

    public static List<String> lines(Scoreboard scoreboard, Set<String> freshOwners, Set<String> freshTeams) {
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return List.of();
        ArrayList<String> lines = new ArrayList<>();
        lines.add(sidebar.getDisplayName().getString());
        scoreboard.listPlayerScores(sidebar).stream()
            .filter(entry -> !entry.isHidden() && (freshOwners == null || freshOwners.contains(entry.owner())))
            .filter(entry -> {
                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                return team == null || freshTeams == null || freshTeams.contains(team.getName());
            })
            .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner))
            .limit(15)
            .forEach(entry -> {
                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                // Hypixel splits real text across the team and uses an invisible synthetic score owner.
                lines.add(team == null ? entry.ownerName().getString()
                    : team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
            });
        return List.copyOf(lines);
    }
}
