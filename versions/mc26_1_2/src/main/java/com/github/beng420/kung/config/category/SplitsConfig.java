package com.github.beng420.kung.config.category;

import java.util.Map;
import java.util.TreeMap;

public final class SplitsConfig extends ConfigCategory {
    private boolean enabled = false;
    private int x = 226;
    private int y = 8;
    private int scale = 85;
    private TimeFormat format = TimeFormat.MINUTES;
    private boolean timeLost = true;
    private boolean timePrediction = true;
    private PredictionMode predictionMode = PredictionMode.PHASE_END;
    /** Real phase durations in milliseconds, keyed by Entrance/F1–F7/M1–M7 and phase name. */
    private Map<String, Map<String, Long>> personalBests = new TreeMap<>();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public int x() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
        save();
    }

    public int y() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
        save();
    }

    public int scale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = Math.clamp(scale, 25, 300);
        save();
    }

    public TimeFormat format() {
        return format == null ? TimeFormat.MINUTES : format;
    }

    public void setFormat(TimeFormat format) {
        this.format = format == null ? TimeFormat.MINUTES : format;
        save();
    }

    public String formatLabel() { return format().label(); }
    public void cycleFormat() { setFormat(format().next()); }

    public boolean timeLost() { return timeLost; }

    public void setTimeLost(boolean timeLost) {
        this.timeLost = timeLost;
        save();
    }

    public boolean timePrediction() { return timePrediction; }

    public void setTimePrediction(boolean timePrediction) {
        this.timePrediction = timePrediction;
        save();
    }

    public PredictionMode predictionMode() {
        return predictionMode == null ? PredictionMode.PHASE_END : predictionMode;
    }

    public void setPredictionMode(PredictionMode predictionMode) {
        this.predictionMode = predictionMode == null ? PredictionMode.PHASE_END : predictionMode;
        save();
    }

    public String predictionModeLabel() { return predictionMode().label(); }
    public void cyclePredictionMode() { setPredictionMode(predictionMode().next()); }

    public long personalBestMillis(int floor, boolean masterMode, String phase) {
        String key = floorKey(floor, masterMode);
        if (key == null || phase == null || personalBests == null) return -1L;
        Map<String, Long> phases = personalBests.get(key);
        Long best = phases == null ? null : phases.get(phase);
        return best != null && best > 0L ? best : -1L;
    }

    /** Coalesce a batch of measured phases into one save, only when a best improves. */
    public void recordPersonalBests(int floor, boolean masterMode, Map<String, Long> measurements) {
        String key = floorKey(floor, masterMode);
        if (key == null || measurements == null) return;
        boolean changed = false;
        for (var entry : measurements.entrySet()) {
            String phase = entry.getKey();
            Long duration = entry.getValue();
            if (phase == null || phase.isBlank() || duration == null || duration <= 0L) continue;
            long previous = personalBestMillis(floor, masterMode, phase);
            if (previous > 0L && duration >= previous) continue;
            if (personalBests == null) personalBests = new TreeMap<>();
            personalBests.computeIfAbsent(key, ignored -> new TreeMap<>()).put(phase, duration);
            changed = true;
        }
        if (changed) save();
    }

    private static String floorKey(int floor, boolean masterMode) {
        if (floor < 0 || floor > 7) return null;
        return floor == 0 ? "Entrance" : (masterMode ? "M" : "F") + floor;
    }

    public enum PredictionMode {
        PHASE_END("Phase End"), LIVE("Live");

        private final String label;

        PredictionMode(String label) { this.label = label; }
        public String label() { return label; }
        public PredictionMode next() { return this == PHASE_END ? LIVE : PHASE_END; }
    }

    public enum TimeFormat {
        MINUTES("Minutes"), SECONDS("Seconds");

        private final String label;

        TimeFormat(String label) { this.label = label; }
        public String label() { return label; }
        public TimeFormat next() { return this == MINUTES ? SECONDS : MINUTES; }
    }
}
