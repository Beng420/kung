package com.github.beng420.kung.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UiBoundsTest {
    @Test
    public void containsUsesExclusiveRightAndBottomEdges() {
        UiBounds bounds = new UiBounds(10, 20, 5, 4);

        assertTrue(bounds.contains(10, 20));
        assertTrue(bounds.contains(14, 23));
        assertFalse(bounds.contains(15, 23));
        assertFalse(bounds.contains(14, 24));
        assertFalse(bounds.contains(9, 20));
    }

    @Test
    public void rejectsNegativeSizes() {
        assertThrows(IllegalArgumentException.class, () -> new UiBounds(0, 0, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new UiBounds(0, 0, 1, -2));
    }
}
