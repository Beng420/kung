package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import org.junit.Test;

public final class KungUpdateCheckScheduleTest {
    @Test public void checksImmediatelyAndThenEveryThirtySecondsWithoutMenuOrConnectionEvents() {
        var schedule = new KungUpdateCheckSchedule();
        assertTrue(schedule.due(100));
        schedule.started(100);
        assertFalse(schedule.due(101));
        assertFalse(schedule.due(30_099));
        assertTrue(schedule.due(30_100));
        schedule.started(30_100);
        assertFalse(schedule.due(60_099));
        assertTrue(schedule.due(60_100));
    }

    @Test public void repeatedMenuOrJoinChecksCannotPostponeAnOverduePoll() {
        var schedule = new KungUpdateCheckSchedule();
        schedule.started(0);
        for (long now = 1; now < 30_000; now += 50) assertFalse(schedule.due(now));
        assertTrue(schedule.due(30_000));
        assertTrue(schedule.due(90_000)); // Busy worker: do not consume the interval until a check starts.
        schedule.started(90_000);
        assertFalse(schedule.due(90_001));
        assertTrue(schedule.due(120_000));
    }

    @Test public void monotonicClockDoesNotRequireAPositiveEpoch() {
        var schedule = new KungUpdateCheckSchedule();
        assertTrue(schedule.due(-60_000));
        schedule.started(-60_000);
        assertFalse(schedule.due(-30_001));
        assertTrue(schedule.due(-30_000));
    }
}
