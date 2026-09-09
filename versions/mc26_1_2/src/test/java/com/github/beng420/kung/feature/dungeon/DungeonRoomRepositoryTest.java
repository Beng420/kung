package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class DungeonRoomRepositoryTest {
    @Test
    public void matchesARoomTemplateWithoutChangingItsIdentity() {
        DungeonKnownRoomCatalog.RoomTemplate template = new DungeonKnownRoomCatalog.RoomTemplate(
            "Test Room",
            com.github.beng420.kung.feature.dungeon.room.RoomType.NORMAL,
            7,
            2,
            false,
            1,
            List.of(new DungeonKnownRoomCatalog.TemplateComponent(0, 0, 101, 1001))
        );
        List<DungeonScanPoint> points = new ArrayList<>();
        for (DungeonKnownRoomCatalog.TemplateComponent component : template.components()) {
            points.add(new DungeonScanPoint(
                component.dx() * 2,
                component.dz() * 2,
                component.dx() * 32,
                component.dz() * 32,
                DungeonScanPointKind.ROOM,
                true,
                component.coreHash(),
                component.stableCoreHash(),
                0,
                DungeonDoorKind.NONE
            ));
        }
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        snapshot.addScan(1, 1L, 5, 5, points);
        assertNotNull(snapshot.pointAt(0, 0));
        assertTrue(template.components().get(0).matches(101, 1001));

        List<DungeonKnownRoomCatalog.MatchedRoom> matches =
            DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), Map.of());

        DungeonKnownRoomCatalog.MatchedRoom matched = matches.stream()
            .filter(match -> match.template().name().equals("Test Room"))
            .findFirst()
            .orElseThrow();
        assertEquals(template.type(), matched.template().type());
        assertEquals(template.secrets(), matched.template().secrets());
        assertEquals(1, matched.components().size());
        assertTrue(matched.contains(0, 0));
    }

    @Test
    public void readsPrinceClassificationFromBundledRoomData() {
        DungeonKnownRoomCatalog.reload();

        assertTrue(DungeonKnownRoomCatalog.hasPrince("Flags"));
        assertFalse(DungeonKnownRoomCatalog.hasPrince("Andesite"));
    }
}
