package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.skyblock.HypixelDungeonFloor;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Test;

public final class DungeonBossMapCatalogTest {
    @Test public void everyStellaArenaHasItsOriginalImageAndDeclaredDimensions() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var images = new HashSet<String>();
        for (int floor = 1; floor <= 7; floor++) {
            assertEquals("arena count for floor " + floor, floor == 7 ? 6 : 1, catalog.arenas(floor).size());
            for (var arena : catalog.arenas(floor)) {
                assertTrue("duplicate asset " + arena.image(), images.add(arena.image()));
                String resource = DungeonBossMapCatalog.RESOURCE_ROOT + arena.image() + ".png";
                try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                    assertNotNull("missing image " + resource, stream);
                    var image = ImageIO.read(stream);
                    assertNotNull("invalid PNG " + resource, image);
                    assertEquals(arena.image() + " width", arena.width(), image.getWidth());
                    assertEquals(arena.image() + " height", arena.height(), image.getHeight());
                }
            }
        }
        assertEquals(12, images.size());
        assertTrue(catalog.arenas(0).isEmpty());
        assertTrue(catalog.arenas(-1).isEmpty());
        assertTrue(catalog.arenas(8).isEmpty());
    }

    @Test public void allSevenFloorsResolveTheirOwnArenaAtRealCoordinates() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        double[][] positions = {{-42, 80, 21}, {-8, 70, -10}, {1, 80, 1}, {5, 70, 5},
            {5, 70, 42}, {-9, 70, 45}, {73, 230, 48}};
        for (int floor = 1; floor <= 7; floor++) {
            double[] p = positions[floor - 1];
            var normalFloor = HypixelDungeonFloor.fromLine("The Catacombs (F" + floor + ")");
            var masterFloor = HypixelDungeonFloor.fromLine("The Catacombs (M" + floor + ")");
            var normal = catalog.find(normalFloor.floor(), p[0], p[1], p[2]);
            assertNotNull("floor " + floor, normal);
            assertEquals(floor == 7 ? "f7_boss_s1" : "f" + floor + "_boss", normal.image());
            // Both normal and master metadata use the numeric floor: there is no separate M texture set.
            assertTrue(masterFloor.masterMode());
            assertSame(normal, catalog.find(masterFloor.floor(), p[0], p[1], p[2]));
        }
        assertNull(catalog.find(0, -42, 80, 21));
        assertNull(catalog.find(1, -185, 70, -185));
    }

    @Test public void seventhFloorSwitchesEveryLayerAndKeepsRewardRoomFirst() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        assertEquals(List.of("f7_boss_end", "f7_boss_s1", "f7_boss_s2", "f7_boss_s3", "f7_boss_s4", "f7_boss_s5"),
            catalog.arenas(7).stream().map(DungeonBossMapCatalog.Arena::image).toList());
        double[][] positions = {{28, 175, 130}, {73, 230, 48}, {73, 186, 53},
            {54, 120, 86}, {54, 75, 76}, {63, 25, 68}};
        for (int i = 0; i < positions.length; i++) {
            double[] p = positions[i];
            assertSame(catalog.arenas(7).get(i), catalog.find(7, p[0], p[1], p[2]));
        }
        assertNull(catalog.find(7, 73, 212.5, 48));
        assertNull(catalog.find(7, 73, 159.5, 48));
    }

    @Test public void metadataOrderWinsIfTwoArenaBoundsOverlap() {
        var catalog = DungeonBossMapCatalog.read(new StringReader("""
            {"7": [
              {"image":"f7_boss_end", "bounds":[[0,0,0],[10,10,10]], "width":480,"height":480,
               "widthInWorld":10,"heightInWorld":10,"topLeftLocation":[0,0]},
              {"image":"f7_boss_s2", "bounds":[[0,0,0],[10,10,10]], "width":480,"height":480,
               "widthInWorld":10,"heightInWorld":10,"topLeftLocation":[0,0]}
            ]}
            """));
        assertEquals("f7_boss_end", catalog.find(7, 5, 5, 5).image());
    }

    @Test public void markerContainmentIncludesHeightAndExactArenaEdges() throws Exception {
        var arena = DungeonBossMapCatalog.loadBundled().find(7, 73, 230, 48);
        var bounds = arena.bounds();
        assertTrue(bounds.contains(33, 213, 11));
        assertTrue(bounds.contains(113, 255, 86));
        assertFalse(bounds.contains(73, 212, 48));
        assertFalse(bounds.contains(32.99, 230, 48));
        assertFalse(bounds.contains(73, 230, 86.01));
        assertFalse(bounds.contains(Double.NaN, 230, 48));
    }

    @Test public void terminalsUseStellasSixtyFourBlockScrollingViewport() throws Exception {
        var arena = DungeonBossMapCatalog.loadBundled().find(7, 54, 120, 86);
        var center = arena.view(54, 86);
        assertEquals(64.0, center.worldSize(), 0.0);
        assertEquals(228, center.textureWidth());
        assertEquals(228, center.textureHeight());
        assertEquals(50.0, center.viewX(), 0.0);
        assertEquals(50.0, center.viewZ(), 0.0);
        assertEquals(64.0, center.playerX(54), 0.0);
        assertEquals(64.0, center.playerY(86), 0.0);
        var start = arena.view(-3, 29);
        assertEquals(0.0, start.viewX(), 0.0);
        assertEquals(0.0, start.viewZ(), 0.0);
        var end = arena.view(111, 143);
        assertEquals(100.0, end.viewX(), 0.00001);
        assertEquals(100.0, end.viewZ(), 0.00001);
        assertEquals(128.0, end.playerX(111), 0.0);
        assertEquals(128.0, end.playerY(143), 0.0);
    }

    @Test public void necronCenterAndArenaEdgesAlignWithTheSquareImage() throws Exception {
        var arena = DungeonBossMapCatalog.loadBundled().find(7, 54, 75, 76);
        assertEquals("f7_boss_s4", arena.image());
        var view = arena.view(54, 76);
        // The central island is at the image center, not southeast at (77.62, 77.62).
        assertEquals(64.0, view.playerX(54), 0.0001);
        assertEquals(64.0, view.playerY(76), 0.0001);
        // Correct the scale over the whole arena, not just a marker offset at its center.
        assertEquals(0.0, view.playerX(-3), 0.0001);
        assertEquals(0.0, view.playerY(19), 0.0001);
        assertEquals(128.0, view.playerX(111), 0.0001);
        assertEquals(128.0, view.playerY(133), 0.0001);
        assertEquals(128, view.textureWidth());
        assertEquals(128, view.textureHeight());
        assertEquals(0.0, arena.view(111, 133).viewX(), 0.0);
        assertEquals(0.0, arena.view(111, 133).viewZ(), 0.0);
    }

    @Test public void longAndNonSquareArenasPreserveStellasTextureCalibration() throws Exception {
        var catalog = DungeonBossMapCatalog.loadBundled();
        var sadan = catalog.find(6, -9, 70, 45).view(-9, 45);
        assertEquals(44.0, sadan.worldSize(), 0.0);
        assertEquals(128, sadan.textureWidth());
        assertEquals(281, sadan.textureHeight());
        assertEquals(0.0, sadan.viewX(), 0.0);
        assertEquals(64.0, sadan.playerX(-9), 0.001);
        assertEquals(64.0, sadan.playerY(45), 0.001);
        var sadanEnd = sadan.arena().view(-9, 94);
        assertEquals(854.0 * 128 / 388 - 128, sadanEnd.viewZ(), 0.00001);

        var bonzo = catalog.find(1, -42, 80, 21).view(-42, 21);
        assertEquals(46.0, bonzo.worldSize(), 0.0);
        assertEquals(128, bonzo.textureWidth());
        assertEquals(128, bonzo.textureHeight());
        assertEquals(0.0, bonzo.playerX(-65), 0.0);
        assertEquals(128.0, bonzo.playerX(-19), 0.0);
        assertEquals(48.0 / 46 * 128, bonzo.playerY(45), 0.0001);
    }
}
