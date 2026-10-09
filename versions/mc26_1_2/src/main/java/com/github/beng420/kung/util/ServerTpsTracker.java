package com.github.beng420.kung.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

public final class ServerTpsTracker {
    public static final ServerTpsTracker INSTANCE = new ServerTpsTracker();
    private static final int MAX_TICK_TIMESTAMPS = 201;
    private static final long CURRENT_WINDOW_NANOS = 2_000_000_000L;
    private static final int SAMPLE_INTERVALS = 20;
    private static final double MAX_TPS = 20.0;
    private static final long TICK_NANOS = (long) (1_000_000_000L / MAX_TPS);

    private final Deque<Long> tickTimes = new ArrayDeque<>();
    private final ServerTickSequence sequence = new ServerTickSequence();
    private long observedTicks;
    private long ignoredBundledPings;
    private int lastTickId;
    private int lastBundledPingId;
    private long duplicatePings;
    private long nonTickPings;
    private long largestGapNanos;
    private volatile long lastArrivalNanos;

    private ServerTpsTracker() {
    }

    /** Network arrival time keeps local frame stalls out of the TPS sample. */
    public void observePacket(Packet<?> packet) {
        long now = System.nanoTime();
        if (!(packet instanceof ClientboundPingPacket || packet instanceof ClientboundBundlePacket
            || packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket)) {
            lastArrivalNanos = now;
            return;
        }
        recordPacketAtNanos(packet, now);
    }

    synchronized void recordPacketAtNanos(Packet<?> packet, long nowNanos) {
        lastArrivalNanos = nowNanos;
        recordPacketAtNanos(packet, nowNanos, false);
    }

    private void recordPacketAtNanos(Packet<?> packet, long nowNanos, boolean bundled) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> child : bundle.subPackets()) recordPacketAtNanos(child, nowNanos, true);
        } else if (packet instanceof ClientboundPingPacket ping) {
            if (!sequence.accept(ping.getId(), bundled)) {
                if (bundled) {
                    ignoredBundledPings++;
                    lastBundledPingId = ping.getId();
                } else if (ping.getId() == 0) nonTickPings++;
                else duplicatePings++;
                return;
            }
            observedTicks++;
            lastTickId = ping.getId();
            recordTickAtNanos(nowNanos);
        } else if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            reset();
        }
    }

    synchronized void recordTickAtNanos(long nowNanos) {
        if (!tickTimes.isEmpty() && nowNanos < tickTimes.getLast()) {
            return;
        }
        if (!tickTimes.isEmpty()) largestGapNanos = Math.max(largestGapNanos, nowNanos - tickTimes.getLast());
        tickTimes.addLast(nowNanos);
        while (tickTimes.size() > MAX_TICK_TIMESTAMPS) {
            tickTimes.removeFirst();
        }
    }

    public synchronized void reset() {
        tickTimes.clear();
        sequence.reset();
        observedTicks = 0L;
        ignoredBundledPings = 0L;
        lastTickId = 0;
        lastBundledPingId = 0;
        duplicatePings = 0L;
        nonTickPings = 0L;
        largestGapNanos = 0L;
    }

    /** Server ticks seen since the last world change; server timings belong on this clock, not on ours. */
    public synchronized long ticks() {
        return observedTicks;
    }

    public synchronized String diagnostics() {
        long now = System.nanoTime();
        long openGap = tickTimes.isEmpty() ? 0L : Math.max(0L, now - tickTimes.getLast());
        return "receivedTicks=" + observedTicks + " ignoredBundledPings=" + ignoredBundledPings
            + " duplicatePings=" + duplicatePings + " nonTickPings=" + nonTickPings
            + " tickSource=standalone-ping lastTickId=" + lastTickId + " lastBundledPingId=" + lastBundledPingId
            + " maxArrivalGapMs=" + Math.max(largestGapNanos, openGap) / 1_000_000L
            + " tps=" + snapshotAtNanos(now).message();
    }

    public synchronized TpsSnapshot snapshot() {
        return deliveredSnapshotAtNanos(System.nanoTime());
    }

    /**
     * A silent server and a stalled connection look alike until the next packet. TCP keeps order, so
     * only silence before the last arrival is proven server lag; a stall after it shows up as ping.
     */
    synchronized TpsSnapshot deliveredSnapshotAtNanos(long nowNanos) {
        return snapshotAtNanos(Math.min(nowNanos, lastArrivalNanos));
    }

    synchronized TpsSnapshot snapshotAtNanos(long nowNanos) {
        if (tickTimes.size() < 2) {
            return TpsSnapshot.unavailable();
        }

        long[] times = new long[tickTimes.size()];
        int index = 0;
        for (long tickTime : tickTimes) {
            times[index++] = tickTime;
        }

        int intervals = times.length - 1;
        double current = currentTps(times, Math.max(nowNanos, times[intervals]));
        double average = tpsForSpan(times, 0, intervals);
        // The server does not tick faster than MAX_TPS, so ticks arriving closer together were held
        // up in the silence before them. Back-date them there: min then shows only gaps no burst filled.
        for (int i = intervals - 1; i >= 0; i--) times[i] = Math.min(times[i], times[i + 1] - TICK_NANOS);
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

    private static double currentTps(long[] times, long nowNanos) {
        // A window ending at the last packet stays at 20 TPS forever during a
        // full stall. End at the observation time and include the silent tail.
        long start = Math.max(times[0], nowNanos - CURRENT_WINDOW_NANOS);
        if (start == nowNanos) return MAX_TPS;
        int ticks;
        if (nowNanos - times[0] < CURRENT_WINDOW_NANOS) {
            ticks = times.length - 1; // Only the first observation is the startup baseline, even in a batch.
        } else {
            ticks = 0;
            for (long time : times) {
                if (time > start) ticks++;
            }
        }
        return Math.clamp(ticks * 1_000_000_000.0 / (nowNanos - start), 0.0, MAX_TPS);
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
