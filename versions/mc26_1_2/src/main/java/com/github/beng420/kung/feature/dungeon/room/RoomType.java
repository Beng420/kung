package com.github.beng420.kung.feature.dungeon.room;

public enum RoomType {
    START(0xFF0aa01e),      // green
    NORMAL(0xFF8B511F),     // warm brown
    YELLOW(0xFFffed4f),     // yellow
    PUZZLE(0xFFD45CFF),     // magenta/purple
    BLOOD(0xFFE53935),      // red
    FAIRY(0xFFFF7BC1),      // pink
    TRAP(0xFFFF8C1A),       // orange, F3+
    UNKNOWN(0xFFFFFFFF);

    private final int color;

    RoomType(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }
}
