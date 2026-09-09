package com.github.beng420.kung.ui;

public record UiBounds(int x, int y, int width, int height) {
    public UiBounds {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("UI bounds cannot have a negative size.");
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }
}
