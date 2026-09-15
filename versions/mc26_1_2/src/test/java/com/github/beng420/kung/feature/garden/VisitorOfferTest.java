package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public class VisitorOfferTest {
    private static final Set<String> VISITORS = Set.of("Dulin", "Trinity", "Maeve", "Ludleth", "Taylor");
    private static final List<String> INFO = List.of("", "Rarity: RARE", "", "§7Offers Accepted: §a1,234");
    private static final VisitorQueue.Snapshot FULL = new VisitorQueue.Snapshot(VISITORS, null, true);

    @Test
    public void acceptAndRefuseClicksSilenceWithoutWaitingForVisitorToDisappear() {
        for (String button : List.of("§aAccept Offer", "§cRefuse Offer")) {
            var state = ringing();
            assertTrue(VisitorOffer.isDecision("§9Dulin", button, INFO, VISITORS));
            assertTrue(state.acknowledge());
            assertFalse(state.ringing());
            assertEquals(VISITORS, state.queue().visitors());
            state.tick(1_000);
            state.observe(FULL, 5_000);
            state.reduce(30_000, 6_000);
            state.seed(360_000, 0, 7_000);
            state.observe(null, 8_000);
            state.tick(1_000_000);
            assertFalse("Unchanged, delayed or missing data cannot restart the acknowledged alarm", state.ringing());
        }
    }

    @Test
    public void nextCycleCanRingAfterImmediateReplacementAtFiveVisitors() {
        var state = ringing();
        state.acknowledge();
        var replaced = new VisitorQueue.Snapshot(Set.of("Beth", "Trinity", "Maeve", "Ludleth", "Taylor"), null, true);
        state.observe(replaced, 10_000);
        state.tick(369_999);
        assertFalse(state.ringing());
        state.tick(370_000);
        assertTrue(state.ringing());
    }

    @Test
    public void clicksBeforeAnAlarmAndResetDoNotSuppressFutureAlarms() {
        var state = new VisitorAlarmState();
        state.observe(FULL, 0);
        assertFalse(state.acknowledge());
        state.seed(360_000, 0, 0);
        assertTrue(state.ringing());
        state.acknowledge();
        state.reset();
        state.observe(FULL, 1_000);
        state.seed(360_000, 0, 1_000);
        assertTrue(state.ringing());
    }

    @Test
    public void unrelatedMenuControlsAndOffersDoNotAcknowledge() {
        for (String button : List.of("Close", "Go Back", "Visitor Details", "Confirm", "Accept Offer Now", "")) {
            assertFalse(button, VisitorOffer.isDecision("Dulin", button, INFO, VISITORS));
        }
        assertFalse(VisitorOffer.isDecision("Auction House", "Accept Offer", INFO, VISITORS));
        assertFalse(VisitorOffer.isDecision("Dulin", "Accept Offer", List.of("Cost: 1,000 Coins"), VISITORS));
        assertFalse(VisitorOffer.isDecision("Dulin", "Refuse Offer", List.of(), VISITORS));
        assertFalse(VisitorOffer.isDecision("Dulin", "Accept Offer", INFO, Set.of()));
    }

    private static VisitorAlarmState ringing() {
        var state = new VisitorAlarmState();
        state.observe(FULL, 0);
        state.seed(360_000, 0, 0);
        assertTrue(state.ringing());
        return state;
    }
}
