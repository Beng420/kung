package com.github.beng420.kung.ui;

import static com.github.beng420.kung.util.GuiDraw.fill;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class UiTooltip {
    private UiTooltip() {
    }

    public static void draw(
        GuiGraphicsExtractor graphics,
        Font font,
        List<String> lines,
        int x,
        int y,
        int viewportWidth,
        int viewportHeight,
        UiTheme theme
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int textWidth = Math.min(260, viewportWidth - UiSpacing.MD * 2 - 2);
        int maxLines = (viewportHeight - UiSpacing.SM * 2 - 2) / (font.lineHeight + UiSpacing.XS);
        if (textWidth <= 0 || maxLines <= 0) return;
        var wrapped = lines.stream().flatMap(line -> font.split(Component.literal(line), textWidth).stream())
            .limit(maxLines).toList();
        int width = wrapped.stream().mapToInt(font::width).max().orElse(0) + UiSpacing.MD * 2;
        int height = wrapped.size() * (font.lineHeight + UiSpacing.XS) + UiSpacing.SM * 2;
        x = Math.clamp(x, 1, Math.max(1, viewportWidth - width - 1));
        if (y + height + 1 > viewportHeight) y -= height + UiSpacing.MD * 2;
        y = Math.clamp(y, 1, Math.max(1, viewportHeight - height - 1));
        fill(graphics, x - 1, y - 1, x + width + 1, y + height + 1, theme.border());
        fill(graphics, x, y, x + width, y + height, theme.panelDark());
        for (int index = 0; index < wrapped.size(); index++) {
            graphics.text(
                font,
                wrapped.get(index),
                x + UiSpacing.MD,
                y + UiSpacing.SM + index * (font.lineHeight + UiSpacing.XS),
                theme.text(),
                true
            );
        }
    }
}
