package com.github.beng420.kung.feature.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

public final class TarantulaNametagHealthTest {
    @Test
    public void nametagsCarryTheRealHealth() {
        // Mobs name their maximum, a slayer boss does not; the entity's own health is full either way.
        assertArrayEquals(new double[] {795_100, 1_200_000},
            TarantulaHelperFeature.nametagHealth("[Lv90] Magma Cube Rider 795.1k/1.2M❤"), 1.0);
        assertArrayEquals(new double[] {9_400_000, -1},
            TarantulaHelperFeature.nametagHealth("☠ Tarantula Broodfather V 9.4M❤"), 1.0);
        assertArrayEquals(new double[] {6_500, 13_000},
            TarantulaHelperFeature.nametagHealth("[Lv55] Zealot 6,500/13,000❤"), 1.0);
        assertNull(TarantulaHelperFeature.nametagHealth("☠ Tarantula Broodfather V"));
    }
}
