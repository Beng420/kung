package com.github.beng420.kung.config.category;

public final class VisitorAlarmConfig extends ConfigCategory {
    private boolean enabled;
    private int volume = 70;

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; save(); }
    public int volume() { return volume; }
    public void setVolume(int value) { volume = Math.clamp(value, 1, 100); save(); }
}
