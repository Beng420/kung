package com.github.beng420.kung.update;

import com.github.beng420.kung.ui.UiBounds;

/** Monotonic visible time and shared drawing/hit-test geometry, independent of Minecraft. */
final class KungUpdateToastState {
    static final int WIDTH = 238;
    static final int HEIGHT = 106;
    static final long SLIDE_MILLIS = 250L;
    static final long DISPLAY_MILLIS = 8_000L;
    static final UiBounds CLOSE = new UiBounds(210, 7, 20, 20);
    static final UiBounds UPDATES = new UiBounds(12, 62, 128, 20);
    static final UiBounds GITHUB = new UiBounds(146, 62, 80, 20);
    private static final long EXIT_START = SLIDE_MILLIS + DISPLAY_MILLIS;
    private long elapsedMillis;
    private long lastMillis;
    private boolean active;

    void show(long nowMillis) {
        elapsedMillis = 0L;
        lastMillis = nowMillis;
        active = true;
    }

    void clear() { active = false; }
    boolean active() { return active; }

    void advance(long nowMillis, boolean visible, boolean hovered) {
        long delta = Math.max(0L, nowMillis - lastMillis);
        lastMillis = nowMillis;
        if (!active || !visible) return;
        // Hover pauses only the reading time, never leaving a partially slid card stuck.
        if (hovered && elapsedMillis >= SLIDE_MILLIS && elapsedMillis < EXIT_START) return;
        elapsedMillis += delta;
        if (elapsedMillis >= EXIT_START + SLIDE_MILLIS) clear();
    }

    float remaining() {
        return Math.clamp(1F - (float) (elapsedMillis - SLIDE_MILLIS) / DISPLAY_MILLIS, 0F, 1F);
    }

    Layout layout(float screenWidth, float screenHeight) {
        float scale = Math.max(0.1F, Math.min(1F,
            Math.min((screenWidth - 20F) / WIDTH, (screenHeight - 42F) / HEIGHT)));
        float fraction = elapsedMillis < SLIDE_MILLIS ? (float) elapsedMillis / SLIDE_MILLIS
            : 1F - (float) Math.max(0L, elapsedMillis - EXIT_START) / SLIDE_MILLIS;
        fraction = Math.clamp(fraction, 0F, 1F);
        float eased = fraction * fraction * (3F - 2F * fraction);
        return new Layout(screenWidth - (WIDTH * scale + 10F) * eased,
            Math.max(0F, screenHeight - HEIGHT * scale - 32F), scale);
    }

    Action hitTest(Layout layout, double mouseX, double mouseY, int button) {
        if (!active || !layout.contains(mouseX, mouseY)) return Action.NONE;
        if (button != 0) return Action.BLOCK;
        if (layout.contains(CLOSE, mouseX, mouseY)) return Action.DISMISS;
        if (layout.contains(UPDATES, mouseX, mouseY)) return Action.UPDATES;
        if (layout.contains(GITHUB, mouseX, mouseY)) return Action.GITHUB;
        return Action.BLOCK;
    }

    enum Action { NONE, BLOCK, DISMISS, UPDATES, GITHUB }

    record Layout(float x, float y, float scale) {
        boolean contains(double mouseX, double mouseY) {
            return contains(new UiBounds(0, 0, WIDTH, HEIGHT), mouseX, mouseY);
        }

        boolean contains(UiBounds bounds, double mouseX, double mouseY) {
            double localX = (mouseX - x) / scale;
            double localY = (mouseY - y) / scale;
            return localX >= bounds.x() && localX < bounds.right()
                && localY >= bounds.y() && localY < bounds.bottom();
        }
    }
}
