package com.github.beng420.kung.feature.misc;

import net.minecraft.world.item.BowItem;

/** Advances only on the shared, deduplicated server tick stream. */
final class BowDrawProgress {
    static final int MIN_SHOT_TICKS = 3;
    static final int FULL_DRAW_TICKS = 20;
    static final long RELEASE_HOLD_NANOS = 200_000_000L;
    private int ticks = -1;
    private boolean drawing;
    private long stoppedAtNanos;

    void start() { ticks = 0; drawing = true; }
    void reset() { ticks = -1; drawing = false; }
    boolean active() { return drawing; }
    int ticks() { return Math.max(0, ticks); }

    void stop(long nowNanos) {
        if (!drawing) return;
        drawing = false;
        stoppedAtNanos = nowNanos;
    }

    boolean visible(long nowNanos) {
        return ticks >= 0 && (drawing || nowNanos - stoppedAtNanos < RELEASE_HOLD_NANOS);
    }

    void serverTick() {
        if (active() && ticks < FULL_DRAW_TICKS) ticks++;
    }

    static float power(int ticks) {
        return BowItem.getPowerForTime(Math.clamp(ticks, 0, FULL_DRAW_TICKS));
    }
}
