package com.github.beng420.kung.feature.dungeon;

import static com.github.beng420.kung.config.category.DungeonConfig.DragonPrioSplit.ARCH;
import static com.github.beng420.kung.config.category.DungeonConfig.DragonPrioSplit.BERS;
import static com.github.beng420.kung.feature.dungeon.DungeonRunStats.DungeonClass.*;
import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig.DragonPrioSplit;
import com.github.beng420.kung.config.category.DungeonConfig.DragonPrioUnit;
import com.google.gson.Gson;
import org.junit.Test;

public final class M7DragonPrioTest {
    private static boolean shown(int hint, DungeonRunStats.DungeonClass self) {
        return M7DragonFeature.prioShown(hint, self, BERS, ARCH, ARCH);
    }

    @Test public void defaultsGiveTheFirstDragonToBersAndTheSecondToArch() {
        // Bers split: Berserker + Mage. Arch split: Archer + Healer + Tank.
        for (var bers : new DungeonRunStats.DungeonClass[] {BERSERKER, MAGE}) {
            assertTrue(bers + " on dragon 1", shown(1, bers));
            assertFalse(bers + " on dragon 2", shown(2, bers));
        }
        for (var arch : new DungeonRunStats.DungeonClass[] {ARCHER, HEALER, TANK}) {
            assertFalse(arch + " on dragon 1", shown(1, arch));
            assertTrue(arch + " on dragon 2", shown(2, arch));
        }
    }

    @Test public void everyLaterDragonIsShownToEveryone() {
        for (int hint : new int[] {3, 4, 5, 12}) {
            for (var self : DungeonRunStats.DungeonClass.values()) assertTrue(self + " on dragon " + hint, shown(hint, self));
        }
    }

    @Test public void anUnknownClassNeverSeesNothing() {
        for (int hint = 1; hint <= 6; hint++) {
            for (var mage : DragonPrioSplit.values()) for (var healer : DragonPrioSplit.values()) {
                for (var tank : DragonPrioSplit.values()) {
                    assertTrue(M7DragonFeature.prioShown(hint, UNKNOWN, mage, healer, tank));
                }
            }
        }
    }

    @Test public void mageHealerAndTankFollowTheirConfiguredSplit() {
        assertFalse(M7DragonFeature.prioShown(1, MAGE, ARCH, ARCH, ARCH));
        assertTrue(M7DragonFeature.prioShown(2, MAGE, ARCH, ARCH, ARCH));
        assertTrue(M7DragonFeature.prioShown(1, HEALER, BERS, BERS, BERS));
        assertFalse(M7DragonFeature.prioShown(2, HEALER, BERS, BERS, BERS));
        assertTrue(M7DragonFeature.prioShown(1, TANK, BERS, BERS, BERS));
        assertFalse(M7DragonFeature.prioShown(2, TANK, BERS, BERS, BERS));
        // The configured splits of the other two classes change nothing for you.
        assertTrue(M7DragonFeature.prioShown(1, MAGE, BERS, ARCH, BERS));
        assertFalse(M7DragonFeature.prioShown(2, MAGE, BERS, BERS, BERS));
    }

    @Test public void archerAndBerserkerIgnoreTheConfiguredSplits() {
        for (var mage : DragonPrioSplit.values()) for (var healer : DragonPrioSplit.values()) {
            for (var tank : DragonPrioSplit.values()) {
                assertFalse(M7DragonFeature.prioShown(1, ARCHER, mage, healer, tank));
                assertTrue(M7DragonFeature.prioShown(2, ARCHER, mage, healer, tank));
                assertTrue(M7DragonFeature.prioShown(1, BERSERKER, mage, healer, tank));
                assertFalse(M7DragonFeature.prioShown(2, BERSERKER, mage, healer, tank));
            }
        }
    }

    @Test public void missingOrBrokenConfigKeysKeepTheDefaults() {
        var dungeon = new Gson().fromJson("{}", KungConfig.class).dungeon;
        assertFalse(dungeon.dragonPrioEnabled());
        assertEquals(BERS, dungeon.dragonPrioMage());
        assertEquals(ARCH, dungeon.dragonPrioHealer());
        assertEquals(ARCH, dungeon.dragonPrioTank());
        assertEquals(DragonPrioUnit.TICKS, dungeon.dragonPrioUnit());
        assertEquals(200, dungeon.dragonPrioScale());
        // A null or unknown name reads back as null from Gson; the getters fall back to the defaults.
        var broken = new Gson().fromJson("""
            {"dungeonMap":{"dragonPrioMage":null,"dragonPrioHealer":"NOPE","dragonPrioTank":"BERS","dragonPrioUnit":"NOPE","dragonPrioScale":9999}}
            """, KungConfig.class).dungeon;
        assertEquals(BERS, broken.dragonPrioMage());
        assertEquals(ARCH, broken.dragonPrioHealer());
        assertEquals(BERS, broken.dragonPrioTank());
        assertEquals(DragonPrioUnit.TICKS, broken.dragonPrioUnit());
        assertEquals(300, broken.dragonPrioScale());
        var nullUnit = new Gson().fromJson("""
            {"dungeonMap":{"dragonPrioUnit":null}}
            """, KungConfig.class).dungeon;
        assertEquals(DragonPrioUnit.TICKS, nullUnit.dragonPrioUnit());
    }

    @Test public void theCountdownIsShownInTicksOrMilliseconds() {
        assertEquals("101", M7DragonFeature.prioText(101, DragonPrioUnit.TICKS));
        assertEquals("5050", M7DragonFeature.prioText(101, DragonPrioUnit.MILLISECONDS));
        assertEquals("0", M7DragonFeature.prioText(0, DragonPrioUnit.TICKS));
        assertEquals("0", M7DragonFeature.prioText(0, DragonPrioUnit.MILLISECONDS));
        assertEquals("50", M7DragonFeature.prioText(1, DragonPrioUnit.MILLISECONDS));
    }

    @Test public void dragonPrioOnlyKeepsTheClassTrackedWhileWitherDragonsIsOn() {
        var config = new Gson().fromJson("{}", KungConfig.class);
        config.dungeon.onChange(() -> { });
        assertFalse(DungeonWorkload.from(config).players());
        config.dungeon.setDragonPrioEnabled(true);
        assertFalse(DungeonWorkload.from(config).players());
        config.dungeon.setWitherDragonsEnabled(true);
        assertTrue(DungeonWorkload.from(config).players());
        assertFalse(DungeonWorkload.from(config).rooms());
        config.dungeon.setDragonPrioEnabled(false);
        assertFalse(DungeonWorkload.from(config).players());
    }

    @Test public void theHudEditorSwitchTurnsTheWholeFeatureOn() {
        var dungeon = new Gson().fromJson("{}", KungConfig.class).dungeon;
        dungeon.onChange(() -> { });
        assertFalse(dungeon.dragonPrioHudShown());
        dungeon.setDragonPrioHudShown(true);
        assertTrue(dungeon.dragonPrioHudShown());
        assertTrue(dungeon.dragonPrioEnabled());
        assertTrue(dungeon.witherDragonsEnabled());
        dungeon.setDragonPrioHudShown(false);
        assertFalse(dungeon.dragonPrioHudShown());
        assertTrue("Hiding the countdown keeps the line", dungeon.dragonPrioEnabled());
    }
}
