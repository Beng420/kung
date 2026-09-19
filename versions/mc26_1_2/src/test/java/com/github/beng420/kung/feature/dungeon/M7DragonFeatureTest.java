package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.runtime.AppServices;
import com.github.beng420.kung.skyblock.HypixelDungeonFloor;
import com.google.gson.Gson;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

public final class M7DragonFeatureTest {
    @Test public void copiedEnabledSettingsCannotRunWithoutTheDeveloperAccount() {
        var config = new Gson().fromJson("""
            {"dungeonMap":{"m7DragonHelperEnabled":true,"devDragonDiagnosticsEnabled":true}}
            """, KungConfig.class);
        var feature = M7DragonFeature.INSTANCE;
        feature.initialize(AppServices.create(config, new DungeonStateTracker()));
        try {
            assertTrue(config.dungeon.m7DragonHelperEnabled());
            assertFalse(feature.isEnabled());
        } finally {
            feature.shutdown();
        }
    }

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

    @Test public void deathLineRecordsOffsetsFromTheSpawnAnchorAndTheCurrentInsideVerdict() {
        // GREEN's anchor is (27, 14, 94); offsets are what the real counting box gets fitted against.
        String line = M7DragonFeature.deathLine(1234, M7DragonTracker.Statue.GREEN,
            M7DragonTracker.Outcome.COUNTS, new Vec3(24.5, 18.0, 81.25));
        assertTrue(line, line.contains("\"tick\":1234"));
        assertTrue(line, line.contains("\"statue\":\"GREEN\""));
        assertTrue(line, line.contains("\"outcome\":\"COUNTS\""));
        assertTrue(line, line.contains("\"dx\":-2.5"));
        assertTrue(line, line.contains("\"dy\":4.0"));
        assertTrue(line, line.contains("\"dz\":-12.75"));
        assertTrue(line, line.contains("\"predictedInside\":true"));
        // A death far outside the estimate must still be recorded, flagged as outside.
        String far = M7DragonFeature.deathLine(9, M7DragonTracker.Statue.GREEN,
            M7DragonTracker.Outcome.UNKNOWN, new Vec3(24.5, 34.0, 81.25));
        assertTrue(far, far.contains("\"predictedInside\":false"));
        assertTrue(far, far.contains("\"outcome\":\"UNKNOWN\""));
    }
}
