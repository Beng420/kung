package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.SplitsConfig;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class DungeonSplitsOverlayTest {
    @Test public void wipedRunKeepsItsLastVisiblePhaseTimesUntilLeaving() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        for (int tick = 0; tick < 1100; tick++) tracker.serverTick(0L);
        clock.set(57_600L);
        long visibleWall = tracker.currentSplitDurationMillis();
        long visibleServer = tracker.currentSplitServerDurationMillis();
        assertNull(DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Open"));
        tracker.observeMessage("Team Score: 11 (D)", 0L);
        var snapshot = DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Open");
        assertNotNull(snapshot);
        assertEquals(visibleWall, snapshot.splitDurationMillis());
        assertEquals(visibleServer, snapshot.serverSplitDurationMillis());
        assertTrue(DungeonSplitsOverlayFeature.isVisible(false, true, tracker));
        clock.set(90_000L);
        tracker.serverTick(0L);
        tracker.configureKnownFloor(1, false);
        assertEquals(snapshot, DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Open"));
        assertFalse(DungeonSplitsOverlayFeature.isVisible(false, false, tracker));
        tracker.reset();
        assertNull(DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Open"));
    }

    @Test public void totalLossOnlyUpdatesAtPhaseBoundariesAndRunEnd() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        clock.set(12_000L);
        for (int tick = 0; tick < 200; tick++) tracker.serverTick(0L);
        assertEquals(-1L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        tracker.mark("Blood Clear", 0L);
        assertEquals(2_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        var bloodOpen = DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Open");
        clock.set(32_000L);
        for (int tick = 0; tick < 300; tick++) tracker.serverTick(0L);
        assertEquals(2_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        assertEquals(bloodOpen, DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Open"));
        assertNull(DungeonSplitsOverlayFeature.phaseSnapshot(tracker, "Blood Clear"));
        tracker.mark("Portal Entry", 0L);
        assertEquals(7_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        clock.set(34_000L);
        tracker.observeMessage("Team Score: 11 (D)", 0L);
        assertEquals(9_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        clock.set(60_000L);
        tracker.serverTick(0L);
        assertEquals(9_000L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
    }

    @Test public void secondsFormatDoesNotWrapAtTheMinuteBoundary() {
        assertEquals("1m 30.12s", DungeonSplitsOverlayFeature.formatDurationMillis(90_123L, SplitsConfig.TimeFormat.MINUTES));
        assertEquals("90.12s", DungeonSplitsOverlayFeature.formatDurationMillis(90_123L, SplitsConfig.TimeFormat.SECONDS));
        assertEquals("3600.00s", DungeonSplitsOverlayFeature.formatDurationMillis(3_600_000L, SplitsConfig.TimeFormat.SECONDS));
        assertEquals("0.00s", DungeonSplitsOverlayFeature.formatDurationMillis(0L, SplitsConfig.TimeFormat.SECONDS));
        assertEquals("--", DungeonSplitsOverlayFeature.formatDurationMillis(-1L, SplitsConfig.TimeFormat.SECONDS));
    }

    @Test public void disablingTimeLostRemovesItsColumnAndRowFromEditorBounds() {
        var config = new SplitsConfig();
        var tracker = new DungeonSplitTracker();
        tracker.configureForFloor(1, false);
        config.setScale(100);
        var withLoss = DungeonSplitsOverlayFeature.overlayBounds(config, tracker);
        config.setTimeLost(false);
        var withoutLoss = DungeonSplitsOverlayFeature.overlayBounds(config, tracker);
        assertEquals(withLoss.width() - 60, withoutLoss.width());
        assertEquals(withLoss.height() - 10, withoutLoss.height());
        config.setScale(200);
        assertEquals(withoutLoss.width() * 2, DungeonSplitsOverlayFeature.overlayBounds(config, tracker).width());
        assertEquals(withoutLoss.height() * 2, DungeonSplitsOverlayFeature.overlayBounds(config, tracker).height());
    }

    @Test public void liveOverlayStartsWithTheTimerAndRemainsVisibleThroughCompletion() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.configureForFloor(1, false);
        assertFalse(DungeonSplitsOverlayFeature.isVisible(false, true, tracker));
        assertTrue(DungeonSplitsOverlayFeature.isVisible(true, true, tracker));
        tracker.startRun(0L, 1, false);
        assertTrue(DungeonSplitsOverlayFeature.isVisible(false, true, tracker));
        tracker.observeMessage("Defeated Bonzo in 3m 20s", 0L);
        assertTrue(DungeonSplitsOverlayFeature.isVisible(false, true, tracker));
        assertFalse(DungeonSplitsOverlayFeature.isVisible(false, false, tracker));
        tracker.reset();
        assertFalse(DungeonSplitsOverlayFeature.isVisible(false, true, tracker));
    }

    @Test public void editorUsesTheKnownStartRoomFloorAndOnlyFallsBackForUnknownInstances() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        assertTrue(Arrays.asList(DungeonSplitsOverlayFeature.displayedNames(tracker)).contains("Dragons"));
        tracker.configureForFloor(1, false);
        assertArrayEquals(tracker.splitNames(), DungeonSplitsOverlayFeature.displayedNames(tracker));
        assertTrue(Arrays.asList(DungeonSplitsOverlayFeature.displayedNames(tracker)).contains("Bonzo Phase 2"));
        assertFalse(Arrays.asList(DungeonSplitsOverlayFeature.displayedNames(tracker)).contains("Dragons"));
        var bounds = DungeonSplitsOverlayFeature.overlayBounds(new SplitsConfig(), tracker);
        tracker.startRun(0L, 0, false);
        assertEquals(bounds.height(), DungeonSplitsOverlayFeature.overlayBounds(new SplitsConfig(), tracker).height());
        assertTrue(DungeonSplitsOverlayFeature.isVisible(true, false, tracker));
    }

    @Test public void knownEntranceUsesOnlyClearPhasesInsteadOfTheEditorSample() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.configureKnownFloor(0, false);
        assertTrue(tracker.hasKnownFloor());
        assertArrayEquals(new String[] {"Blood Open", "Blood Clear", "Portal Entry"},
            DungeonSplitsOverlayFeature.displayedNames(tracker));
        tracker.configureForFloor(0, false);
        assertTrue(tracker.hasKnownFloor());
        tracker.startRun(0L, 0, false);
        assertTrue(tracker.hasKnownFloor());
        assertArrayEquals(new String[] {"Blood Open", "Blood Clear", "Portal Entry"}, tracker.splitNames());
    }

    @Test public void showsTheRequestedRealMinusServerTimeAsNegativeSeconds() {
        assertEquals(10_000L, DungeonSplitsOverlayFeature.lostTimeMillis(90_000L, 80_000L));
        assertEquals("-10.0s", DungeonSplitsOverlayFeature.lostTimeSuffix(90_000L, 80_000L));
        assertEquals("-65.4s", DungeonSplitsOverlayFeature.lostTimeSuffix(165_350L, 100_000L));
    }

    @Test public void unknownBoundariesAndServerCatchupDoNotInventLosses() {
        assertEquals("", DungeonSplitsOverlayFeature.lostTimeSuffix(-1L, 80_000L));
        assertEquals("", DungeonSplitsOverlayFeature.lostTimeSuffix(90_000L, -1L));
        assertEquals("", DungeonSplitsOverlayFeature.lostTimeSuffix(80_000L, 90_000L));
        assertEquals("", DungeonSplitsOverlayFeature.lostTimeSuffix(80_000L, 80_000L));
        assertEquals("", DungeonSplitsOverlayFeature.lostTimeSuffix(80_049L, 80_000L));
        assertEquals("-0.1s", DungeonSplitsOverlayFeature.lostTimeSuffix(80_050L, 80_000L));
        assertEquals("--", DungeonSplitsOverlayFeature.formatLostTimeMillis(-1L));
        assertEquals("0.0s", DungeonSplitsOverlayFeature.formatLostTimeMillis(0L));
    }

    @Test public void lossUsesMeasuredTicksAndFreezesWhenTheRunStops() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        tracker.mark("Blood Clear", 0L);
        for (int tick = 0; tick < 1600; tick++) tracker.serverTick(0L);
        clock.set(90_000L);
        tracker.observeMessage("[BOSS] The Watcher: You have proven yourself. You may pass.", 0L);
        var bloodClear = tracker.completedSplits().getLast();
        assertEquals("Blood Clear", bloodClear.name());
        assertEquals("-10.0s", DungeonSplitsOverlayFeature.lostTimeSuffix(
            bloodClear.splitDurationMillis(), bloodClear.serverSplitDurationMillis()));
        tracker.stopRun();
        clock.set(120_000L);
        tracker.serverTick(0L);
        assertEquals("-10.0s", DungeonSplitsOverlayFeature.lostTimeSuffix(
            tracker.currentTotalDurationMillis(), tracker.currentTotalServerDurationMillis()));
    }

    @Test public void editorBoundsIncludeLossColumnAndSummaryRowAtEveryScale() {
        DungeonSplitTracker tracker = new DungeonSplitTracker();
        tracker.startRun(0L, 7, true);
        SplitsConfig config = new SplitsConfig();
        config.setScale(100);
        var bounds = DungeonSplitsOverlayFeature.overlayBounds(config, tracker);
        assertEquals(300, bounds.width());
        assertEquals(6 + (tracker.splitNames().length + 3) * 10, bounds.height());
        config.setScale(200);
        var enlarged = DungeonSplitsOverlayFeature.overlayBounds(config, tracker);
        assertEquals(bounds.width() * 2, enlarged.width());
        assertEquals(bounds.height() * 2, enlarged.height());
    }
}
