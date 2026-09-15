package com.github.beng420.kung.update;

/** Polling uses monotonic time; opening menus or changing worlds does not restart the interval. */
final class KungUpdateCheckSchedule {
    static final long INTERVAL_MILLIS = 5 * 60_000L;
    private boolean started;
    private long lastStarted;

    boolean due(long nowMillis) {
        return !started || nowMillis - lastStarted >= INTERVAL_MILLIS;
    }

    void started(long nowMillis) {
        started = true;
        lastStarted = nowMillis;
    }
}
