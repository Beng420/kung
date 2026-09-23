package com.github.beng420.kung.feature.slayer;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class TarantulaEggSacGridTest {
    @Test
    public void recordedPhasesCountPerGridCell() {
        var counts = TarantulaHelperFeature.eggSacGridCounts(List.of(
            "anchor=10.00,70.00,-5.00 sacs=0.00,0.10,0.00;2.25,0.00,-1.50;-3.30,0.00,3.30",
            "anchor=11.00,70.00,-6.00 sacs=0.05,0.00,-0.02;2.30,0.00,-1.45",
            "broken line without offsets",
            "anchor=1.00,70.00,1.00 sacs=nan,0.00,0.00"));
        // 0.75 per cell: 2.25 -> 3, -1.50 -> -2, and the near-zero offsets share the center cell.
        assertEquals(Map.of("0,0", 2, "3,-2", 2, "-4,4", 1), counts);
    }
}
