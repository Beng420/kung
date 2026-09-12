package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WitherShieldSoundTimerTest {
    @Test
    public void repeatedHyperionClicksDoNotPostponeExpiry() {
        WitherShieldSoundTimer timer = new WitherShieldSoundTimer();
        assertTrue(timer.start(0));
        assertFalse(timer.start(147_000_000));
        assertFalse(timer.start(298_000_000));
        assertFalse(timer.expire(4_999_999_999L));
        assertTrue(timer.expire(5_000_000_000L));
        assertFalse(timer.expire(5_100_000_000L));
    }

    @Test
    public void confirmedEndAndWorldResetConsumeThePendingSoundOnlyOnce() {
        WitherShieldSoundTimer timer = new WitherShieldSoundTimer();
        timer.start(0);
        assertTrue(timer.finish());
        assertFalse(timer.finish());
        assertFalse(timer.expire(6_000_000_000L));
        assertTrue(timer.start(6_000_000_000L));
        assertTrue(timer.expire(11_000_000_000L));
    }
}
