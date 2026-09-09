package com.github.beng420.kung.ui;

import static com.github.beng420.kung.util.GuiDraw.fill;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiToggle {
    private UiToggle() {
    }

    public static void draw(GuiGraphicsExtractor graphics, UiBounds bounds, boolean enabled, UiTheme theme) {
        int trackWidth = Math.min(20, bounds.width());
        int trackX = bounds.right() - trackWidth;
        fill(graphics, trackX, bounds.y() + 3, bounds.right(), bounds.bottom() - 3,
            enabled ? theme.accent() : theme.panelDark());
        int knobLeft = trackX + (enabled ? trackWidth - 9 : 3);
        fill(graphics, knobLeft, bounds.y() + 5, knobLeft + 6, bounds.bottom() - 5, theme.text());
    }
}
