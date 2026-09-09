package com.github.beng420.kung.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KungDebugRecorderTest {
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

        KungDebugRecorder.event(
            "player-markers",
            "resolved=1 drawn=1 slot=0 mapX=12 mapZ=40 yaw=91.0 class=MAGE"
        );
        KungDebugRecorder.event(
            "player-markers",
            "resolved=1 drawn=1 slot=0 mapX=13 mapZ=41 yaw=92.0 class=MAGE"
        );

        String dump = KungDebugRecorder.dump();

        assertTrue(dump.contains("player-markers seen=2 kept=1 suppressed=1 duplicate=1"));
        assertTrue(dump.contains("mapX=12"));
        assertFalse(dump.contains("mapX=13"));
    }

    @Test
    public void importantDungeonMessagesBypassMessageRateLimit() {
        KungDebugRecorder.clear();

        for (int index = 0; index < 25; index++) {
            KungDebugRecorder.event("message", "Lobby chatter " + index);
        }
        KungDebugRecorder.event("message", "Starting in 1 second.");

        String dump = KungDebugRecorder.dump();

        assertTrue(dump.contains("message seen=26 kept=21 suppressed=5"));
        assertTrue(dump.contains("[message] Starting in 1 second."));
    }
}
