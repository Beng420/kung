package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class SackTrackerFeatureTest {
    @Test
    public void sackMenusCountsAndMessagesParse() {
        assertTrue(SackTrackerFeature.isSack("Large Enchanted Agronomy Sack"));
        assertFalse(SackTrackerFeature.isSack("Sack of Sacks"));

        assertEquals(Long.valueOf(1234), SackTrackerFeature.stored(List.of("Item", " Stored: 1,234/20.2k", "Click to pickup!")));
        assertNull(SackTrackerFeature.stored(List.of(" Rough: 12", " Flawed: 3")));
        assertEquals(Long.valueOf(1_500_000), SackTrackerFeature.parseAmount("1.5m"));
        assertEquals(Long.valueOf(10_000), SackTrackerFeature.parseAmount("10,000"));
        assertNull(SackTrackerFeature.parseAmount(""));

        // Added and removed lists net out per item; everything else in the hover is ignored.
        assertEquals(Map.of("Wheat", 1270L, "Cobblestone", -10L), SackTrackerFeature.changes(List.of(
            "Added items:\n  +1,280 Wheat (Agronomy Sack)\n\nThis message can be disabled in the settings.",
            "Removed items:\n  -10 Wheat (Agronomy Sack)\n  -10 Cobblestone (Mining Sack, Large Mining Sack)")));
    }
}
