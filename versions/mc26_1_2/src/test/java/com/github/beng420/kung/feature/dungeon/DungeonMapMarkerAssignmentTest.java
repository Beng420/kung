package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

public final class DungeonMapMarkerAssignmentTest {
    private static DungeonRunStats.DungeonPlayerSlot slot(int index, String name) {
        return new DungeonRunStats.DungeonPlayerSlot(index, UUID.nameUUIDFromBytes(name.getBytes()), name, null);
    }

    private static DungeonMapCheckmarkReader.MapPixel at(int x, int z) {
        return new DungeonMapCheckmarkReader.MapPixel(x, z);
    }

    @Test
    public void revivedTeammateOutOfTabOrderDoesNotTakeTheLoadedHealersMarker() {
        var archer = slot(1, "zett3");
        var healer = slot(2, "xtentoria");
        var mage = slot(3, "t14s");
        var tank = slot(4, "yrkuna");
        // After the revive the markers came as archer, mage, healer, tank; the tab order is A, H, M, T.
        List<DungeonMapCheckmarkReader.MapPixel> markers = List.of(at(10, 10), at(80, 90), at(40, 40), at(120, 20));
        Map<UUID, DungeonMapCheckmarkReader.MapPixel> loaded = Map.of(archer.uuid(), at(11, 10), healer.uuid(), at(41, 39));

        List<DungeonRunStats.DungeonPlayerSlot> assigned = DungeonMapFeature.teammateSlotsByDecoration(
            markers, List.of(slot(0, "self"), archer, healer, mage, tank), s -> loaded.get(s.uuid()));

        assertEquals(List.of(archer, mage, healer, tank), assigned);
    }
}
