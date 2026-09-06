package com.github.beng420.kung.feature.dungeon;

public enum DungeonDoorKind {
    NONE,
    OPEN,
    WITHER,
    BLOOD;

    public boolean visible() {
        return this != NONE;
    }
}
