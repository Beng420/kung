package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.SplitsConfig;
import com.github.beng420.kung.config.category.SplitsConfig.PredictionMode;
import com.github.beng420.kung.config.category.SplitsConfig.PredictionSource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class DungeonSplitAveragesTest {
    private final AtomicLong clock = new AtomicLong();
    private final SplitsConfig config = new SplitsConfig();
    private final DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get, ignored -> { }, config);

    private void after(long millis, String message) {
        clock.addAndGet(millis);
        tracker.observeMessage(message, 0L);
    }

    private void lividRun(long bloodOpen, long bloodClear, long portal, long boss) {
        tracker.startRun(0L, 5, false);
        after(bloodOpen, "The BLOOD DOOR has been opened!");
        after(bloodClear, "[BOSS] The Watcher: You have proven yourself. You may pass.");
        after(portal, "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.");
        after(boss, "Defeated Livid in 1m 30s");
    }

    @Test public void averageOfEachSplitDrivesBothModesAndSourceSwitchesImmediately() {
        config.setEnabled(true);
        config.setTimePrediction(false);
        lividRun(10_000L, 30_000L, 10_000L, 40_000L);
        lividRun(30_000L, 10_000L, 20_000L, 80_000L);
        assertEquals(2, config.recentRunCount(5, false));
        assertEquals(20_000L, config.averageMillis(5, false, "Blood Open"));
        assertEquals(20_000L, config.averageMillis(5, false, "Blood Clear"));
        assertEquals(15_000L, config.averageMillis(5, false, "Portal Entry"));
        assertEquals(60_000L, config.averageMillis(5, false, "Livid"));
        config.setTimePrediction(true);
        tracker.startRun(0L, 5, false);
        assertEquals(70_000L, tracker.predictedFinishMillis());
        config.setPredictionSource(PredictionSource.AVG);
        assertEquals(115_000L, tracker.predictedFinishMillis());
        clock.addAndGet(5_000L);
        assertEquals(115_000L, tracker.predictedFinishMillis());
        config.setPredictionMode(PredictionMode.LIVE);
        assertEquals(100_000L, tracker.predictedFinishMillis());
        config.setPredictionSource(PredictionSource.PB);
        assertEquals(65_000L, tracker.predictedFinishMillis());
        config.setPredictionSource(PredictionSource.AVG);
        after(25_000L, "The BLOOD DOOR has been opened!");
        assertEquals(105_000L, tracker.predictedFinishMillis());
        config.setPredictionMode(PredictionMode.PHASE_END);
        assertEquals(125_000L, tracker.predictedFinishMillis());
        var timings = tracker.currentTimings();
        config.clearRecentRuns(5, false);
        assertEquals(-1L, tracker.predictedFinishMillis());
        config.setPredictionMode(PredictionMode.LIVE);
        assertEquals(-1L, tracker.predictedFinishMillis());
        assertEquals(timings, tracker.currentTimings());
        config.setPredictionSource(PredictionSource.PB);
        assertEquals(80_000L, tracker.predictedFinishMillis());
    }

    @Test public void disabledManualAbortedAndUnknownRunsDoNotBecomeHistory() {
        lividRun(10_000L, 10_000L, 10_000L, 10_000L);
        assertEquals(0, config.recentRunCount(5, false));
        config.setEnabled(true);
        tracker.startRun(0L, 5, false);
        after(10_000L, "The BLOOD DOOR has been opened!");
        tracker.stopRun();
        assertEquals(10_000L, config.personalBestMillis(5, false, "Blood Open"));
        tracker.startRun(0L, 5, false);
        tracker.mark("Livid", 0L);
        after(10_000L, "Defeated Livid in 10s");
        tracker.startRun(0L, -1, false);
        after(10_000L, "The BLOOD DOOR has been opened!");
        after(10_000L, "[BOSS] The Watcher: You have proven yourself. You may pass.");
        after(10_000L, "Defeated The Watcher in 30s");
        assertEquals(0, config.recentRunCount(5, false));
        assertEquals(0, config.recentRunCount(0, false));
    }

    @Test public void lateMasterSevenRecordsOnlyKnownSplitsAndWaitsForTheFinishBanner() {
        config.setEnabled(true);
        tracker.startRun(0L, 7, false);
        after(10_000L, "The BLOOD DOOR has been opened!");
        after(10_000L, "[BOSS] Necron: You went further than any human before, congratulations.");
        after(10_000L, "[BOSS] Necron: All this, for nothing...");
        tracker.configureKnownFloor(7, true);
        after(2_000L, "[BOSS] Wither King: You... again?");
        after(3_000L, "[BOSS] Wither King: We will decide it all, here, now.");
        after(15_000L, "[BOSS] Wither King: Incredible. You did what I couldn't do myself.");
        assertEquals(0, config.recentRunCount(7, true));
        after(2_000L, "Team Score: 300 (S+)");
        after(1L, "Defeated The Wither King in 52s");
        assertEquals(1, config.recentRunCount(7, true));
        assertEquals(0, config.recentRunCount(7, false));
        assertEquals(10_000L, config.averageMillis(7, true, "Blood Open"));
        assertEquals(-1L, config.averageMillis(7, true, "Blood Clear"));
        assertEquals(2_000L, config.averageMillis(7, true, "Relics"));
        assertEquals(15_000L, config.averageMillis(7, true, "Dragons"));
        assertEquals(52_000L, tracker.predictedFinishMillis());
    }

    @Test public void missingAverageNeverBorrowsAPersonalBestOrAnotherFloor() {
        config.recordRun(5, false, Map.of("Blood Open", 10_000L),
            Map.of("Blood Clear", 20_000L, "Portal Entry", 5_000L, "Livid", 40_000L));
        config.setPredictionSource(PredictionSource.AVG);
        tracker.startRun(0L, 5, false);
        assertEquals(-1L, tracker.predictedFinishMillis());
        config.setPredictionMode(PredictionMode.LIVE);
        assertEquals(65_000L, tracker.predictedFinishMillis());
        after(30_000L, "The BLOOD DOOR has been opened!");
        config.setPredictionMode(PredictionMode.PHASE_END);
        assertEquals(95_000L, tracker.predictedFinishMillis());
        tracker.configureKnownFloor(5, true);
        assertEquals(-1L, tracker.predictedFinishMillis());
    }
}
