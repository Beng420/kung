package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small aggregate windows, so saved traces can distinguish scanning from HUD stalls. */
final class DungeonTimings {
    private static final Map<String, Window> WINDOWS = new HashMap<>();

    static void record(String stage, long started) {
        long now = System.nanoTime();
        Window window = WINDOWS.computeIfAbsent(stage, ignored -> new Window(now));
        long elapsed = now - started;
        window.total += elapsed;
        window.maximum = Math.max(window.maximum, elapsed);
        window.samples++;
        if (now - window.started < 5_000_000_000L) return;
        KungDebugRecorder.event("dungeon-performance", String.format(Locale.ROOT,
            "stage=%s samples=%d avgMs=%.3f maxMs=%.3f", stage, window.samples,
            window.total / (window.samples * 1_000_000.0), window.maximum / 1_000_000.0));
        WINDOWS.put(stage, new Window(now));
    }

    private static final class Window {
        final long started;
        long total;
        long maximum;
        int samples;
        Window(long started) { this.started = started; }
    }
}
