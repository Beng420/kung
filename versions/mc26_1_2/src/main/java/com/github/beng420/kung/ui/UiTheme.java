package com.github.beng420.kung.ui;

public record UiTheme(
    int backdrop,
    int panel,
    int panelDark,
    int panelSoft,
    int control,
    int controlHover,
    int border,
    int text,
    int muted,
    int error,
    int success,
    int warning,
    int accent,
    int accentDark
) {
    private static final UiTheme SETTINGS = new UiTheme(
        0x66000000,
        0xEE151719,
        0xEE17191B,
        0xCC243044,
        0xCC2C384C,
        0xCC3A4960,
        0xAA000000,
        0xFFFFFFFF,
        0xFFBBC4D0,
        0xFFFF5555,
        0xFF55FF55,
        0xFFFFD166,
        0xFF3498DB,
        0xFF2077AE
    );

    private static final UiTheme CATACOMBS_CALCULATOR = new UiTheme(
        0xFF262626,
        0xFF5A3020,
        0xFF472719,
        0xFF70402D,
        0xFF737373,
        0xFF929292,
        0xFF8B8B8B,
        0xFFEDEDED,
        0xFFB4B4B4,
        0xFFFF5555,
        0xFF7CFF83,
        0xFFFFD166,
        0xFF79A8FF,
        0xFF527CCB
    );

    public static UiTheme settings() {
        return SETTINGS;
    }

    public static UiTheme catacombsCalculator() {
        return CATACOMBS_CALCULATOR;
    }
}
