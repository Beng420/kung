package com.github.beng420.kung.util;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

public final class KungDebugRecorderTest {
    @After public void clearRecorder() { KungDebugRecorder.clear(); }

    @Test
    public void bowSpamDiagnosticsStayBounded() {
        KungDebugRecorder.clear();
        for (int index = 0; index < 120; index++) KungDebugRecorder.event("bow-draw", "stop heldMs=" + index);
        assertTrue(KungDebugRecorder.dump().contains("bow-draw seen=120 kept=80 suppressed=40"));
    }

    @Test
    public void packetNoiseIsRateLimitedAndSummarized() {
        KungDebugRecorder.clear();
        for (int index = 0; index < 30; index++) {
            char first = (char) ('a' + (index % 26));
            char second = (char) ('a' + (index / 26));
            KungDebugRecorder.event("packet", "PacketKind" + first + second);
        }
        String dump = KungDebugRecorder.dump();
        assertTrue(dump.contains("packet seen=30 kept=12 suppressed=18"));
        assertTrue(dump.contains("suppressed=18"));
        assertTrue(dump.contains("[packet] PacketKindaa"));
        assertFalse(dump.contains("[packet] PacketKinddb"));
    }

    @Test
    public void similarPlayerMarkerFramesCollapseIntoOneUsefulLine() {
        KungDebugRecorder.clear();
        KungDebugRecorder.event("player-markers", "resolved=1 drawn=1 slot=0 mapX=12 mapZ=40 yaw=91.0 class=MAGE");
        KungDebugRecorder.event("player-markers", "resolved=1 drawn=1 slot=0 mapX=13 mapZ=41 yaw=92.0 class=MAGE");
        String dump = KungDebugRecorder.dump();
        assertTrue(dump.contains("player-markers seen=2 kept=1 suppressed=1 duplicate=1"));
        assertTrue(dump.contains("mapX=12"));
        assertFalse(dump.contains("mapX=13"));
    }

    @Test
    public void importantDungeonMessagesBypassMessageRateLimit() {
        KungDebugRecorder.clear();
        for (int index = 0; index < 25; index++) KungDebugRecorder.event("message", "Lobby chatter " + index);
        KungDebugRecorder.event("message", "Starting in 1 second.");
        String dump = KungDebugRecorder.dump();
        assertTrue(dump.contains("message seen=26 kept=21 suppressed=5"));
        assertTrue(dump.contains("[message] Starting in 1 second."));
    }

    @Test
    public void fullTraceRetainsEarlySplitBoundariesAfterGeneralTrafficEvictsThem() {
        KungDebugRecorder.clear();
        KungDebugRecorder.event("dungeon-splits", "run-start fixture-start");
        for (int event = 0; event < 2600; event++) KungDebugRecorder.event("test-traffic", "event=" + event);
        KungDebugRecorder.event("dungeon-splits", "run-finished fixture-end");
        String dump = KungDebugRecorder.dump();
        assertTrue(dump.contains("fixture-start"));
        assertEquals(dump.indexOf("fixture-end"), dump.lastIndexOf("fixture-end"));
        assertTrue(dump.indexOf("fixture-start") < dump.indexOf("fixture-end"));
        assertFalse(KungDebugRecorder.dump(10).contains("fixture-start"));
        KungDebugRecorder.clear();
        assertFalse(KungDebugRecorder.dump().contains("fixture-start"));
    }

    @Test
    public void reservedSplitHistoryIsBounded() {
        KungDebugRecorder.clear();
        for (int event = 0; event < 129; event++) KungDebugRecorder.event("dungeon-splits", "boundary-id=" + event + ";");
        for (int event = 0; event < 2600; event++) KungDebugRecorder.event("test-traffic", "event=" + event);
        String dump = KungDebugRecorder.dump();
        assertFalse(dump.contains("boundary-id=0;"));
        assertTrue(dump.contains("boundary-id=1;"));
        assertTrue(dump.contains("boundary-id=128;"));
    }
}
