package com.github.beng420.kung.ui;

import static com.github.beng420.kung.util.GuiDraw.fill;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiTooltip {
    private UiTooltip() {
    }

    public static void draw(
        GuiGraphicsExtractor graphics,
        Font font,
        List<String> lines,
        int x,
        int y,
        UiTheme theme
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int width = lines.stream().mapToInt(font::width).max().orElse(0) + UiSpacing.MD * 2;
        int height = lines.size() * (font.lineHeight + UiSpacing.XS) + UiSpacing.SM * 2;
        fill(graphics, x - 1, y - 1, x + width + 1, y + height + 1, theme.border());
        fill(graphics, x, y, x + width, y + height, theme.panelDark());
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(
                font,
                lines.get(index),
                x + UiSpacing.MD,
                y + UiSpacing.SM + index * (font.lineHeight + UiSpacing.XS),
                theme.text(),
                true
            );
        }
    }
}
