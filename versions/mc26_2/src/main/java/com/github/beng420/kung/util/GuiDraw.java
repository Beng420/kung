package com.github.beng420.kung.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class GuiDraw {
    private GuiDraw() {
    }

    public static void fill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }
}
