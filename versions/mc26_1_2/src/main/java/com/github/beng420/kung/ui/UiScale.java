package com.github.beng420.kung.ui;

import net.minecraft.client.Minecraft;

/**
 * How many device pixels one unit of a menu covers. Menus draw at a fraction of a GUI unit, and a font
 * baked for a different resolution is then sampled without filtering: one stem of a letter survives,
 * the next is dropped, which reads as a squashed font. {@link KungFonts} rasterizes for this number.
 */
public final class UiScale {
    private static volatile float deviceScale = 1F;

    private UiScale() {
    }

    /** Call with the scale a menu is about to draw at; returns it unchanged. */
    public static float menu(float scale) {
        deviceScale = Math.max(0.1F, scale * guiScale());
        return scale;
    }

    public static float deviceScale() {
        return deviceScale;
    }

    private static int guiScale() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.getWindow() == null ? 1 : client.getWindow().getGuiScale();
    }
}
