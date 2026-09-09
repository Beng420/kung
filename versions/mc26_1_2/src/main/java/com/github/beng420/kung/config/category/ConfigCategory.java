package com.github.beng420.kung.config.category;

public abstract class ConfigCategory {
    private transient Runnable onChange;

    public final void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    protected final void save() {
        if (onChange != null) onChange.run();
    }
}
