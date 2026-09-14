package com.github.beng420.kung.config.category;

public final class FeastConfig extends ConfigCategory {
    private boolean enabled;
    private boolean showInHubFarm = true;
    private int x = 12;
    private int y = 190;
    private int scale = 100;

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; save(); }
    public boolean showInHubFarm() { return showInHubFarm; }
    public void setShowInHubFarm(boolean value) { showInHubFarm = value; save(); }
    public int x() { return x; }
    public void setX(int value) { x = value; save(); }
    public int y() { return y; }
    public void setY(int value) { y = value; save(); }
    public int scale() { return scale; }
    public void setScale(int value) { scale = Math.clamp(value, 25, 300); save(); }
}
