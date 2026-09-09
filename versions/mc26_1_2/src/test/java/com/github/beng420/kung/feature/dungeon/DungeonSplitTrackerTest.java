package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DungeonSplitTrackerTest {
    @Test
    public void followsBloodAndPortalMessages() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.startRun(0L, 7, false);

        assertTrue(tracker.observeMessage("\u00a7cThe BLOOD DOOR has been opened!", 1L));
        assertEquals("Blood Clear", tracker.currentSplitName());
        assertFalse(tracker.observeMessage("The BLOOD DOOR has been opened!", 2L));
        assertTrue(tracker.observeMessage("[BOSS] The Watcher: You have proven yourself. You may pass.", 3L));
        assertEquals("Portal Entry", tracker.currentSplitName());
    }

    @Test
    public void finishesFromTheServerCompletionMessage() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.startRun(0L, 7, false);

        assertTrue(tracker.observeMessage("S Defeated Necron in 05m 42s", 1L));
        assertFalse(tracker.running());
        assertEquals(1, tracker.completedSplits().size());
    }
}
