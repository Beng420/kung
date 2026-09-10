package com.github.beng420.kung.config.category;

public final class SplitsConfig extends ConfigCategory {
    private boolean enabled = false;
    private int x = 226;
    private int y = 8;
    private int scale = 85;
    private TimeFormat format = TimeFormat.MINUTES;
    private boolean timeLost = true;

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

    public enum TimeFormat {
        MINUTES("Minutes"), SECONDS("Seconds");

        private final String label;

        TimeFormat(String label) { this.label = label; }
        public String label() { return label; }
        public TimeFormat next() { return this == MINUTES ? SECONDS : MINUTES; }
    }
}
