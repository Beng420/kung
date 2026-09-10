package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class DungeonSplitTrackerTest {
    @Test
    public void startRoomFloorMetadataSurvivesTheTimerResetWhenCountdownHasNoFloor() {
        AtomicLong clock = new AtomicLong(1_000L);
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.configureForFloor(1, false);
        assertTrue(tracker.hasKnownFloor());
        assertFalse(tracker.started());
        assertTrue(Arrays.asList(tracker.splitNames()).contains("Bonzo Phase 1"));
        clock.set(20_000L);
        tracker.startRun(0L, 0, false);
        assertTrue(tracker.running());
        assertTrue(Arrays.asList(tracker.splitNames()).contains("Bonzo Phase 1"));
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Maxor"));
        assertEquals(0L, tracker.currentTotalDurationMillis());
    }

    @Test
    public void preparedFloorCanBeCorrectedWithoutCarryingMetadataFromAnEndedRun() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.configureForFloor(7, true);
        tracker.configureForFloor(7, false);
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Dragons"));
        tracker.startRun(0L, 7, true);
        tracker.stopRun();
        tracker.startRun(0L, 0, false);
        assertFalse(tracker.hasKnownFloor());
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Dragons"));
        tracker.configureForFloor(1, false);
        tracker.reset();
        assertFalse(tracker.hasKnownFloor());
        assertFalse(tracker.started());
    }

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
        assertTrue(tracker.completedSplits().isEmpty());
        assertEquals("Blood Open", tracker.stoppedCurrentSplit().name());
    }

    @Test
    public void soloWipeKeepsTheExactMeasuredBloodProgressAndFrozenMasterLayout() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        for (int tick = 0; tick < 1100; tick++) tracker.serverTick(0L);
        clock.set(57_600L);
        // Actual trace: a wipe before Blood prints the result score without any boss banner.
        assertTrue(tracker.observeMessage("Team Score: 11 (D)", 0L));
        var stopped = tracker.stoppedCurrentSplit();
        assertEquals("Blood Open", stopped.name());
        assertEquals(57_600L, stopped.splitDurationMillis());
        assertEquals(57_600L, stopped.totalDurationMillis());
        assertEquals(55_000L, stopped.serverSplitDurationMillis());
        assertEquals(55_000L, stopped.serverTotalDurationMillis());
        assertTrue(tracker.completedSplits().isEmpty());
        assertFalse(tracker.hasCurrentSplit());
        assertTrue(tracker.started());
        clock.set(120_000L);
        tracker.serverTick(0L);
        tracker.stopRun();
        tracker.configureForFloor(1, false);
        tracker.configureKnownFloor(0, false);
        assertFalse(tracker.observeMessage("Team Score: 11 (D)", 1L));
        assertFalse(tracker.observeMessage("[BOSS] Wither King: We will decide it all, here, now.", 1L));
        assertEquals(stopped, tracker.stoppedCurrentSplit());
        assertEquals(57_600L, tracker.currentTotalDurationMillis());
        assertEquals(55_000L, tracker.currentTotalServerDurationMillis());
        assertTrue(Arrays.asList(tracker.splitNames()).contains("Dragons"));
    }

    @Test
    public void endingMidPhasePreservesCompletedPhasesAndKeepsTheInterruptedSpanSeparate() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 1, false);
        clock.set(1_000L);
        tracker.observeMessage("The BLOOD DOOR has been opened!", 0L);
        var bloodOpen = tracker.completedSplits().getFirst();
        clock.set(5_000L);
        tracker.observeMessage("Team Score: 25 (D)", 0L);
        assertEquals(1, tracker.completedSplits().size());
        assertEquals(bloodOpen, tracker.completedSplits().getFirst());
        assertEquals("Blood Clear", tracker.stoppedCurrentSplit().name());
        assertEquals(4_000L, tracker.stoppedCurrentSplit().splitDurationMillis());
        assertEquals(5_000L, tracker.stoppedCurrentSplit().totalDurationMillis());
        tracker.configureKnownFloor(7, true);
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Dragons"));
        tracker.startRun(0L, 7, true);
        assertNull(tracker.stoppedCurrentSplit());
        assertTrue(tracker.completedSplits().isEmpty());
        assertTrue(Arrays.asList(tracker.splitNames()).contains("Dragons"));
    }

    @Test
    public void actualFinalPhaseCompletesWithoutAnInterruptedSnapshotAndRejectsRewardMetadata() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 1, false);
        clock.set(5_000L);
        tracker.observeMessage("[BOSS] Bonzo: Oh I'm dead!", 0L);
        clock.set(8_000L);
        tracker.observeMessage("Defeated Bonzo in 8s", 0L);
        assertEquals("Bonzo Phase 2", tracker.completedSplits().getLast().name());
        assertEquals(3_000L, tracker.completedSplits().getLast().splitDurationMillis());
        assertNull(tracker.stoppedCurrentSplit());
        tracker.configureForFloor(7, true);
        tracker.configureKnownFloor(0, false);
        assertTrue(Arrays.asList(tracker.splitNames()).contains("Bonzo Phase 2"));
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Dragons"));
        clock.set(20_000L);
        tracker.serverTick(0L);
        tracker.stopRun();
        assertEquals(8_000L, tracker.currentTotalDurationMillis());
        assertEquals(3_000L, tracker.completedSplits().getLast().splitDurationMillis());
    }

    @Test
    public void masterSevenContinuesThroughRelicsDialogueAndDragonsUntilCompletion() {
        AtomicLong clock = new AtomicLong(1_000L);
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        String[][] messages = {
            {"The BLOOD DOOR has been opened!", "Blood Clear"},
            {"[BOSS] The Watcher: You have proven yourself. You may pass.", "Portal Entry"},
            {"[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "Maxor"},
            {"[BOSS] Storm: Pathetic Maxor, just like expected.", "Storm"},
            {"[BOSS] Goldor: Who dares trespass into my domain?", "Terminals"},
            {"The Core entrance is opening!", "Goldor"},
            {"[BOSS] Necron: You went further than any human before, congratulations.", "Necron"},
            {"[BOSS] Necron: All this, for nothing...", "Relics"},
            {"[BOSS] Wither King: You... again?", "Wither King"},
            {"[BOSS] Wither King: We will decide it all, here, now.", "Dragons"}
        };
        for (String[] event : messages) {
            clock.addAndGet(2_000L);
            tracker.serverTick(clock.get());
            assertTrue(event[0], tracker.observeMessage(event[0], 0L));
            assertEquals(event[1], tracker.currentSplitName());
            assertTrue(tracker.hasCurrentSplit());
            assertTrue("Only the completion banner ends the run", tracker.running());
        }
        clock.addAndGet(2_000L);
        tracker.serverTick(clock.get());
        assertTrue(tracker.observeMessage("[BOSS] Wither King: Incredible. You did what I couldn't do myself.", 0L));
        assertFalse(tracker.hasCurrentSplit());
        assertTrue(tracker.running());
        assertEquals("Dragons", tracker.completedSplits().getLast().name());
        assertEquals(22_000L, tracker.completedSplits().getLast().totalDurationMillis());
        assertFalse(tracker.observeMessage("[BOSS] Wither King: Incredible. You did what I couldn't do myself.", 0L));
        clock.addAndGet(2_000L);
        assertTrue(tracker.observeMessage("\u00a7r\u00a7c☠ Defeated The Wither King in 06m 42s (NEW RECORD!)", 0L));
        assertFalse(tracker.running());
        assertEquals(11, tracker.completedSplits().size());
        assertEquals(24_000L, tracker.currentTotalDurationMillis());
        assertEquals(550L, tracker.currentTotalServerDurationMillis());
        for (DungeonSplitTracker.CompletedSplit split : tracker.completedSplits()) {
            assertEquals(2_000L, split.splitDurationMillis());
        }
        clock.addAndGet(30_000L);
        tracker.serverTick(clock.get());
        assertEquals(24_000L, tracker.currentTotalDurationMillis());
        assertEquals(550L, tracker.currentTotalServerDurationMillis());
    }

    @Test
    public void repeatedAndOutOfOrderChatCannotRewindOrResetTheTimer() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        assertTrue(tracker.observeMessage("The BLOOD DOOR has been opened!", 0L));
        clock.addAndGet(5_000L);
        assertFalse(tracker.observeMessage("The BLOOD DOOR has been opened!", 100L));
        assertFalse(tracker.observeMessage("Starting in 1 second.", 100L));
        assertTrue(tracker.observeMessage("[BOSS] The Watcher: You have proven yourself. You may pass.", 101L));
        assertFalse(tracker.observeMessage("The BLOOD DOOR has been opened!", 102L));
        assertEquals("Portal Entry", tracker.currentSplitName());
        assertEquals(2, tracker.completedSplits().size());
        assertEquals(5_000L, tracker.currentTotalDurationMillis());
    }

    @Test
    public void delayedFloorMetadataAndMasterSevenDialoguePreserveProgress() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 0, false);
        assertTrue(tracker.observeMessage("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", 1L));
        assertEquals("Maxor", tracker.currentSplitName());
        clock.set(5_000L);
        tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 2L);
        assertFalse(tracker.hasCurrentSplit());
        clock.set(8_000L);
        assertTrue(tracker.observeMessage("[BOSS] Wither King: You... again?", 3L));
        assertEquals("Wither King", tracker.currentSplitName());
        assertTrue(tracker.hasCurrentSplit());
        assertTrue(tracker.completedSplits().stream().anyMatch(split -> split.name().equals("Relics")));
        assertEquals("Relics", tracker.completedSplits().getLast().name());
        assertEquals(3_000L, tracker.completedSplits().getLast().splitDurationMillis());
        tracker.configureForFloor(0, false);
        tracker.configureForFloor(7, false);
        assertTrue(Arrays.asList(tracker.splitNames()).contains("Dragons"));
        assertEquals("Wither King", tracker.currentSplitName());
    }

    @Test
    public void missingMessagesDoNotFabricateAccuratePhaseTimes() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, false);
        clock.set(3_000L);
        tracker.observeMessage("[BOSS] The Watcher: You have proven yourself. You may pass.", 1L);
        assertEquals(-1L, tracker.completedSplits().getFirst().splitDurationMillis());
        assertEquals(3_000L, tracker.completedSplits().getFirst().totalDurationMillis());
        clock.set(4_000L);
        tracker.observeMessage("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", 2L);
        assertEquals(1_000L, tracker.completedSplits().getLast().splitDurationMillis());
    }

    @Test
    public void normalSevenEndsNecronAtCombatDeathWithoutAddingAVictoryPhase() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, false);
        clock.set(10_000L);
        tracker.observeMessage("[BOSS] Necron: You went further than any human before, congratulations.", 0L);
        clock.set(15_000L);
        tracker.serverTick(clock.get());
        tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 1L);
        assertEquals("Necron", tracker.completedSplits().getLast().name());
        assertEquals(5_000L, tracker.completedSplits().getLast().splitDurationMillis());
        assertEquals(50L, tracker.completedSplits().getLast().serverSplitDurationMillis());
        assertEquals(15_000L, tracker.completedSplits().getLast().totalDurationMillis());
        assertFalse(tracker.hasCurrentSplit());
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Relics"));
        assertTrue(tracker.running());
        assertFalse(tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 1L));
        assertFalse(tracker.observeMessage("[BOSS] Necron: You went further than any human before, congratulations.", 1L));
        clock.set(18_000L);
        tracker.serverTick(clock.get());
        assertTrue(tracker.observeMessage("Defeated Necron in 6m 1s", 2L));
        assertFalse(tracker.running());
        assertEquals(18_000L, tracker.currentTotalDurationMillis());
        assertEquals(100L, tracker.currentTotalServerDurationMillis());
        assertEquals(2, tracker.completedSplits().size());
        assertEquals(5_000L, tracker.completedSplits().getLast().splitDurationMillis());
    }

    @Test
    public void lateMasterMetadataStartsRelicsFromNecronsDeath() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, false);
        tracker.observeMessage("[BOSS] Necron: You went further than any human before, congratulations.", 0L);
        clock.set(5_000L);
        tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 1L);
        clock.set(7_000L);
        tracker.configureForFloor(7, true);
        assertTrue(tracker.hasCurrentSplit());
        assertEquals("Relics", tracker.currentSplitName());
        assertEquals(2_000L, tracker.currentSplitDurationMillis());
        assertFalse(tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 2L));
        assertEquals(2, tracker.completedSplits().size());
    }

    @Test
    public void allFloorLayoutsContainOnlyNamedRunPhases() {
        for (int floor = 1; floor <= 7; floor++) {
            for (boolean master : new boolean[] {false, true}) {
                DungeonSplitTracker tracker = new DungeonSplitTracker();
                tracker.startRun(0L, floor, master);
                assertFalse(Arrays.asList(tracker.splitNames()).contains("Victory"));
            }
        }
        assertFalse(Arrays.asList(DungeonSplitTracker.defaultSplitNames()).contains("Victory"));
    }

    @Test
    public void lowerFloorsNameTheActualFinalBossPhase() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.startRun(0L, 6, true);
        tracker.observeMessage("[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!", 1L);
        assertEquals("Terracottas", tracker.currentSplitName());
        tracker.observeMessage("[BOSS] Sadan: ENOUGH!", 2L);
        assertEquals("Giants", tracker.currentSplitName());
        tracker.observeMessage("[BOSS] Sadan: You did it. I understand now, you have earned my respect.", 3L);
        assertEquals("Sadan", tracker.currentSplitName());
        assertTrue(tracker.running());
    }

    @Test
    public void abortFreezesTimeAndNewRunResetsMasterModeAndResults() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        clock.set(10_000L);
        tracker.stopRun();
        clock.set(20_000L);
        assertEquals(10_000L, tracker.currentTotalDurationMillis());
        assertTrue(tracker.completedSplits().isEmpty());
        tracker.startRun(100L, 7, false);
        assertFalse(Arrays.asList(tracker.splitNames()).contains("Dragons"));
        assertEquals(0L, tracker.currentTotalDurationMillis());
    }

    @Test
    public void splitFormattingKeepsMillisecondsAndMarksMissingValues() {
        assertEquals("--", DungeonSplitsOverlayFeature.formatDurationMillis(-1L));
        assertEquals("0.00s", DungeonSplitsOverlayFeature.formatDurationMillis(0L));
        assertEquals("1.03s", DungeonSplitsOverlayFeature.formatDurationMillis(1_039L));
        assertEquals("2m 5.67s", DungeonSplitsOverlayFeature.formatDurationMillis(125_678L));
    }
}
