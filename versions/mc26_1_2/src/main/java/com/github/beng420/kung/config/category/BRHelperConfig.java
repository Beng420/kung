package com.github.beng420.kung.config.category;

public final class BRHelperConfig extends ConfigCategory {
    private boolean enabled = false;
    private int titleDurationTenths = 5;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public int titleDurationTenths() {
        return titleDurationTenths;
    }

    public void setTitleDurationTenths(int duration) {
        this.titleDurationTenths = Math.clamp(duration, 1, 50);
        save();
    }
}
