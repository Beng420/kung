package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;

import com.github.beng420.kung.feature.dungeon.DungeonLiveMapWriter.CellKey;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class DungeonRoomProgressTest {
    @Test
    public void excludedRoomsNeverInflateClearedOpenedOrTotal() {
        CellKey start = new CellKey(0, 0);
        CellKey fairy = new CellKey(1, 0);
        CellKey blood = new CellKey(2, 0);
        CellKey normal = new CellKey(3, 0);
        Map<CellKey, String> owners = Map.of(start, "start", fairy, "fairy", blood, "blood", normal, "normal");
        Map<CellKey, RoomType> types = Map.of(start, RoomType.START, fairy, RoomType.FAIRY,
            blood, RoomType.BLOOD, normal, RoomType.NORMAL);
        assertEquals(new DungeonRoomProgress(1, 1, 1),
            DungeonRoomProgress.from(owners, types, owners.keySet(), owners.keySet(), owners.keySet()));
    }

    @Test
    public void aWhiteCheckmarkCountsAsClearedWithoutAllSecrets() {
        CellKey first = new CellKey(0, 0);
        CellKey second = new CellKey(1, 0);
        CellKey puzzle = new CellKey(2, 0);
        CellKey trap = new CellKey(3, 0);
        Map<CellKey, String> owners = Map.of(first, "large", second, "large", puzzle, "puzzle", trap, "trap");
        Map<CellKey, RoomType> types = Map.of(first, RoomType.NORMAL, second, RoomType.NORMAL,
            puzzle, RoomType.PUZZLE, trap, RoomType.TRAP);
        assertEquals(new DungeonRoomProgress(2, 2, 3),
            DungeonRoomProgress.from(owners, types, Set.of(second), Set.of(puzzle), Set.of(first)));
    }

    @Test
    public void merelyVisitedRoomsDoNotClaimACompletedClear() {
        CellKey first = new CellKey(0, 0);
        CellKey second = new CellKey(1, 0);
        Map<CellKey, String> owners = Map.of(first, "visited", second, "unopened");
        Map<CellKey, RoomType> types = Map.of(first, RoomType.NORMAL, second, RoomType.YELLOW);
        assertEquals(new DungeonRoomProgress(0, 1, 2),
            DungeonRoomProgress.from(owners, types, Set.of(), Set.of(), Set.of(first)));
        assertEquals(new DungeonRoomProgress(0, 0, 0), DungeonRoomProgress.from(null));
    }

    @Test
    public void lateSpecialRoomRecognitionExcludesTheWholeLogicalRoom() {
        CellKey first = new CellKey(0, 0);
        CellKey second = new CellKey(1, 0);
        Map<CellKey, String> owners = Map.of(first, "fairy", second, "fairy");
        Map<CellKey, RoomType> types = Map.of(first, RoomType.UNKNOWN, second, RoomType.FAIRY);
        assertEquals(new DungeonRoomProgress(0, 0, 0),
            DungeonRoomProgress.from(owners, types, owners.keySet(), owners.keySet(), owners.keySet()));
    }
}
