package com.github.beng420.kung.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ServerTpsTrackerTest {
    @Test
    public void reportsUnavailableBeforeTwoTicks() {
        ServerTpsTracker tracker = ServerTpsTracker.INSTANCE;
        tracker.reset();

        assertFalse(tracker.snapshot().available());
        assertEquals("TPS data is not ready yet.", tracker.snapshot().message());
    }

    @Test
    public void formatsCurrentMaxMinAndAverageTps() {
        ServerTpsTracker tracker = ServerTpsTracker.INSTANCE;
        tracker.reset();

        long now = 0L;
        for (int tick = 0; tick <= 40; tick++) {
            tracker.recordTickAtNanos(now);
            now += 50_000_000L;
        }

        ServerTpsTracker.TpsSnapshot snapshot = tracker.snapshot();

        assertTrue(snapshot.available());
        assertEquals("Current: 20.0 (max/min/avg) 20.0/20.0/20.0", snapshot.message());
    }
}
