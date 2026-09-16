package com.github.beng420.kung.ui;

/** Tracks continuous hover over the same UI entry, independent of pointer movement within it. */
public final class UiHoverDelay {
    private static final long DELAY_MILLIS = 2_000;
    private Object target;
    private long since;

    public boolean ready(Object hovered, long now) {
        if (hovered == null) {
            reset();
            return false;
        }
        if (hovered != target) {
            target = hovered;
            since = now;
        }
        return now - since >= DELAY_MILLIS;
    }

    public void reset() { target = null; }
}
