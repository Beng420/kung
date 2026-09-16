package com.github.beng420.kung.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UiControlModelTest {
    @Test
    public void tooltipRequiresTwoSecondsOnOneEntryAndRestartsAfterLeavingOrInteracting() {
        var hover = new UiHoverDelay();
        var feature = new Object();
        var setting = new Object();
        assertFalse(hover.ready(feature, 1_000));
        assertFalse(hover.ready(feature, 2_999));
        assertTrue(hover.ready(feature, 3_000));
        assertTrue(hover.ready(feature, 4_000));
        assertFalse(hover.ready(setting, 4_000));
        assertFalse(hover.ready(setting, 5_999));
        assertTrue(hover.ready(setting, 6_000));
        assertFalse(hover.ready(null, 6_001));
        assertFalse(hover.ready(setting, 7_000));
        assertTrue(hover.ready(setting, 9_000));
        hover.reset();
        assertFalse(hover.ready(setting, 9_001));
        assertTrue(hover.ready(setting, 11_001));
    }

    @Test
    public void numberFieldClampsStepsAndSliderPositions() {
        UiNumberField number = new UiNumberField(10, 50, 5);

        assertEquals(10, number.decrement(10));
        assertEquals(50, number.increment(50));
        assertEquals(25, number.valueAt(0.4));
        assertEquals(10, number.valueAt(-1.0));
        assertEquals(50, number.valueAt(2.0));
        assertEquals(0.5, number.progress(30), 0.0);
    }

    @Test
    public void scrollListTracksAClampedOffset() {
        UiScrollList scroll = new UiScrollList();

        scroll.scrollBy(60, 200, 100);
        assertEquals(60, scroll.offset());
        scroll.scrollBy(60, 200, 100);
        assertEquals(100, scroll.offset());
        scroll.setOffset(-5, 200, 100);
        assertEquals(0, scroll.offset());
        scroll.setOffsetWithinMax(80, 25);
        assertEquals(25, scroll.offset());
        scroll.reset();
        assertEquals(0, scroll.offset());
    }
}
