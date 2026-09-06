package com.github.beng420.kung.feature.dungeon.room;

public enum RoomType {
    START(0xFF0aa01e),      // grün
    NORMAL(0xFF8B511F),     // warmes braun
    YELLOW(0xFFffed4f),     // gelb
    PUZZLE(0xFFD45CFF),     // magenta/purple
    BLOOD(0xFFE53935),      // rot
    FAIRY(0xFFFF7BC1),      // rosa
    TRAP(0xFFFF8C1A),       // orange, ab F3+
    UNKNOWN(0xFFFFFFFF);

    private final int color;

    RoomType(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }
}
