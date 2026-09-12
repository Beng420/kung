package com.github.beng420.kung.feature.dungeon;

import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Pads measured names in chat pixels, independently of proportional or resource-pack fonts. */
final class DungeonRunSummaryLayout {
    static final String PIXEL_SPACE = "\uE000";
    static final FontDescription.Resource SPACING_FONT =
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("kung", "chat_spacing"));
    private static final int COLUMN_GAP = 8;
    private static final Style TEXT_STYLE = Style.EMPTY.withFont(FontDescription.DEFAULT)
        .withColor(ChatFormatting.WHITE).withBold(false);
    private static final Style SPACING_STYLE = Style.EMPTY.withFont(SPACING_FONT).withBold(false);

    private DungeonRunSummaryLayout() { }

    static int nameColumnWidth(List<String> names, ToIntFunction<String> width) {
        return names.stream().mapToInt(width).max().orElse(0) + COLUMN_GAP;
    }

    static Component playerLine(String name, String details, int nameColumnWidth, ToIntFunction<String> width) {
        int padding = Math.max(COLUMN_GAP, nameColumnWidth - width.applyAsInt(name));
        return Component.empty()
            .append(Component.literal(name).setStyle(TEXT_STYLE))
            .append(Component.literal(PIXEL_SPACE.repeat(padding)).setStyle(SPACING_STYLE))
            .append(Component.literal(details).setStyle(TEXT_STYLE));
    }
}
