package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.category.SplitsConfig;
import com.github.beng420.kung.message.KungMessages;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Split notifications use local Kung system messages, independent of prediction visibility. */
final class DungeonSplitMessages {
    private DungeonSplitMessages() { }

    static void send(DungeonSplitTracker.PhaseMessage phase) {
        send(notices(phase));
    }

    static void send(List<Notice> notices) {
        Minecraft client = Minecraft.getInstance();
        for (Notice notice : notices) {
            KungMessages.send(client, notice.component());
        }
    }

    static List<Notice> summary(DungeonSplitTracker tracker, int floor, boolean masterMode, SplitsConfig config) {
        List<Notice> result = new ArrayList<>();
        String floorLabel = floor < 0 ? "" : floor == 0 ? "Entrance " : (masterMode ? "M" : "F") + floor + " ";
        result.add(new Notice(KungMessages.Type.INFO, floorLabel + "Run Splits (server time in parentheses)"));
        for (String name : tracker.splitNames()) {
            var split = DungeonSplitsOverlayFeature.phaseSnapshot(tracker, name);
            String label = split != null && split == tracker.stoppedCurrentSplit() ? name + " (unfinished)" : name;
            result.add(summaryTime(label, split == null ? -1L : split.splitDurationMillis(),
                split == null ? -1L : split.serverSplitDurationMillis(), config));
        }
        var portal = tracker.completedSplits().stream().filter(split -> split.name().equals("Portal Entry"))
            .findFirst().orElse(null);
        result.add(summaryTime("Boss Entry", portal == null ? -1L : portal.totalDurationMillis(),
            portal == null ? -1L : portal.serverTotalDurationMillis(), config));
        result.add(summaryTime("Total", tracker.currentTotalDurationMillis(), tracker.currentTotalServerDurationMillis(), config));
        if (config.timeLost()) {
            result.add(new Notice(KungMessages.Type.INFO, "Time Lost: " + DungeonSplitsOverlayFeature.formatLostTimeMillis(
                DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker))));
        }
        return List.copyOf(result);
    }

    private static Notice summaryTime(String label, long wallMillis, long serverMillis, SplitsConfig config) {
        return new Notice(KungMessages.Type.INFO, label + ": "
            + DungeonSplitsOverlayFeature.formatDurationMillis(wallMillis, config.format()) + " ("
            + DungeonSplitsOverlayFeature.formatDurationMillis(serverMillis, config.format()) + ")");
    }

    static List<Notice> notices(DungeonSplitTracker.PhaseMessage phase) {
        String floor = phase.floor() < 0 ? "" : phase.floor() == 0 ? "Entrance "
            : (phase.masterMode() ? "M" : "F") + phase.floor() + " ";
        String result = floor + phase.phase() + ": "
            + DungeonSplitsOverlayFeature.formatDurationMillis(phase.durationMillis(), phase.format());
        long best = phase.personalBest() ? phase.durationMillis() : phase.previousBestMillis();
        Notice time = new Notice(KungMessages.Type.INFO, result + " (PB: "
            + DungeonSplitsOverlayFeature.formatDurationMillis(best, phase.format()) + ")");
        return phase.personalBest()
            ? List.of(time, new Notice(KungMessages.Type.SUCCESS, result + " (Previous PB: "
                + DungeonSplitsOverlayFeature.formatDurationMillis(phase.previousBestMillis(), phase.format()) + ")"))
            : List.of(time);
    }

    record Notice(KungMessages.Type type, String result) {
        String text() {
            return type == KungMessages.Type.SUCCESS ? "PERSONAL BEST! " + result : result;
        }

        Component component() {
            if (type != KungMessages.Type.SUCCESS) return KungMessages.component(type, "Splits", result);
            return KungMessages.component(type, "Splits", "").copy()
                .append(Component.literal("PERSONAL BEST!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(" " + result).withStyle(ChatFormatting.GREEN));
        }
    }
}
