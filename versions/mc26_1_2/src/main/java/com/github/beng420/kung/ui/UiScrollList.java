package com.github.beng420.kung.ui;

public final class UiScrollList {
    private int offset;

    public int offset() {
        return offset;
    }

    public void setOffset(int offset, int contentSize, int viewportSize) {
        this.offset = Math.clamp(offset, 0, maxOffset(contentSize, viewportSize));
    }

    public void scrollBy(int delta, int contentSize, int viewportSize) {
        setOffset(offset + delta, contentSize, viewportSize);
    }

    public void setOffsetWithinMax(int offset, int maxOffset) {
        this.offset = Math.clamp(offset, 0, Math.max(0, maxOffset));
    }

    public void scrollByWithinMax(int delta, int maxOffset) {
        setOffsetWithinMax(offset + delta, maxOffset);
    }

    public void reset() {
        offset = 0;
    }

    public static int maxOffset(int contentSize, int viewportSize) {
        return Math.max(0, contentSize - Math.max(0, viewportSize));
    }
}
