package com.github.beng420.kung.feature.misc;

/** Item-use estimate; repeat clicks during the existing shield do not extend its lifetime. */
final class WitherShieldSoundTimer {
    private static final long DURATION_NANOS = 5_000_000_000L;
    private long expiresAt;
    private boolean pending;

    boolean start(long nowNanos) {
        if (pending) {
            return false;
        }
        pending = true;
        expiresAt = nowNanos + DURATION_NANOS;
        return true;
    }

    boolean expire(long nowNanos) {
        if (!pending || nowNanos - expiresAt < 0) {
            return false;
        }
        pending = false;
        return true;
    }

    boolean finish() {
        boolean wasPending = pending;
        pending = false;
        return wasPending;
    }
}
