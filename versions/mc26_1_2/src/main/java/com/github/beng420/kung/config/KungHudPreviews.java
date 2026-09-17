package com.github.beng420.kung.config;

import com.github.beng420.kung.feature.dungeon.DungeonSplitsOverlayFeature;
import com.github.beng420.kung.feature.dungeon.DragonDebuffHud;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.feature.garden.FeastOverlayFeature;
import com.github.beng420.kung.feature.safari.SafariOverlayFeature;
import com.github.beng420.kung.feature.misc.BowDrawIndicatorFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Editor previews never enable features or create dungeon/container observations. */
public final class KungHudPreviews {
    private static final int PLACEHOLDER = 0x669AA4B2;
    private static final int TEXT = 0xFFFFFFFF;

    private KungHudPreviews() { }

    public static void draw(GuiGraphicsExtractor graphics, KungHudLayout.Entry entry, DungeonStateTracker tracker) {
        KungConfig config = KungConfig.get();
        switch (entry.id()) {
            case "dungeon_splits" -> DungeonSplitsOverlayFeature.drawPreview(graphics, config.splits, tracker.splitTracker());
            case "feast_progress" -> FeastOverlayFeature.drawPreview(graphics, config.feast);
            case "safari_uniques" -> SafariOverlayFeature.drawPreview(graphics, config.safari);
            case "dragon_debuff" -> DragonDebuffHud.drawPreview(graphics, config.dungeon);
            case "bow_draw_indicator" -> BowDrawIndicatorFeature.drawPreview(graphics, config.misc);
            // Map and Superpairs retain the safe labeled placeholders used by /kung hud.
            case "dungeon_map", "superpairs" -> drawPlaceholder(graphics, entry);
            default -> { }
        }
    }

    private static void drawPlaceholder(GuiGraphicsExtractor graphics, KungHudLayout.Entry entry) {
        var bounds = entry.bounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PLACEHOLDER);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(entry.name(), Math.max(0, bounds.width() - 8));
        graphics.text(font, label, bounds.x() + (bounds.width() - font.width(label)) / 2,
            bounds.y() + bounds.height() / 2 - 4, TEXT, true);
    }
}
