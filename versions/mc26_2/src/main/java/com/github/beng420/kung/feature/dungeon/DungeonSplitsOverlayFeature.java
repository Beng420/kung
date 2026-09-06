package com.github.beng420.kung.feature.dungeon;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.feature.Feature;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class DungeonSplitsOverlayFeature {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "dungeon_splits_overlay");
    private static final int WIDTH = 184;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 12;
    private static final int PADDING = 6;
    private static final int BACKGROUND = 0x00000000;
    private static final int PANEL = 0x00000000;
    private static final int TEXT = 0xFFE9EDF2;
    private static final int MUTED_TEXT = 0xFF99A1AD;
    private static final int ACTIVE_TEXT = 0xFFFFF176;
    private static final int BORDER = 0x00000000;

    private DungeonSplitsOverlayFeature() {
    }

    public static Feature definition() {
        return new Feature(
            "dungeon-splits-overlay",
            "Dungeon Splits Overlay",
            true,
            "Shows dungeon phase times separately from the map."
        );
    }

    public static void initializeClient(DungeonStateTracker dungeonStateTracker) {
        HudElementRegistry.addLast(
            HUD_ID,
            (graphics, deltaTracker) -> render(graphics, deltaTracker, dungeonStateTracker)
        );
    }

    private static void render(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        DungeonStateTracker dungeonStateTracker
    ) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        DungeonSplitTracker tracker = dungeonStateTracker.splitTracker();
        if (!config.splitsEnabled()
            || (!dungeonStateTracker.isInDungeonArea() && !tracker.running())) {
            return;
        }

        List<DungeonSplitTracker.CompletedSplit> completedSplits = tracker.completedSplits();
        String[] splitNames = tracker.splitNames();
        int visibleRows = splitNames.length + (hasBossEntryRow(splitNames) ? 1 : 0);
        int height = HEADER_HEIGHT + PADDING + visibleRows * ROW_HEIGHT + PADDING;
        float scale = config.splitsScale() / 100.0F;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.splitsX(), config.splitsY());
            graphics.pose().scale(scale, scale);
            int left = 0;
            int top = 0;

            if (BORDER != 0) {
                fill(graphics, left - 1, top - 1, left + WIDTH + 1, top + height + 1, BORDER);
            }
            if (BACKGROUND != 0) {
                fill(graphics, left, top, left + WIDTH, top + height, BACKGROUND);
            }
            if (PANEL != 0) {
                fill(graphics, left + 3, top + HEADER_HEIGHT, left + WIDTH - 3, top + height - 3, PANEL);
            }

            Minecraft client = Minecraft.getInstance();
            graphics.text(client.font, "Kung Splits", left + PADDING, top + 6, TEXT, true);

            int rowY = top + HEADER_HEIGHT + PADDING;
            if (completedSplits.isEmpty() && !tracker.running()) {
                graphics.text(client.font, "waiting for run", left + PADDING, rowY, MUTED_TEXT, true);
                return;
            }

            for (String splitName : splitNames) {
                DungeonSplitTracker.CompletedSplit completed = completedSplit(splitName, completedSplits);
                if (completed != null) {
                    drawSplitRow(
                        graphics,
                        left,
                        rowY,
                        completed.name(),
                        formatDurationMillis(completed.splitDurationMillis()),
                        formatDurationMillis(completed.totalDurationMillis()),
                        TEXT
                    );
                } else if (tracker.running() && splitName.equals(tracker.currentSplitName())) {
                    drawSplitRow(
                        graphics,
                        left,
                        rowY,
                        splitName,
                        formatDurationMillis(tracker.currentSplitDurationMillis()),
                        formatDurationMillis(tracker.currentTotalDurationMillis()),
                        ACTIVE_TEXT
                    );
                } else {
                    drawSplitRow(graphics, left, rowY, splitName, "--", "--", MUTED_TEXT);
                }
                rowY += ROW_HEIGHT;
                if ("Portal Entry".equals(splitName)) {
                    DungeonSplitTracker.CompletedSplit portalEntry = completedSplit("Portal Entry", completedSplits);
                    if (portalEntry != null) {
                        drawCumulativeSplitRow(
                            graphics,
                            left,
                            rowY,
                            "Boss Entry",
                            formatDurationMillis(portalEntry.totalDurationMillis()),
                            TEXT
                        );
                    } else {
                        drawCumulativeSplitRow(graphics, left, rowY, "Boss Entry", "--", MUTED_TEXT);
                    }
                    rowY += ROW_HEIGHT;
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static OverlayBounds overlayBounds(DungeonMapOverlayConfig config, DungeonSplitTracker tracker) {
        float scale = config.splitsScale() / 100.0F;
        int visibleRows = tracker.splitNames().length + (hasBossEntryRow(tracker.splitNames()) ? 1 : 0);
        int height = HEADER_HEIGHT + PADDING + visibleRows * ROW_HEIGHT + PADDING;
        return new OverlayBounds(
            Math.round(config.splitsX() - scale),
            Math.round(config.splitsY() - scale),
            Math.round((WIDTH + 2) * scale),
            Math.round((height + 2) * scale)
        );
    }

    private static DungeonSplitTracker.CompletedSplit completedSplit(
        String name,
        List<DungeonSplitTracker.CompletedSplit> completedSplits
    ) {
        for (DungeonSplitTracker.CompletedSplit split : completedSplits) {
            if (split.name().equals(name)) {
                return split;
            }
        }
        return null;
    }

    private static boolean hasBossEntryRow(String[] splitNames) {
        for (String splitName : splitNames) {
            if ("Portal Entry".equals(splitName)) {
                return true;
            }
        }
        return false;
    }

    private static void drawSplitRow(
        GuiGraphicsExtractor graphics,
        int left,
        int y,
        String name,
        String splitDuration,
        String totalDuration,
        int color
    ) {
        Minecraft client = Minecraft.getInstance();
        String time = splitDuration + " (" + totalDuration + ")";
        int timeWidth = client.font.width(time);
        int nameMaxWidth = WIDTH - PADDING * 3 - timeWidth;
        graphics.text(client.font, trimToWidth(name, Math.max(32, nameMaxWidth)), left + PADDING, y, color, true);
        graphics.text(client.font, time, left + WIDTH - PADDING - timeWidth, y, color, true);
    }

    private static void drawCumulativeSplitRow(
        GuiGraphicsExtractor graphics,
        int left,
        int y,
        String name,
        String duration,
        int color
    ) {
        Minecraft client = Minecraft.getInstance();
        int timeWidth = client.font.width(duration);
        int nameMaxWidth = WIDTH - PADDING * 3 - timeWidth;
        graphics.text(client.font, trimToWidth(name, Math.max(32, nameMaxWidth)), left + PADDING, y, color, true);
        graphics.text(client.font, duration, left + WIDTH - PADDING - timeWidth, y, color, true);
    }

    private static String formatDurationMillis(long millis) {
        double seconds = millis / 1000.0;
        if (seconds < 60.0) {
            return String.format(Locale.ROOT, "%.2fs", seconds);
        }

        long minutes = (long) (seconds / 60.0);
        double remainder = seconds - minutes * 60.0;
        return String.format(Locale.ROOT, "%dm%.2fs", minutes, remainder);
    }

    private static String trimToWidth(String value, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        if (client.font.width(value) <= maxWidth) {
            return value;
        }

        String suffix = "...";
        for (int end = value.length(); end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (client.font.width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    public record OverlayBounds(int x, int y, int width, int height) {
    }
}
