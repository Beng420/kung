package com.github.beng420.kung.ui;

import static com.github.beng420.kung.util.GuiDraw.fill;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudTextBlockRenderer {
    public enum Alignment { LEFT, CENTER, RIGHT }

    public void render(
        GuiGraphicsExtractor graphics,
        Font font,
        List<Line> lines,
        Options options
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int textWidth = lines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
        int width = textWidth + options.padding() * 2;
        int height = lines.size() * options.lineSpacing() + options.padding() * 2;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(options.x(), options.y());
            graphics.pose().scale(options.scale(), options.scale());
            if ((options.backgroundColor() >>> 24) != 0) {
                fill(graphics, 0, 0, width, height, options.backgroundColor());
            }
            for (int index = 0; index < lines.size(); index++) {
                Line line = lines.get(index);
                int lineWidth = font.width(line.text());
                int x = switch (options.alignment()) {
                    case LEFT -> options.padding();
                    case CENTER -> options.padding() + (textWidth - lineWidth) / 2;
                    case RIGHT -> options.padding() + textWidth - lineWidth;
                };
                graphics.text(font, line.text(), x, options.padding() + index * options.lineSpacing(), line.color(), line.shadow());
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public record Line(String text, int color, boolean shadow) {
    }

    public record Options(
        int x,
        int y,
        float scale,
        Alignment alignment,
        int backgroundColor,
        int padding,
        int lineSpacing
    ) {
        public Options {
            if (scale <= 0.0F || padding < 0 || lineSpacing <= 0) {
                throw new IllegalArgumentException("Invalid HUD text block options.");
            }
        }
    }
}
