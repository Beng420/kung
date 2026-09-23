package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public final class FrozenBlazeHudFeatureTest {
    @Test
    public void onlyAFullSetOfOneKindCounts() {
        assertEquals("Frozen Blaze", FrozenBlazeHudFeature.setName(List.of(
            "FROZEN_BLAZE_HELMET", "FROZEN_BLAZE_CHESTPLATE", "FROZEN_BLAZE_LEGGINGS", "FROZEN_BLAZE_BOOTS")));
        assertEquals("Blaze", FrozenBlazeHudFeature.setName(List.of(
            "BLAZE_HELMET", "BLAZE_CHESTPLATE", "BLAZE_LEGGINGS", "BLAZE_BOOTS")));
        assertNull(FrozenBlazeHudFeature.setName(List.of(
            "FROZEN_BLAZE_HELMET", "BLAZE_CHESTPLATE", "BLAZE_LEGGINGS", "BLAZE_BOOTS")));
        assertNull(FrozenBlazeHudFeature.setName(List.of(
            "", "FROZEN_BLAZE_CHESTPLATE", "FROZEN_BLAZE_LEGGINGS", "FROZEN_BLAZE_BOOTS")));
    }
}
