package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.config.category.FeastConfig;
import java.util.Locale;

/** Client-thread rate: blend completed farming blocks into a saved estimate; idle time never counts. */
final class FeastKernelRate {
    static final long BLOCK_MILLIS = 5 * 60_000L;
    private String profile = "";
    private String profileId = "";
    private String eventKey = "";
    private long farmingMillis;
    private long lastUpdate = Long.MIN_VALUE;
    private long farmingUntil = Long.MIN_VALUE;
    private long lastCrop = Long.MIN_VALUE;
    private long timeoutMillis = FeastConfig.DEFAULT_KERNEL_TIMEOUT_SECONDS * 1_000L;
    private long kernels;
    private Double average;
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
        if (!profileId.isEmpty()) {
            clearMeasurement();
            average = null;
        }
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
        if (!eligible) {
            farmingUntil = Long.MIN_VALUE;
            lastCrop = Long.MIN_VALUE;
        }
    }

    void setTimeoutSeconds(int seconds, long now) {
        long next = Math.clamp(seconds, FeastConfig.MIN_KERNEL_TIMEOUT_SECONDS, FeastConfig.MAX_KERNEL_TIMEOUT_SECONDS) * 1_000L;
        if (next == timeoutMillis) return;
        // Settle time under the old setting; editing must not rewrite history or fill a past pause.
        advance(now);
        timeoutMillis = next;
        if (eligible && lastCrop != Long.MIN_VALUE) farmingUntil = lastCrop + timeoutMillis;
    }

    void crop(long now) {
        advance(now);
        if (eligible) {
            lastCrop = now;
            farmingUntil = now + timeoutMillis;
        }
    }

    boolean observeMessage(String message, long now) {
        advance(now);
        if (!eligible || now > farmingUntil || !FeastKernels.donationMessage(message)) return false;
        kernels++;
        return true;
    }

    void pause(long now) {
        advance(now);
        eligible = false;
        farmingUntil = Long.MIN_VALUE;
        lastCrop = Long.MIN_VALUE;
    }

    Snapshot snapshot(long now) {
        advance(now);
        return new Snapshot(kernels, farmingMillis, average, !eligible || now >= farmingUntil);
    }

    Double value() { return average; }

    void restore(Double saved) { average = saved; }

    private void advance(long now) {
        if (lastUpdate != Long.MIN_VALUE && now > lastUpdate && eligible && farmingUntil > lastUpdate) {
            long elapsed = Math.min(now, farmingUntil) - lastUpdate;
            while (elapsed > 0) {
                long step = Math.min(elapsed, BLOCK_MILLIS - farmingMillis);
                farmingMillis += step;
                elapsed -= step;
                if (farmingMillis == BLOCK_MILLIS) {
                    double measured = kernels * 3_600_000.0 / BLOCK_MILLIS;
                    // Whole blocks avoid per-drop spikes. Retain the full precision between updates/restarts.
                    average = average == null ? measured : average + (measured - average) * 0.2;
                    farmingMillis = 0;
                    kernels = 0;
                }
            }
        }
        lastUpdate = Math.max(lastUpdate, now);
    }

    private void clearMeasurement() {
        farmingMillis = 0;
        lastUpdate = Long.MIN_VALUE;
        farmingUntil = Long.MIN_VALUE;
        lastCrop = Long.MIN_VALUE;
        kernels = 0;
        eligible = false;
    }

    void reset() {
        clearMeasurement();
        average = null;
        profile = "";
        profileId = "";
        eventKey = "";
    }

    record Snapshot(long kernels, long farmingMillis, Double average, boolean paused) {
        Long perHour() {
            return average == null ? null : Math.round(average);
        }
    }
}
