package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import org.junit.Test;

public final class KungUpdateCheckScheduleTest {
    @Test public void checksImmediatelyAndThenEveryFiveMinutesWithoutMenuOrConnectionEvents() {
        var schedule = new KungUpdateCheckSchedule();
        assertTrue(schedule.due(100));
        schedule.started(100);
        assertFalse(schedule.due(101));
        assertFalse(schedule.due(300_099));
        assertTrue(schedule.due(300_100));
        schedule.started(300_100);
        assertFalse(schedule.due(600_099));
        assertTrue(schedule.due(600_100));
    }

    @Test public void repeatedMenuOrJoinChecksCannotPostponeAnOverduePoll() {
        var schedule = new KungUpdateCheckSchedule();
        schedule.started(0);
        for (long now = 1; now < 300_000; now += 50) assertFalse(schedule.due(now));
        assertTrue(schedule.due(300_000));
        assertTrue(schedule.due(900_000)); // Busy worker: do not consume the interval until a check starts.
        schedule.started(900_000);
        assertFalse(schedule.due(900_001));
        assertTrue(schedule.due(1_200_000));
    }

    @Test public void monotonicClockDoesNotRequireAPositiveEpoch() {
        var schedule = new KungUpdateCheckSchedule();
        assertTrue(schedule.due(-600_000));
        schedule.started(-600_000);
        assertFalse(schedule.due(-300_001));
        assertTrue(schedule.due(-300_000));
    }
}
