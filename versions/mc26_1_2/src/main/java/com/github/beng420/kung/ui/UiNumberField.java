package com.github.beng420.kung.ui;

public record UiNumberField(int min, int max, int step) {
    public UiNumberField {
        if (max < min || step <= 0) {
            throw new IllegalArgumentException("Invalid number field range.");
        }
    }

    public int clamp(int value) {
        return Math.clamp(value, min, max);
    }

    public int increment(int value) {
        return clamp(value + step);
    }

    public int decrement(int value) {
        return clamp(value - step);
    }

    public double progress(int value) {
        return max == min ? 0.0 : (double) (clamp(value) - min) / (double) (max - min);
    }

    public int valueAt(double progress) {
        int raw = min + (int) Math.round(Math.clamp(progress, 0.0, 1.0) * (max - min));
        int stepped = min + Math.round((float) (raw - min) / (float) step) * step;
        return clamp(stepped);
    }
}
