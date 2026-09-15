package com.github.beng420.kung.feature.garden;

import java.util.ArrayDeque;
import java.util.Locale;

/** Client-thread rolling rate. Clocks use monotonic milliseconds; samples age only during farming. */
final class FeastKernelRate {
    static final long WINDOW_MILLIS = 20 * 60_000L;
    static final long WARMUP_MILLIS = 60_000;
    static final long IDLE_MILLIS = 5_000;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private String profile = "";
    private String profileId = "";
    private String eventKey = "";
    private long farmingMillis;
    private long lastUpdate = Long.MIN_VALUE;
    private long farmingUntil = Long.MIN_VALUE;
    private long kernels;
    private boolean eligible;

    void selectProfile(String account, String name) {
        if (name == null || name.isBlank()) return;
        String next = account + ":" + name.toLowerCase(Locale.ROOT);
        if (next.equals(profile)) return;
        reset();
        profile = next;
    }

    void identifyProfile(String id) {
        if (profile.isEmpty() || id == null || id.equals(profileId)) return;
        if (!profileId.isEmpty()) clearMeasurement();
        profileId = id;
    }

    void updateContext(boolean garden, FeastContext.Event event, long now) {
        advance(now);
        if (event != null && !event.key().equals(eventKey)) {
            clearMeasurement();
            eventKey = event.key();
            lastUpdate = now;
        }
        eligible = garden && !profile.isEmpty() && event != null && event.kind() == FeastProgress.Kind.GRAND;
        if (!eligible) farmingUntil = Long.MIN_VALUE;
    }

    void crop(long now) {
        advance(now);
        if (eligible) farmingUntil = now + IDLE_MILLIS;
    }

    boolean observeMessage(String message, long now) {
        advance(now);
        if (!eligible || now > farmingUntil || !FeastKernels.donationMessage(message)) return false;
        long second = farmingMillis / 1_000;
        Sample last = samples.peekLast();
        if (last == null || last.second != second) samples.addLast(new Sample(second));
        samples.getLast().kernels++;
        kernels++;
        return true;
    }

    void pause(long now) {
        advance(now);
        eligible = false;
        farmingUntil = Long.MIN_VALUE;
    }

    Snapshot snapshot(long now) {
        advance(now);
        return new Snapshot(kernels, Math.min(farmingMillis, WINDOW_MILLIS), !eligible || now >= farmingUntil);
    }

    private void advance(long now) {
        if (lastUpdate != Long.MIN_VALUE && now > lastUpdate && eligible && farmingUntil > lastUpdate) {
            farmingMillis += Math.max(0, Math.min(now, farmingUntil) - lastUpdate);
        }
        lastUpdate = Math.max(lastUpdate, now);
        // One-second buckets bound storage to 1,201 entries, even with many gains in one second.
        if (farmingMillis >= WINDOW_MILLIS) {
            long cutoff = (farmingMillis - WINDOW_MILLIS) / 1_000;
            while (!samples.isEmpty() && samples.getFirst().second <= cutoff) kernels -= samples.removeFirst().kernels;
        }
    }

    private void clearMeasurement() {
        samples.clear();
        farmingMillis = 0;
        lastUpdate = Long.MIN_VALUE;
        farmingUntil = Long.MIN_VALUE;
        kernels = 0;
        eligible = false;
    }

    void reset() {
        clearMeasurement();
        profile = "";
        profileId = "";
        eventKey = "";
    }

    int sampleCount() { return samples.size(); }

    record Snapshot(long kernels, long farmingMillis, boolean paused) {
        Long perHour() {
            return farmingMillis < WARMUP_MILLIS ? null : Math.round(kernels * 3_600_000.0 / farmingMillis);
        }
    }

    private static final class Sample {
        final long second;
        long kernels;
        Sample(long second) { this.second = second; }
    }
}
