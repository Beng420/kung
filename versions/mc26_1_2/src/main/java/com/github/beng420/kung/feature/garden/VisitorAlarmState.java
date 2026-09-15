package com.github.beng420.kung.feature.garden;

import java.util.Objects;

/** Client-thread state. Deadlines and reductions are milliseconds on a monotonic clock. */
final class VisitorAlarmState {
    private static final long DEFAULT_INTERVAL = 15 * 60_000L;
    private long interval = DEFAULT_INTERVAL;
    private long deadline = -1;
    private long nextReminder = -1;
    private VisitorQueue.Snapshot queue;
    private boolean ringing;
    private boolean acknowledged;

    void observe(VisitorQueue.Snapshot next, long now) {
        if (next == null) return;
        boolean departed = queue != null && !next.visitors().containsAll(queue.visitors());
        boolean arrived = queue != null && !queue.visitors().containsAll(next.visitors());
        if (departed) {
            ringing = false;
            acknowledged = false;
            deadline = -1;
            nextReminder = -1;
        }
        if (next.remainingMillis() != null) {
            if (arrived && next.remainingMillis() > 0) interval = next.remainingMillis();
            if (queue == null || departed || arrived || queue.full()
                || !Objects.equals(queue.remainingMillis(), next.remainingMillis())) {
                deadline = now + next.remainingMillis();
            }
        } else if (next.full() && next.count() == 5 && (deadline < 0 || arrived)) {
            // Full hides the timer at the FIFTH arrival. It does not mean the sixth is ready.
            // On an already-full first observation, a full interval is a conservative upper bound.
            deadline = now + interval;
        }
        queue = next;
        tick(now);
    }

    void tick(long now) {
        if (!acknowledged && queue != null && queue.count() == 5 && deadline >= 0 && now >= deadline) {
            ringing = true;
        }
    }

    boolean acknowledge() {
        if (!ringing) return false;
        ringing = false;
        nextReminder = -1;
        // The click acknowledges this cycle before the server removes/replaces the visitor.
        acknowledged = true;
        return true;
    }

    boolean reminderDue(long now) {
        if (!ringing || (nextReminder != -1 && now < nextReminder)) return false;
        // Schedule from delivery time so a paused client does not flood chat on resume.
        nextReminder = now + 10_000;
        return true;
    }

    void reduce(long millis, long now) {
        if (deadline >= 0 && queue != null && queue.full()) {
            deadline = Math.max(0, deadline - Math.max(0, millis));
            tick(now);
        }
    }

    boolean ringing() { return ringing; }
    long remaining(long now) { return deadline < 0 ? -1 : Math.max(0, deadline - now); }
    long interval() { return interval; }
    VisitorQueue.Snapshot queue() { return queue; }

    void seed(long intervalMillis, long remainingMillis, long now) {
        if (intervalMillis > 0 && intervalMillis <= DEFAULT_INTERVAL) interval = intervalMillis;
        if (queue != null && queue.full() && remainingMillis >= 0 && remainingMillis <= DEFAULT_INTERVAL) {
            deadline = now + remainingMillis;
            tick(now);
        }
    }

    void reset() {
        interval = DEFAULT_INTERVAL;
        deadline = -1;
        nextReminder = -1;
        queue = null;
        ringing = false;
        acknowledged = false;
    }
}
