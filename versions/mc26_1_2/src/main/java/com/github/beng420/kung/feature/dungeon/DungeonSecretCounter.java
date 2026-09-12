package com.github.beng420.kung.feature.dungeon;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One player's run count, with explicit provenance. Room totals and party totals
 * are not personal observations and must never be supplied to this counter.
 */
final class DungeonSecretCounter {
    enum Source {
        UNKNOWN, OBSERVED_EVENTS, API_DELTA, SELF_REPORT, PERSONAL_TAB
    }

    private static final AtomicLong OBSERVATIONS = new AtomicLong();
    private int value;
    private Source source = Source.UNKNOWN;
    private long observedAt = Long.MIN_VALUE;

    int value() { return value; }
    boolean known() { return source != Source.UNKNOWN; }
    Source source() { return source; }

    void personalTab(int count) {
        observe(count, Source.PERSONAL_TAB, OBSERVATIONS.incrementAndGet());
    }

    void selfReport(int count, long reportedAtMillis) {
        if (reportedAtMillis <= 0L) return;
        observe(count, Source.SELF_REPORT, reportedAtMillis);
    }

    void apiDelta(int count) {
        observe(count, Source.API_DELTA, OBSERVATIONS.incrementAndGet());
    }

    void incrementObserved(int count) {
        if (count <= 0 || source.ordinal() > Source.OBSERVED_EVENTS.ordinal()) return;
        observe(Math.addExact(value, count), Source.OBSERVED_EVENTS, OBSERVATIONS.incrementAndGet());
    }

    void reset() {
        value = 0;
        source = Source.UNKNOWN;
        observedAt = Long.MIN_VALUE;
    }

    void merge(DungeonSecretCounter other) {
        if (other != null && other.known()) observe(other.value, other.source, other.observedAt);
    }

    private void observe(int count, Source nextSource, long nextObservedAt) {
        if (count < 0 || nextSource.ordinal() < source.ordinal()) return;
        if (nextSource == source && nextObservedAt <= observedAt) return;
        value = count;
        source = nextSource;
        observedAt = nextObservedAt;
    }
}
