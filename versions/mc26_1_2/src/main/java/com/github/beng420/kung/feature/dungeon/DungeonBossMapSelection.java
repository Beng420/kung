package com.github.beng420.kung.feature.dungeon;

/** Presentation state only. Coordinates never establish dungeon instance membership. */
final class DungeonBossMapSelection {
    private long epoch = Long.MIN_VALUE;
    private int floor = -1;
    private DungeonBossMapCatalog.Arena selected;

    DungeonBossMapCatalog.Arena update(DungeonBossMapCatalog catalog, long instanceEpoch, int currentFloor,
                                       boolean inCatacombs, boolean positionKnown, boolean bossStarted,
                                       double x, double y, double z) {
        if (epoch != instanceEpoch || floor != currentFloor || !inCatacombs) {
            selected = null;
            epoch = instanceEpoch;
            floor = currentFloor;
        }
        if (!inCatacombs || !positionKnown) return null;
        boolean inClearGrid = insideClearGrid(x, z);
        // Some arena bounds overlap the coarse clear-grid rectangle. A boss phase,
        // or an arena first reached outside that grid, must establish the transition.
        if (inClearGrid && !bossStarted && selected == null) return null;
        DungeonBossMapCatalog.Arena match = catalog.find(currentFloor, x, y, z);
        if (match != null) {
            selected = match;
            return selected;
        }
        if (inClearGrid) selected = null;
        // Keep the preceding map through the sub-block gaps between Stella's Y bands.
        return selected;
    }

    static boolean insideClearGrid(double x, double z) {
        return x >= -201 && x <= -9 && z >= -201 && z <= -9;
    }
}
