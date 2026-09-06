package com.github.beng420.kung.feature.dungeon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

final class DungeonSidebarReader {
    private DungeonSidebarReader() {
    }

    static List<String> lines(Minecraft client) {
        if (client.level == null) {
            return List.of();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            lines.add(entry.display() != null ? entry.display().getString() : entry.owner());
        }
        return lines;
    }
}
