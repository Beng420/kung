package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.message.KungMessages;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Phase notifications use local Kung system messages, independent of prediction visibility. */
final class DungeonSplitMessages {
    private DungeonSplitMessages() { }

    static void send(DungeonSplitTracker.PhaseMessage phase) {
        Minecraft client = Minecraft.getInstance();
        for (Notice notice : notices(phase)) {
            KungMessages.send(client, notice.component());
        }
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
