package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.SplitsConfig;
import com.github.beng420.kung.message.KungMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class DungeonSplitMessagesTest {
    private static final String BLOOD = "The BLOOD DOOR has been opened!";
    private static final String CLEAR = "[BOSS] The Watcher: You have proven yourself. You may pass.";
    private final AtomicLong clock = new AtomicLong();
    private final SplitsConfig config = new SplitsConfig();
    private final List<DungeonSplitTracker.PhaseMessage> phases = new ArrayList<>();
    private final DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get, ignored -> { }, config, phases::add);

    @Test public void eachFinishedPhaseReportsTimeAndOnlyNewOrStrictlyFasterTimesCelebrate() {
        config.setEnabled(true);
        for (long duration : new long[] {30_000L, 30_000L, 31_000L, 29_000L}) {
            tracker.startRun(0L, 6, false);
            clock.addAndGet(duration);
            tracker.observeMessage(BLOOD, 0L);
            tracker.observeMessage(BLOOD, 0L); // Duplicate server notification.
            tracker.stopRun();
        }
        assertEquals(4, phases.size());
        assertTrue(phases.get(0).personalBest());
        assertFalse(phases.get(1).personalBest());
        assertFalse(phases.get(2).personalBest());
        assertTrue(phases.get(3).personalBest());
        assertEquals(List.of(-1L, 30_000L, 30_000L, 30_000L),
            phases.stream().map(DungeonSplitTracker.PhaseMessage::previousBestMillis).toList());
        assertEquals(29_000L, config.personalBestMillis(6, false, "Blood Open"));
        var first = DungeonSplitMessages.notices(phases.getFirst());
        assertEquals(List.of(
            new DungeonSplitMessages.Notice(KungMessages.Type.INFO, "F6 Blood Open: 30.00s (PB: 30.00s)"),
            new DungeonSplitMessages.Notice(KungMessages.Type.SUCCESS,
                "F6 Blood Open: 30.00s (Previous PB: --)")), first);
        assertEquals("[Kung Splits] F6 Blood Open: 30.00s (PB: 30.00s)",
            first.getFirst().component().getString());
        assertEquals(List.of(new DungeonSplitMessages.Notice(KungMessages.Type.INFO,
            "F6 Blood Open: 30.00s (PB: 30.00s)")), DungeonSplitMessages.notices(phases.get(1)));
        assertEquals(List.of(new DungeonSplitMessages.Notice(KungMessages.Type.INFO,
            "F6 Blood Open: 31.00s (PB: 30.00s)")), DungeonSplitMessages.notices(phases.get(2)));
        assertEquals(List.of(
            new DungeonSplitMessages.Notice(KungMessages.Type.INFO, "F6 Blood Open: 29.00s (PB: 29.00s)"),
            new DungeonSplitMessages.Notice(KungMessages.Type.SUCCESS,
                "F6 Blood Open: 29.00s (Previous PB: 30.00s)")),
            DungeonSplitMessages.notices(phases.getLast()));
    }

    @Test public void recordsAndMessagesUseOnlyTheMatchingFloorAndMode() {
        config.setEnabled(true);
        config.recordPersonalBests(6, false, Map.of("Blood Open", 20_000L));
        config.recordPersonalBests(7, false, Map.of("Blood Open", 25_000L));
        config.recordPersonalBests(7, true, Map.of("Blood Open", 40_000L));
        for (int run = 0; run < 3; run++) {
            tracker.startRun(0L, run == 0 ? 6 : 7, run == 2);
            clock.addAndGet(30_000L);
            tracker.observeMessage(BLOOD, 0L);
            tracker.stopRun();
        }
        assertFalse(phases.get(0).personalBest());
        assertFalse(phases.get(1).personalBest());
        assertTrue(phases.get(2).personalBest());
        assertEquals(List.of(20_000L, 25_000L, 40_000L),
            phases.stream().map(DungeonSplitTracker.PhaseMessage::previousBestMillis).toList());
        assertEquals("PERSONAL BEST! M7 Blood Open: 30.00s (Previous PB: 40.00s)",
            DungeonSplitMessages.notices(phases.get(2)).getLast().text());
    }

    @Test public void finalPhaseWaitsForVictoryAfterScoreAndAnnouncesOnceUsingTheFrozenTime() {
        config.setEnabled(true);
        config.recordPersonalBests(1, false, Map.of("Bonzo Phase 2", 4_000L));
        tracker.startRun(0L, 1, false);
        clock.set(5_000L);
        tracker.observeMessage("[BOSS] Bonzo: Oh I'm dead!", 0L);
        assertTrue(phases.isEmpty()); // Skipped clear/first boss boundaries cannot supply a measured phase.
        clock.set(8_000L);
        tracker.observeMessage("Team Score: 177 (B)", 0L);
        tracker.stopRun();
        assertTrue(phases.isEmpty());
        clock.set(8_003L);
        tracker.observeMessage("☠ Defeated Bonzo in 8s", 0L);
        tracker.observeMessage("☠ Defeated Bonzo in 8s", 0L);
        tracker.observeMessage("Team Score: 177 (B)", 0L);
        tracker.stopRun();
        tracker.reset();
        assertEquals(1, phases.size());
        assertEquals("Bonzo Phase 2", phases.getFirst().phase());
        assertEquals(3_000L, phases.getFirst().durationMillis());
        assertTrue(phases.getFirst().personalBest());
        assertEquals(3_000L, config.personalBestMillis(1, false, "Bonzo Phase 2"));
        assertEquals(4_000L, phases.getFirst().previousBestMillis());
        assertEquals("PERSONAL BEST! F1 Bonzo Phase 2: 3.00s (Previous PB: 4.00s)",
            DungeonSplitMessages.notices(phases.getFirst()).getLast().text());
    }

    @Test public void confirmedBossDeathAndOrdinaryVictoryDoNotAnnounceAgainAtTheBanner() {
        config.setEnabled(true);
        tracker.startRun(0L, 7, false);
        clock.set(5_000L);
        tracker.observeMessage("[BOSS] Necron: You went further than any human before, congratulations.", 0L);
        clock.set(10_000L);
        tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 0L);
        assertEquals(1, phases.size());
        tracker.observeMessage("[BOSS] Necron: All this, for nothing...", 0L);
        clock.set(12_000L);
        tracker.observeMessage("Defeated Necron in 12s", 0L);
        tracker.observeMessage("Team Score: 300 (S+)", 0L);
        assertEquals(1, phases.size());
        assertEquals(5_000L, phases.getFirst().durationMillis());
        tracker.startRun(0L, 1, false);
        clock.addAndGet(5_000L);
        tracker.observeMessage("[BOSS] Bonzo: Oh I'm dead!", 0L);
        clock.addAndGet(3_000L);
        tracker.observeMessage("Defeated Bonzo in 8s", 0L);
        tracker.observeMessage("Team Score: 177 (B)", 0L);
        assertEquals(2, phases.size());
        assertEquals(3_000L, phases.getLast().durationMillis());
        assertTrue(phases.getLast().personalBest());
    }

    @Test public void disabledManualSkippedAndAbortedPhasesStaySilent() {
        tracker.startRun(0L, 6, false);
        clock.addAndGet(1_000L);
        tracker.observeMessage(BLOOD, 0L);
        tracker.stopRun();
        assertTrue(phases.isEmpty());
        config.setEnabled(true);
        tracker.startRun(0L, 6, false);
        clock.addAndGet(1_000L);
        tracker.observeMessage(CLEAR, 0L); // Unknown Blood boundary.
        tracker.stopRun();
        assertTrue(phases.isEmpty());
        tracker.startRun(0L, 6, false);
        clock.addAndGet(1_000L);
        tracker.mark("Blood Clear", 0L);
        clock.addAndGet(1_000L);
        tracker.observeMessage(CLEAR, 0L);
        tracker.stopRun();
        assertTrue(phases.isEmpty());
        tracker.startRun(0L, 1, false);
        clock.addAndGet(1_000L);
        tracker.observeMessage("[BOSS] Bonzo: Oh I'm dead!", 0L);
        clock.addAndGet(1_000L);
        tracker.observeMessage("Team Score: 100 (D)", 0L);
        clock.addAndGet(5_001L);
        tracker.observeMessage("Defeated Bonzo in 2s", 0L);
        assertTrue(phases.isEmpty());
    }

    @Test public void predictionVisibilityDoesNotMuteMessagesAndTheyUseTheConfiguredTimeFormat() {
        config.setEnabled(true);
        config.setTimePrediction(false);
        tracker.startRun(0L, 6, false);
        clock.addAndGet(90_120L);
        tracker.observeMessage(BLOOD, 0L);
        config.setFormat(SplitsConfig.TimeFormat.SECONDS);
        clock.addAndGet(91_230L);
        tracker.observeMessage(CLEAR, 0L);
        assertEquals("F6 Blood Open: 1m 30.12s (PB: 1m 30.12s)",
            DungeonSplitMessages.notices(phases.getFirst()).getFirst().text());
        assertEquals("F6 Blood Clear: 91.23s (PB: 91.23s)",
            DungeonSplitMessages.notices(phases.getLast()).getFirst().text());
    }

    @Test public void unknownFloorCanReportTimeButCannotClaimAFloorSpecificRecord() {
        config.setEnabled(true);
        tracker.startRun(0L, -1, false);
        clock.addAndGet(30_000L);
        tracker.observeMessage(BLOOD, 0L);
        assertEquals(1, phases.size());
        assertFalse(phases.getFirst().personalBest());
        assertEquals("Blood Open: 30.00s (PB: --)", DungeonSplitMessages.notices(phases.getFirst()).getFirst().text());
    }
}
