package com.github.beng420.kung.feature.dungeon;

/** Bounded round-robin work; a full map keeps getting refreshed, including preload cores. */
final class DungeonScanSchedule {
    private final int pointCount;
    private int cursor;

    DungeonScanSchedule(int pointCount) {
        if (pointCount <= 0) throw new IllegalArgumentException("pointCount must be positive");
        this.pointCount = pointCount;
    }

    int cursor() { return cursor; }

    void advance(int scanned) { cursor = Math.floorMod(cursor + scanned, pointCount); }

    void reset() { cursor = 0; }

    static boolean due(long now, long previous, long interval) {
        return previous == Long.MIN_VALUE || now < previous || now - previous >= interval;
    }
}
