package com.github.beng420.kung.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class ServerTpsTracker {
    public static final ServerTpsTracker INSTANCE = new ServerTpsTracker();
    private static final int MAX_TICK_TIMESTAMPS = 201;
    private static final int CURRENT_INTERVALS = 40;
    private static final int SAMPLE_INTERVALS = 20;
    private static final double MAX_TPS = 20.0;

    private final Deque<Long> tickTimes = new ArrayDeque<>();

    private ServerTpsTracker() {
    }

    public synchronized void observeServerTick() {
        recordTickAtNanos(System.nanoTime());
    }

    synchronized void recordTickAtNanos(long nowNanos) {
        if (!tickTimes.isEmpty() && nowNanos <= tickTimes.getLast()) {
            return;
        }
        tickTimes.addLast(nowNanos);
        while (tickTimes.size() > MAX_TICK_TIMESTAMPS) {
            tickTimes.removeFirst();
        }
    }

    public synchronized void reset() {
        tickTimes.clear();
    }

    public synchronized TpsSnapshot snapshot() {
        if (tickTimes.size() < 2) {
            return TpsSnapshot.unavailable();
        }

        long[] times = new long[tickTimes.size()];
        int index = 0;
        for (long tickTime : tickTimes) {
            times[index++] = tickTime;
        }

        int intervals = times.length - 1;
        double current = tpsForSpan(times, Math.max(0, intervals - CURRENT_INTERVALS), intervals);
        double average = tpsForSpan(times, 0, intervals);
        double min = current;
        double max = current;
        int sampleSize = Math.min(SAMPLE_INTERVALS, intervals);
        for (int end = sampleSize; end <= intervals; end++) {
            double sample = tpsForSpan(times, end - sampleSize, end);
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        return new TpsSnapshot(true, current, max, min, average);
    }

    private static double tpsForSpan(long[] times, int startInterval, int endInterval) {
        int intervals = Math.max(1, endInterval - startInterval);
        long elapsedNanos = Math.max(1L, times[endInterval] - times[startInterval]);
        double tps = intervals * 1_000_000_000.0 / elapsedNanos;
        return Math.clamp(tps, 0.0, MAX_TPS);
    }

    public record TpsSnapshot(boolean available, double current, double max, double min, double average) {
        private static TpsSnapshot unavailable() {
            return new TpsSnapshot(false, 0.0, 0.0, 0.0, 0.0);
        }

        public String message() {
            if (!available) {
                return "TPS data is not ready yet.";
            }
            return String.format(
                Locale.ROOT,
                "Current: %.1f (max/min/avg) %.1f/%.1f/%.1f",
                current,
                max,
                min,
                average
            );
        }
    }
}
