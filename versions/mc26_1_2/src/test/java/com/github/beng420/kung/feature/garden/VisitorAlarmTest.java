package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public class VisitorAlarmTest {
    private static final Set<String> FIVE = Set.of("Maeve", "Liam", "Stella", "Jack", "Beth");
    private static VisitorQueue.Snapshot full() { return new VisitorQueue.Snapshot(FIVE, null, true); }
    private static VisitorQueue.Snapshot four(long timer) {
        return new VisitorQueue.Snapshot(Set.of("Maeve", "Liam", "Stella", "Jack"), timer, false);
    }

    @Test
    public void guestGardensAndIncompleteWorldTransfersCannotTrigger() {
        assertTrue(VisitorQueue.ownGarden("Garden", List.of("§eSKYBLOCK ♲"), List.of()));
        assertFalse(VisitorQueue.ownGarden("Garden", List.of("§eSKYBLOCK GUEST"), List.of()));
        assertFalse(VisitorQueue.ownGarden("Garden", List.of(), List.of("Area: Garden")));
        assertFalse(VisitorQueue.ownGarden("Hub", List.of("SKYBLOCK"), List.of("Area: Hub")));
    }

    @Test
    public void readsServerWidgetWithFormattingAndCountdown() {
        var queue = VisitorQueue.parse(List.of("Stats:", "§b§lVisitors: §r§f(5)", " §aMaeve", " §9Liam",
            " §6Stella", " §aJack", " §dBeth", " Next Visitor: §e1m 23s", "Guests (0)"));
        assertNotNull(queue);
        assertEquals(FIVE, queue.visitors());
        assertEquals(Long.valueOf(83_000), queue.remainingMillis());
        assertFalse(queue.full());
    }

    @Test
    public void queueFullAndZeroVisitorsAreDifferentFromUnknown() {
        assertTrue(VisitorQueue.parse(List.of("Visitors: (5)", "Maeve", "Liam", "Stella", "Jack", "Beth",
            "Next Visitor: Queue Full!")).full());
        assertEquals(0, VisitorQueue.parse(List.of("Visitors: (0)", "Next Visitor: 14m 2s")).count());
        assertNull(VisitorQueue.parse(List.of("Visitors: (5)", "Maeve", "Liam", "Next Visitor: Queue Full!")));
        assertNull(VisitorQueue.parse(List.of("Visitors: (5)", "Maeve", "Liam", "Stella", "Jack", "Jack",
            "Next Visitor: Queue Full!")));
        assertNull(VisitorQueue.parse(List.of("Party > Ben: Visitors: (5)", "Next Visitor: Queue Full!")));
        assertNull(VisitorQueue.parse(List.of("Visitors: (0)", "Next Visitor: Not Unlocked!")));
    }

    @Test
    public void parsesOnlyCompleteBoundedTimersAndPestConfirmations() {
        assertEquals(Long.valueOf(0), VisitorQueue.duration("0s"));
        assertEquals(Long.valueOf(900_000), VisitorQueue.duration("15m"));
        assertEquals(Long.valueOf(61_000), VisitorQueue.duration("1m1s"));
        for (String invalid : List.of("", "Soon", "1m 99s", "99999999999s", "-1s", "1m left", "Queue Full!")) {
            assertNull(invalid, VisitorQueue.duration(invalid));
        }
        assertTrue(VisitorQueue.pestKill("§eYou received §a128x Enchanted Hay Bale §efor killing a §2Fly§e!"));
        assertTrue(VisitorQueue.pestKill("[17:37:21] You received 2x Enchanted Carrot for killing an Earthworm!"));
        assertFalse(VisitorQueue.pestKill("Party > Ben: You received 2x Enchanted Carrot for killing an Earthworm!"));
        assertFalse(VisitorQueue.pestKill("RARE DROP! Pesterminator I"));
    }

    @Test
    public void fifthArrivalDoesNotImmediatelyMeanSixthReady() {
        var state = new VisitorAlarmState();
        state.observe(four(1_000), 0);
        state.observe(full(), 1_000);
        assertFalse(state.ringing());
        assertEquals(900_000, state.remaining(1_000));
        state.tick(900_999);
        assertFalse(state.ringing());
        state.tick(901_000);
        assertTrue(state.ringing());
    }

    @Test
    public void learnsPersonalIntervalWhenVisitorArrivesBeforeQueueFills() {
        var state = new VisitorAlarmState();
        state.observe(new VisitorQueue.Snapshot(Set.of("Maeve", "Liam", "Stella"), 1_000L, false), 0);
        state.observe(four(600_000), 1_000);
        assertEquals(600_000, state.interval());
        state.observe(full(), 601_000);
        state.tick(1_200_999);
        assertFalse(state.ringing());
        state.tick(1_201_000);
        assertTrue(state.ringing());
    }

    @Test
    public void unchangedTabUpdatesNeverPostponeCountdown() {
        var state = new VisitorAlarmState();
        state.observe(new VisitorQueue.Snapshot(FIVE, 1_000L, false), 0);
        state.observe(new VisitorQueue.Snapshot(FIVE, 1_000L, false), 500);
        state.tick(1_000);
        assertTrue(state.ringing());
        state.observe(full(), 2_000);
        state.tick(60_000_000);
        state.observe(null, 60_000_001);
        assertTrue(state.ringing());
    }

    @Test
    public void fourVisitorsWithExpiredTimerNeverRing() {
        var state = new VisitorAlarmState();
        state.observe(four(0), 0);
        state.tick(1_000_000);
        assertFalse(state.ringing());
    }

    @Test
    public void farmingAndPestsAccelerateOnlyHiddenTimer() {
        var state = new VisitorAlarmState();
        state.observe(full(), 0);
        state.seed(600_000, 30_200, 0);
        state.reduce(30_000, 0);
        state.reduce(100, 0);
        assertFalse(state.ringing());
        state.reduce(100, 0);
        assertTrue(state.ringing());
        state.reset();
        state.observe(four(10_000), 0);
        state.reduce(30_000, 0);
        assertEquals(10_000, state.remaining(0));
    }

    @Test
    public void acceptedOrDeclinedVisitorStopsAlarmEvenWithImmediateReplacement() {
        var state = new VisitorAlarmState();
        state.observe(full(), 0);
        state.seed(600_000, 0, 0);
        assertTrue(state.ringing());
        var replaced = new VisitorQueue.Snapshot(Set.of("Maeve", "Liam", "Stella", "Jack", "Duke"), null, true);
        state.observe(replaced, 1_000);
        assertFalse(state.ringing());
        state.observe(replaced, 1_500);
        assertFalse(state.ringing());
        state.tick(601_000);
        assertTrue(state.ringing());
        state.observe(four(20_000), 602_000);
        assertFalse(state.ringing());
    }

    @Test
    public void disableDisconnectOrProfileResetClearsEverything() {
        var state = new VisitorAlarmState();
        state.observe(full(), 0);
        state.seed(600_000, 0, 0);
        state.reset();
        state.tick(10_000_000);
        assertFalse(state.ringing());
        assertNull(state.queue());
        assertEquals(-1, state.remaining(0));
    }

    @Test
    public void optionalStartupTimerRejectsSentinelsAndAcceptsKnownReadyState() {
        long now = 1_800_000_000_000L;
        assertNull(VisitorTimerCompatibility.validate(600_000, 0, now));
        assertNull(VisitorTimerCompatibility.validate(600_000, Long.MAX_VALUE, now));
        assertNull(VisitorTimerCompatibility.validate(600_000, now - 86_400_001, now));
        assertNull(VisitorTimerCompatibility.validate(600_000, now + 900_001, now));
        assertNull(VisitorTimerCompatibility.validate(-1, now, now));
        assertEquals(0, VisitorTimerCompatibility.validate(600_000, now - 1_000, now).remainingMillis());
        assertEquals(45_000, VisitorTimerCompatibility.validate(600_000, now + 45_000, now).remainingMillis());
    }
}
