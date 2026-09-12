package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class DungeonSplitClockConservationTest {
    @Test
    public void countdownAndMortDiagnosticsDoNotMoveTheRunStartOrItsTickBaseline() {
        AtomicLong clock = new AtomicLong();
        var diagnostics = new ArrayList<String>();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get, diagnostics::add);
        tracker.startRun(0L, 7, true);
        assertFalse(tracker.observeMessage("Starting in 1 second.", 0L));
        for (int tick = 1; tick <= 20; tick++) {
            clock.set(tick * 50L);
            tracker.serverTick(clock.get());
        }
        assertFalse(tracker.observeMessage("[NPC] Mort: Here, I found this map when I first entered the dungeon.", 0L));
        assertEquals(1_000L, tracker.currentTotalDurationMillis());
        assertEquals(1_000L, tracker.currentTotalServerDurationMillis());
        assertTrue(diagnostics.stream().anyMatch(line -> line.startsWith("start-candidate")
            && line.contains("[NPC] Mort:") && line.contains("wallMs=1000 totalTicks=20")));
        tracker.observeMessage("The BLOOD DOOR has been opened!", 0L);
        assertTrue(diagnostics.stream().anyMatch(line -> line.startsWith("phase-end")
            && line.contains("phaseStartTicks=0 totalTicks=20 boundary=\"The BLOOD DOOR has been opened!\"")));
    }

    @Test
    public void reportedRunMustKeepAll9200AcceptedTicksAcrossDialogueAndCompletionBoundaries() throws Exception {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        long oldDisplayedIdeal = 0L;
        try (var reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/dungeon/splits-171838.csv"), StandardCharsets.UTF_8))) {
            for (String row : reader.lines().filter(line -> !line.startsWith("#")).toList()) {
                String[] columns = row.split(",");
                String phase = columns[0];
                long wall = Long.parseLong(columns[1]);
                int ticks = Integer.parseInt(columns[2]);
                oldDisplayedIdeal += Long.parseLong(columns[3]);
                if (!phase.equals("Completion wait")) assertEquals(phase, tracker.currentSplitName());
                long start = clock.get();
                // Spacing is synthetic; the phase boundaries and tick counts
                // are measured. Credited duration must not depend on spacing.
                for (int tick = 1; tick <= ticks; tick++) {
                    clock.set(start + tick * wall / ticks);
                    tracker.serverTick(clock.get());
                }
                assertTrue(tracker.observeMessage(endMessage(phase), 0L));
                if (!phase.equals("Completion wait")) {
                    assertEquals(phase, ticks * 50L, tracker.completedSplits().getLast().serverSplitDurationMillis());
                }
            }
        }
        assertEquals(458_341L, oldDisplayedIdeal);
        assertEquals(473_901L, tracker.currentTotalDurationMillis());
        assertEquals(460_000L, tracker.currentTotalServerDurationMillis());
        assertEquals(13_901L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        clock.set(500_000L);
        tracker.serverTick(clock.get());
        assertEquals(460_000L, tracker.currentTotalServerDurationMillis());
    }

    private static String endMessage(String phase) {
        // Retained chat boundaries from the trace, except Blood Open whose
        // event was evicted; use a supported boundary for its derived count.
        return switch (phase) {
            case "Blood Open" -> "The BLOOD DOOR has been opened!";
            case "Blood Clear" -> "[BOSS] The Watcher: You have proven yourself. You may pass.";
            case "Portal Entry" -> "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!";
            case "Maxor" -> "[BOSS] Storm: Pathetic Maxor, just like expected.";
            case "Storm" -> "[BOSS] Goldor: Who dares trespass into my domain?";
            case "Terminals" -> "The Core entrance is opening!";
            case "Goldor" -> "[BOSS] Necron: You went further than any human before, congratulations.";
            case "Necron" -> "[BOSS] Necron: All this, for nothing...";
            case "Relics" -> "[BOSS] Wither King: You... again?";
            case "Wither King" -> "[BOSS] Wither King: We will decide it all, here, now.";
            case "Dragons" -> "[BOSS] Wither King: Incredible. You did what I couldn't do myself.";
            case "Completion wait" -> "Defeated Maxor, Storm, Goldor, and Necron in 07m 52s";
            default -> throw new AssertionError(phase);
        };
    }

    @Test
    public void healthyTickDeliveryJitterMustNotLoseFractionsOfAcceptedTicksAtEachBoundary() {
        AtomicLong clock = new AtomicLong();
        DungeonSplitTracker tracker = new DungeonSplitTracker(clock::get);
        tracker.startRun(0L, 7, true);
        for (int phase = 0; phase < 10; phase++) {
            for (int tick = 1; tick <= 200; tick++) {
                clock.set(phase * 10_000L + tick * 50L - (tick % 2 == 0 ? 20L : 0L));
                tracker.serverTick(clock.get());
            }
            clock.set((phase + 1) * 10_000L);
            tracker.mark("Phase " + phase, 0L);
            assertEquals(10_000L, tracker.completedSplits().getLast().serverSplitDurationMillis());
            assertEquals(0L, DungeonSplitsOverlayFeature.settledTotalLostTimeMillis(tracker));
        }
    }
}
