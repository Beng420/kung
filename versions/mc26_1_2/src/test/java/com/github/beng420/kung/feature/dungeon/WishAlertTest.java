package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.google.gson.Gson;
import org.junit.Test;

public final class WishAlertTest {
    private static final String ENRAGED = "⚠ Maxor is enraged! ⚠";
    private static final DungeonRunStats.DungeonClass HEALER = DungeonRunStats.DungeonClass.HEALER;
    private static final DungeonRunStats.DungeonClass TANK = DungeonRunStats.DungeonClass.TANK;

    private static DungeonConfig config(boolean enabled, boolean ignoreGates) {
        return new Gson().fromJson("{\"dungeonMap\":{\"wishAlertEnabled\":" + enabled
            + ",\"devWishAlertAnyClassEnabled\":" + ignoreGates + "}}", KungConfig.class).dungeon;
    }

    @Test public void alertsOnlyForAHealerWhoseUltimateIsActuallyReady() {
        var on = config(true, false);
        assertEquals("Maxor", WishAlert.enragedBoss(ENRAGED, on, HEALER, 0, false));
        // Level counts the cooldown down, so anything above zero means Wish is still charging.
        assertNull(WishAlert.enragedBoss(ENRAGED, on, HEALER, 1, false));
        assertNull(WishAlert.enragedBoss(ENRAGED, on, TANK, 0, false));
        assertNull(WishAlert.enragedBoss(ENRAGED, config(false, false), HEALER, 0, false));
    }

    @Test public void matchesAnyEnragedBossButNothingElse() {
        var on = config(true, false);
        assertEquals("Storm", WishAlert.enragedBoss("⚠ Storm is enraged! ⚠", on, HEALER, 0, false));
        assertNull(WishAlert.enragedBoss("[BOSS] Maxor: I'M TOO YOUNG TO DIE AGAIN!", on, HEALER, 0, false));
        // A chat quote of the line must not fire it.
        assertNull(WishAlert.enragedBoss("Beng114: ⚠ Maxor is enraged! ⚠", on, HEALER, 0, false));
        assertNull(WishAlert.enragedBoss(null, on, HEALER, 0, false));
    }

    @Test public void lowHealthFiresOncePerDropAndWaitsForWish() {
        var low = new WishAlert.LowHealth();
        assertNull(low.observe("[H] yrkuna 10,000❤", 20, true));
        assertNull(low.observe("[H] yrkuna 2,500❤", 20, true));
        // Below 20% of the most HP seen, but Wish is charging: held back, not consumed.
        assertNull(low.observe("[H] yrkuna 1,900❤", 20, false));
        assertEquals("yrkuna", low.observe("[H] yrkuna 1,900❤", 20, true));
        assertNull(low.observe("[H] yrkuna 1,500❤", 20, true));
        // Back above the line re-arms it for the next drop.
        assertNull(low.observe("[H] yrkuna 6,000❤", 20, true));
        assertEquals("yrkuna", low.observe("[H] yrkuna 1,000❤", 20, true));
        assertNull(low.observe("[H] yrkuna DEAD", 20, true));
        assertNull(low.observe("[M] foo 12.5k❤", 0, true));
        assertEquals("foo", low.observe("[M] foo 2.4k❤", 20, true));
        low.reset();
        assertNull(low.observe("[M] foo 2.4k❤", 20, true));
    }

    @Test public void theDeveloperBypassDropsBothGatesButOnlyForTheDeveloperAccount() {
        var bypass = config(true, true);
        // Developer: any class, ultimate still charging.
        assertEquals("Maxor", WishAlert.enragedBoss(ENRAGED, bypass, TANK, 12, true));
        // Same settings copied to anyone else stay fully gated.
        assertNull(WishAlert.enragedBoss(ENRAGED, bypass, TANK, 12, false));
        assertNull(WishAlert.enragedBoss(ENRAGED, bypass, HEALER, 12, false));
        // The developer account alone is not enough while the flag is off.
        assertNull(WishAlert.enragedBoss(ENRAGED, config(true, false), TANK, 12, true));
        // The bypass never overrides the feature toggle itself.
        assertNull(WishAlert.enragedBoss(ENRAGED, config(false, true), TANK, 12, true));
    }
}
