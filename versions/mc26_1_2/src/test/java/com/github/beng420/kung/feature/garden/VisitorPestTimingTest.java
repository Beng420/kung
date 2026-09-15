package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public class VisitorPestTimingTest {
    // Reward burst from the supplied 2026-09-14 20:38:50 live incident.
    private static final List<String> LIVE_REWARDS = List.of(
        "You received 46x Enchanted Brown Mushroom for killing a Slug!",
        "You received 48x Enchanted Pumpkin for killing a Rat!",
        "You received 2x Tasty Cheese for killing a Field Mouse!",
        "You received 2x Honey Jar for killing a Field Mouse!",
        "You received 2x Plant Matter for killing a Field Mouse!",
        "You received 145x Enchanted Cactus Green for killing a Field Mouse!",
        "You received 2x Compost for killing a Field Mouse!",
        "You received 2x Dung for killing a Field Mouse!",
        "You received 2x Jelly for killing a Field Mouse!",
        "You received 96x Enchanted Cactus Green for killing a Mite!",
        "You received 97x Enchanted Cactus Green for killing a Mite!",
        "You received 49x Enchanted Pumpkin for killing a Rat!",
        "You received 48x Enchanted Wheat for killing a Fly!",
        "You received 95x Enchanted Sugar for killing a Mosquito!"
    );

    @Test
    public void liveLootBurstLeavesSixthVisitorCountdownRunning() {
        var state = new VisitorAlarmState();
        state.observe(new VisitorQueue.Snapshot(Set.of("Taylor", "Ludleth", "Emissary Wilson", "Ryu", "Maeve"), null, true), 0);
        state.seed(360_000, 360_000, 0);
        long elapsed = 3_913; // 20:38:46.847 -> 20:38:50.760
        int kills = 0;
        for (String reward : LIVE_REWARDS) {
            if (VisitorQueue.pestKill(reward)) {
                kills++;
                state.reduce(30_000, elapsed);
            }
        }
        assertEquals("14 loot messages represent eight kills, including one Field Mouse", 8, kills);
        assertEquals(116_087, state.remaining(elapsed));
        assertFalse("The reported burst must not trigger the alarm", state.ringing());
        state.tick(119_999);
        assertFalse(state.ringing());
        state.tick(120_000);
        assertTrue("Still alarms once the actual predicted deadline is reached", state.ringing());
    }

    @Test
    public void fieldMouseCountsDungOnceRegardlessOfBonusOrderAndQuantity() {
        assertTrue(VisitorQueue.pestKill("§eYou received §a2x Dung §efor killing a §2Field Mouse§e!"));
        for (String item : List.of("Tasty Cheese", "Honey Jar", "Plant Matter", "Enchanted Cactus Green", "Compost", "Jelly")) {
            assertFalse(item, VisitorQueue.pestKill("You received 2x " + item + " for killing a Field Mouse!"));
        }
        // Different mice may produce identical lines in the same instant; no time-window/name deduplication.
        assertTrue(VisitorQueue.pestKill("You received 2x Dung for killing a Field Mouse!"));
        assertTrue(VisitorQueue.pestKill("You received 2x Dung for killing a Field Mouse!"));
    }

    @Test
    public void lunarMothAndOverclockerExtrasDoNotMultiplyKills() {
        assertTrue(VisitorQueue.pestKill("[20:38:50] You received 32x Enchanted Sunflower for killing a Lunar Moth!"));
        assertFalse(VisitorQueue.pestKill("You received 32x Enchanted Wild Rose for killing a Lunar Moth!"));
        assertFalse(VisitorQueue.pestKill("You received 32x Enchanted Moonflower for killing a Lunar Moth!"));
        assertFalse(VisitorQueue.pestKill("You received 1x Overclocker 3000 for killing a Beetle!"));
        assertFalse(VisitorQueue.pestKill("Party > Ben: You received 2x Dung for killing a Field Mouse!"));
    }
}
