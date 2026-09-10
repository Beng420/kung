package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.beng420.kung.config.category.DungeonConfig;
import org.junit.Test;

public final class DungeonMapLayoutTest {
    @Test
    public void eachEditorScaleStepChangesMapBoundsWithoutDependingOnTheWindow() {
        DungeonConfig config = new DungeonConfig();
        config.setScale(25);
        DungeonMapFeature.OverlayBounds previous = DungeonMapFeature.overlayBounds(config);
        for (int scale = 30; scale <= 300; scale += 5) {
            config.setScale(scale);
            DungeonMapFeature.OverlayBounds current = DungeonMapFeature.overlayBounds(config);
            assertTrue("map width at " + scale + "%", current.width() > previous.width());
            assertTrue("map height at " + scale + "%", current.height() > previous.height());
            previous = current;
        }
    }

    @Test
    public void textEnlargementReservesFooterAndLegendSpaceWithoutChangingMapScale() {
        DungeonConfig config = new DungeonConfig();
        config.setShowLegend(true);
        DungeonMapFeature.OverlayBounds normal = DungeonMapFeature.overlayBounds(config);
        config.setTextScale(200);
        DungeonMapFeature.OverlayBounds readable = DungeonMapFeature.overlayBounds(config);
        assertEquals(100, config.scale());
        assertTrue(readable.width() > normal.width());
        assertTrue(readable.height() > normal.height());
        config.setShowLegend(false);
        assertTrue(DungeonMapFeature.overlayBounds(config).height() < readable.height());
    }
}
