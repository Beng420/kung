package com.github.beng420.kung.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiToggle {
    private UiToggle() {
    }

    public static void draw(GuiGraphicsExtractor graphics, UiBounds bounds, boolean enabled, UiTheme theme) {
        int trackWidth = Math.min(20, bounds.width());
        int trackX = bounds.right() - trackWidth;
        graphics.fill(trackX, bounds.y() + 3, bounds.right(), bounds.bottom() - 3,
            enabled ? theme.accent() : theme.panelDark());
        int knobLeft = trackX + (enabled ? trackWidth - 9 : 3);
        graphics.fill(knobLeft, bounds.y() + 5, knobLeft + 6, bounds.bottom() - 5, theme.text());
    }
}
