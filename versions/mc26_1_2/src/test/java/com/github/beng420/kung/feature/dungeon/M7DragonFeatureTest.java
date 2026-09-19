package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.skyblock.HypixelDungeonFloor;
import org.junit.Test;

public final class M7DragonFeatureTest {
    @Test public void explicitFloorWinsOverBossFallbackAndRequiresDungeonInstance() {
        for (int number = 0; number <= 7; number++) {
            for (boolean master : new boolean[] {false, true}) {
                var floor = new HypixelDungeonFloor(number, master);
                for (boolean bossEvidence : new boolean[] {false, true}) {
                    assertEquals(number == 7 && master, M7DragonFeature.contextAllowed(true, floor, bossEvidence));
                    assertFalse(M7DragonFeature.contextAllowed(false, floor, bossEvidence));
                }
            }
        }
    }

    @Test public void missingFloorNeedsWitherKingEvidenceInTheCurrentDungeon() {
        assertFalse(M7DragonFeature.contextAllowed(true, HypixelDungeonFloor.UNKNOWN, false));
        assertTrue(M7DragonFeature.contextAllowed(true, HypixelDungeonFloor.UNKNOWN, true));
        assertFalse(M7DragonFeature.contextAllowed(false, HypixelDungeonFloor.UNKNOWN, true));
    }
}
