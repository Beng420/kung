package com.github.beng420.kung.feature;

public final class DungeonMapFeature {
    private DungeonMapFeature() {
    }

    public static Feature definition() {
        return new Feature(
            "dungeon-map",
            "Dungeon Map",
            true,
            "Adds a minimap for dungeons."
        );
    }
}
