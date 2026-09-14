package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import org.junit.Test;

public final class DungeonPuzzleProgressTest {
    @Test public void undiscoveredRowsAndDuplicateSnapshotsCannotRemoveOrMultiplyOutstandingPuzzles() {
        var puzzles = new DungeonPuzzleProgress();
        assertTrue(puzzles.observe("Puzzles: (2)"));
        puzzles.observe("???: [✦]");
        puzzles.observe("???: [✦]");
        assertEquals(2, puzzles.unfinished(0, 0));
        puzzles.observe("Water Board: [✦]");
        puzzles.observe("Water Board: [✦]");
        assertEquals(0, puzzles.failed());
        assertEquals(2, puzzles.unfinished(1, 0));
        puzzles.observe("Water Board: [✔]");
        puzzles.observe("Blaze: [✓]");
        assertEquals(0, puzzles.unfinished(2, 0));
        puzzles.reset();
        assertEquals(0, puzzles.completed());
        assertEquals(1, puzzles.unfinished(1, 0));
    }

    @Test public void missingRowsRetainFailureAndOnlyExplicitUpdatesReplaceIt() {
        var puzzles = new DungeonPuzzleProgress();
        puzzles.observe("Blaze: [X] (Alice)");
        assertFalse(puzzles.observe(""));
        assertFalse(puzzles.observe("Party > Alice: Blaze: [✓]"));
        assertEquals(1, puzzles.failed());
        assertEquals(1, puzzles.unfinished(1, 1));
        puzzles.observe("Blaze: [✓]");
        assertEquals(0, puzzles.failed());
        assertEquals(0, puzzles.unfinished(1, 0));
    }
}
