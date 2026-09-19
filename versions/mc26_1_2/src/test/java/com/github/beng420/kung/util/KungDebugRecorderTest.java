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

    @Test
    public void scoreEvidenceSurvivesMapTrafficWithoutEvictingSplitBoundaries() {
        KungDebugRecorder.clear();
        KungDebugRecorder.event("dungeon-splits", "run-start score-fixture");
        for (int event = 0; event < 257; event++) {
            KungDebugRecorder.event("score-calc", "score-sample=" + event + ";");
        }
        KungDebugRecorder.event("score-bonus", "source=skyblocker bonus=BAT");
        KungDebugRecorder.event("mimic-kill", "evidence source=chat first=true");
        for (int event = 0; event < 2600; event++) KungDebugRecorder.event("test-traffic", "event=" + event);
        KungDebugRecorder.event("score-calc", "score=305 scoreSource=team");
        String dump = KungDebugRecorder.dump();
        assertTrue(dump.contains("run-start score-fixture"));
        assertFalse(dump.contains("score-sample=3;"));
        assertTrue(dump.contains("score-sample=4;"));
        assertTrue(dump.contains("source=skyblocker bonus=BAT"));
        assertTrue(dump.contains("evidence source=chat first=true"));
        assertTrue(dump.indexOf("score-sample=4;") < dump.indexOf("score=305 scoreSource=team"));
        assertEquals(dump.indexOf("score=305 scoreSource=team"), dump.lastIndexOf("score=305 scoreSource=team"));
        assertFalse(KungDebugRecorder.dump(10).contains("score-sample=4;"));
        KungDebugRecorder.clear();
        assertFalse(KungDebugRecorder.dump().contains("source=skyblocker bonus=BAT"));
    }

    @Test
    public void firstNonemptyRoomHashSurvivesEmptyScansDoorNoiseAndLaterTraffic() {
        KungDebugRecorder.clear();
        for (int index = 0; index < 130; index++) {
            KungDebugRecorder.event("map-change", "point scan=" + index + " grid=2,0 previous=none "
                + "next=ROOM:loaded=true:core=-318865360:stable=-318865360:type=UNKNOWN");
        }
        String observation = "initial-room-point scan=131 grid=2,0 "
            + "point=ROOM:loaded=true:world=-153,-185:core=827369333:stable=-1936872710:type=NORMAL";
        KungDebugRecorder.event("map-change", observation);
        for (int index = 0; index < 140; index++) {
            KungDebugRecorder.event("map-change", "point scan=" + index + " grid=2,1 previous=none "
                + "next=DOOR:loaded=true:door=NONE:block=" + index);
        }
        String beforeEviction = KungDebugRecorder.dump();
        assertTrue(beforeEviction.contains(observation));
        assertTrue(beforeEviction.contains("map-change seen=271 kept=121 suppressed=150"));
        for (int event = 0; event < 2600; event++) KungDebugRecorder.event("test-traffic", "event=" + event);
        String dump = KungDebugRecorder.dump();
        assertTrue(dump.contains(observation));
        assertEquals(dump.indexOf(observation), dump.lastIndexOf(observation));
        assertTrue(KungDebugRecorder.dump(5000).contains(observation));
        String tail = KungDebugRecorder.dump(10);
        assertFalse(tail.contains(observation));
        assertEquals(10L, tail.lines().filter(line -> line.matches("\\d+ .*\\[.*")).count());
        KungDebugRecorder.clear();
        assertFalse(KungDebugRecorder.dump().contains(observation));
    }

    @Test
    public void reservedFirstRoomHashesStayBounded() {
        KungDebugRecorder.clear();
        for (int event = 0; event < 129; event++) {
            KungDebugRecorder.event("map-change", "initial-room-point fixture=" + event + "; core=" + event);
        }
        for (int event = 0; event < 2600; event++) KungDebugRecorder.event("test-traffic", "event=" + event);
        String dump = KungDebugRecorder.dump();
        assertFalse(dump.contains("initial-room-point fixture=0;"));
        assertTrue(dump.contains("initial-room-point fixture=1;"));
        assertTrue(dump.contains("initial-room-point fixture=128;"));
    }
}
