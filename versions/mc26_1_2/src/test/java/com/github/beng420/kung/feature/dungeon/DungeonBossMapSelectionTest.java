package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import org.junit.Test;

public final class DungeonBossMapSelectionTest {
    @Test public void appliedBossEntryRetainsItsSignalAfterVictoryUntilInstanceReset() {
        var tracker = new DungeonSplitTracker(() -> 1_000L, ignored -> {});
        tracker.configureForFloor(4, true);
        assertFalse(tracker.hasEnteredBoss());
        tracker.startRun(0, 4, true);
        assertFalse(tracker.hasEnteredBoss());
        tracker.observeMessage("[BOSS] The Watcher: Things feel a little more roomy now, eh?", 0);
        tracker.observeMessage("[BOSS] The Watcher: You have proven yourself. You may pass.", 0);
        assertFalse(tracker.hasEnteredBoss());
        tracker.observeMessage("Party > Beng114: [BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", 0);
        assertFalse(tracker.hasEnteredBoss());
        tracker.observeMessage("[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", 0);
        assertTrue(tracker.hasEnteredBoss());
        tracker.observeMessage("Team Score: 300 (S+)", 0);
        assertFalse(tracker.running());
        assertTrue(tracker.hasEnteredBoss());
        tracker.reset();
        assertFalse(tracker.hasEnteredBoss());
    }

    @Test public void fourthFloorOverlapCannotReplaceClearMapBeforeBossEvidence() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var selection = new DungeonBossMapSelection();
        assertNotNull(catalog.find(4, -25, 70, -25));
        assertNull(selection.update(catalog, 1, 4, true, true, false, -25, 70, -25));
        var boss = selection.update(catalog, 1, 4, true, true, true, -25, 70, -25);
        assertEquals("f4_boss", boss.image());
        assertSame(boss, selection.update(catalog, 1, 4, true, true, false, -25, 70, -25));
    }

    @Test public void unambiguousArenaPositionSupportsActivationAfterMissingDialogue() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var selection = new DungeonBossMapSelection();
        var boss = selection.update(catalog, 1, 4, true, true, false, 5, 70, 5);
        assertEquals("f4_boss", boss.image());
        assertSame(boss, selection.update(catalog, 1, 4, true, true, false, -25, 70, -25));
    }

    @Test public void instancePositionAndFloorEvidenceAreRequired() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var selection = new DungeonBossMapSelection();
        assertNull(selection.update(catalog, 1, 4, false, true, true, 5, 70, 5));
        assertNull(selection.update(catalog, 1, 4, true, false, true, 5, 70, 5));
        assertNull(selection.update(catalog, 1, -1, true, true, true, 5, 70, 5));
        assertNull(selection.update(catalog, 1, 0, true, true, true, 5, 70, 5));
        assertNull(selection.update(catalog, 1, 8, true, true, true, 5, 70, 5));
    }

    @Test public void fractionalLayerGapsRetainMapUntilTheNextRealLayerMatches() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var selection = new DungeonBossMapSelection();
        var maxor = selection.update(catalog, 1, 7, true, true, false, 73, 230, 48);
        assertEquals("f7_boss_s1", maxor.image());
        assertSame(maxor, selection.update(catalog, 1, 7, true, true, false, 73, 212.5, 48));
        var storm = selection.update(catalog, 1, 7, true, true, false, 73, 212, 48);
        assertEquals("f7_boss_s2", storm.image());
        assertSame(storm, selection.update(catalog, 1, 7, true, true, false, 73, 159.5, 48));
        assertEquals("f7_boss_s3", selection.update(catalog, 1, 7, true, true, false, 73, 159, 48).image());
        assertEquals("f7_boss_end", selection.update(catalog, 1, 7, true, true, false, 28, 175, 130).image());
    }

    @Test public void confirmedClearReturnRestoresClearMapAndRemovesTheLatch() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var selection = new DungeonBossMapSelection();
        assertNotNull(selection.update(catalog, 1, 4, true, true, false, 5, 70, 5));
        assertNull(selection.update(catalog, 1, 4, true, true, true, -185, 70, -185));
        assertNull(selection.update(catalog, 1, 4, true, true, false, -25, 70, -25));
    }

    @Test public void transferFloorChangeAndLeavingCatacombsCannotReuseAnOldArena() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var selection = new DungeonBossMapSelection();
        assertNotNull(selection.update(catalog, 1, 4, true, true, false, 5, 70, 5));
        assertNull(selection.update(catalog, 2, 4, true, true, false, -25, 70, -25));
        assertNotNull(selection.update(catalog, 2, 4, true, true, true, -25, 70, -25));
        assertNull(selection.update(catalog, 2, 3, true, true, false, -25, 70, -25));
        assertNotNull(selection.update(catalog, 2, 3, true, true, true, -25, 70, -25));
        assertNull(selection.update(catalog, 2, 3, false, true, true, -25, 70, -25));
        assertNull(selection.update(catalog, 2, 3, true, true, false, -25, 70, -25));
    }

    @Test public void clearGridIncludesItsBoundaryButNeverTheOriginDuringLoad() {
        assertTrue(DungeonBossMapSelection.insideClearGrid(-201, -201));
        assertTrue(DungeonBossMapSelection.insideClearGrid(-9, -9));
        assertFalse(DungeonBossMapSelection.insideClearGrid(-201.01, -25));
        assertFalse(DungeonBossMapSelection.insideClearGrid(-25, -8.99));
        assertFalse(DungeonBossMapSelection.insideClearGrid(0, 0));
    }
}
