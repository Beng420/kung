package com.github.beng420.kung.feature.garden;

import com.github.beng420.kung.config.category.FeastConfig;
import java.util.Locale;

/** Client-thread exponentially weighted rate. Monotonic clocks advance weights only during farming. */
final class FeastKernelRate {
    static final long HALF_LIFE_MILLIS = 5 * 60_000L;
    static final long WARMUP_MILLIS = 60_000;
    private static final double DECAY_PER_MILLI = Math.log(2.0) / HALF_LIFE_MILLIS;
    private String profile = "";
    private String profileId = "";
    private String eventKey = "";
    private long farmingMillis;
    private long lastUpdate = Long.MIN_VALUE;
    private long farmingUntil = Long.MIN_VALUE;
    private long lastCrop = Long.MIN_VALUE;
    private long timeoutMillis = FeastConfig.DEFAULT_KERNEL_TIMEOUT_SECONDS * 1_000L;
    private long kernels;
    private double weightedKernels;
    private double weightedFarmingMillis;
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
        weightedKernels++;
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
        return new Snapshot(kernels, farmingMillis, weightedKernels, weightedFarmingMillis,
            !eligible || now >= farmingUntil);
    }

    private void advance(long now) {
        if (lastUpdate != Long.MIN_VALUE && now > lastUpdate && eligible && farmingUntil > lastUpdate) {
            long elapsed = Math.min(now, farmingUntil) - lastUpdate;
            double exponent = -DECAY_PER_MILLI * elapsed;
            double decay = Math.exp(exponent);
            weightedKernels *= decay;
            // Integrate exposure with the same decay as gains, independent of tick/render cadence.
            // expm1 preserves precision for short intervals and normalizes startup from observed time.
            weightedFarmingMillis = weightedFarmingMillis * decay - Math.expm1(exponent) / DECAY_PER_MILLI;
            farmingMillis += elapsed;
        }
        lastUpdate = Math.max(lastUpdate, now);
    }

    private void clearMeasurement() {
        farmingMillis = 0;
        lastUpdate = Long.MIN_VALUE;
        farmingUntil = Long.MIN_VALUE;
        lastCrop = Long.MIN_VALUE;
        kernels = 0;
        weightedKernels = 0;
        weightedFarmingMillis = 0;
        eligible = false;
    }

    void reset() {
        clearMeasurement();
        profile = "";
        profileId = "";
        eventKey = "";
    }

    record Snapshot(long kernels, long farmingMillis, double weightedKernels, double weightedFarmingMillis,
                    boolean paused) {
        Long perHour() {
            return farmingMillis < WARMUP_MILLIS ? null : Math.round(weightedKernels * 3_600_000.0 / weightedFarmingMillis);
        }
    }
}
