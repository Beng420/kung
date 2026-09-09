package com.github.beng420.kung.config.category;

public final class SplitsConfig extends ConfigCategory {
    private boolean enabled = false;
    private int x = 226;
    private int y = 8;
    private int scale = 85;

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
}
