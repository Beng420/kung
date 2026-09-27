package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.beng420.kung.feature.dungeon.DungeonLiveMapWriter.CellKey;
import com.github.beng420.kung.feature.dungeon.DungeonLiveMapWriter.DoorRenderInfo;
import com.github.beng420.kung.feature.dungeon.DungeonLiveMapWriter.MatchRenderPlan;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class DungeonEnteredRoomsTest {
    // Alpha (two cells) and Gamma are entered; Beta, behind Gamma, is not.
    private static final CellKey ALPHA = new CellKey(0, 0);
    private static final CellKey ALPHA_2 = new CellKey(1, 0);
    private static final CellKey GAMMA = new CellKey(2, 0);
    private static final CellKey BETA = new CellKey(3, 0);
    private static final CellKey ALPHA_GAMMA_DOOR = new CellKey(3, 0);
    private static final CellKey GAMMA_BETA_DOOR = new CellKey(5, 0);
    // Between Beta and the cell after it: neither side entered.
    private static final CellKey BETA_OUTER_DOOR = new CellKey(7, 0);

    @Test
    public void onlyEnteredRoomsAndTheirExitsReachTheMap() {
        MatchRenderPlan view = DungeonEnteredRooms.filter(plan(Set.of(ALPHA, ALPHA_2, GAMMA)));

        assertEquals(List.of("Alpha", "Gamma"), view.matches().stream().map(match -> match.template().name()).toList());
        assertTrue(view.predictedRooms().isEmpty());
        assertEquals(Set.of(ALPHA, ALPHA_2, GAMMA), view.roomOwners().keySet());
        assertEquals(Set.of(ALPHA, ALPHA_2, GAMMA), view.roomTypes().keySet());
        assertEquals(Set.of(new CellKey(1, 0)), view.internalDoors());
        // Every exit of an entered room shows; the one into Beta only as the door you can see.
        assertEquals(Set.of(ALPHA_GAMMA_DOOR, GAMMA_BETA_DOOR), view.externalDoors().keySet());
        assertEquals(WITHER, view.externalDoors().get(ALPHA_GAMMA_DOOR));
        assertEquals(new DoorRenderInfo(DungeonDoorKind.OPEN, RoomType.UNKNOWN, false, false),
            view.externalDoors().get(GAMMA_BETA_DOOR));
        assertTrue(view.hints().isEmpty());
        assertTrue(view.remoteRoomCells().isEmpty());
        assertTrue(view.mapPuzzleNames().isEmpty());
        assertTrue(view.openedSpecialDoorCells().isEmpty());
        assertNull(view.fairyEntranceDoor());
        assertTrue(view.bloodRushPath().isEmpty());
        assertFalse(DungeonRoomRenderLayout.from(view).cells().contains(BETA));

        MatchRenderPlan all = DungeonEnteredRooms.filter(plan(Set.of(ALPHA, ALPHA_2, GAMMA, BETA)));
        assertEquals(3, all.matches().size());
        assertEquals(Set.of(ALPHA_GAMMA_DOOR, GAMMA_BETA_DOOR, BETA_OUTER_DOOR), all.externalDoors().keySet());
        assertEquals(INTO_PUZZLE, all.externalDoors().get(GAMMA_BETA_DOOR));
    }

    @Test
    public void everyExitOfAnEnteredRoomLeadsToOneUnexploredCell() {
        // Beta could be a 2x2; until someone enters it, it is the one cell behind Gamma's exit.
        assertEquals(Set.of(BETA), DungeonEnteredRooms.unexplored(DungeonEnteredRooms.filter(plan(Set.of(ALPHA, ALPHA_2, GAMMA)))));
        assertEquals(Set.of(new CellKey(4, 0)),
            DungeonEnteredRooms.unexplored(DungeonEnteredRooms.filter(plan(Set.of(ALPHA, ALPHA_2, GAMMA, BETA)))));
    }

    @Test
    public void aKnownFairyRoomsEntranceIsDrawnAsAWitherDoor() {
        // Gamma -> Beta is the Fairy entrance; it only turns into a wither door once the Fairy room is entered.
        MatchRenderPlan unknownFairy = DungeonEnteredRooms.filter(plan(Set.of(ALPHA, ALPHA_2, GAMMA), RoomType.FAIRY));
        assertEquals(DungeonDoorKind.OPEN, unknownFairy.externalDoors().get(GAMMA_BETA_DOOR).kind());
        MatchRenderPlan knownFairy = DungeonEnteredRooms.filter(plan(Set.of(ALPHA, ALPHA_2, GAMMA, BETA), RoomType.FAIRY));
        DoorRenderInfo entrance = knownFairy.externalDoors().get(GAMMA_BETA_DOOR);
        assertEquals(DungeonDoorKind.WITHER, entrance.kind());
        assertFalse("A special colour would hide the wither door", entrance.colorAsSpecial());
    }

    @Test
    public void totalsWaitUntilEveryRoomIsEnteredAndEveryOtherCellIsKnownEmpty() {
        Set<CellKey> rooms = Set.of(ALPHA, ALPHA_2, GAMMA, BETA);
        assertFalse(DungeonEnteredRooms.allEntered(snapshot(rooms, Set.of()), plan(Set.of(ALPHA, ALPHA_2, GAMMA))));
        assertTrue(DungeonEnteredRooms.allEntered(snapshot(rooms, Set.of()), plan(rooms)));
        // A cell that never fully loaded could still hide a room.
        assertFalse(DungeonEnteredRooms.allEntered(snapshot(rooms, Set.of(new CellKey(5, 5))), plan(rooms)));
        // Hypixel's map showing a room there proves one even without a scan.
        DungeonMapSnapshot grayRoom = snapshot(rooms, Set.of());
        grayRoom.observeMapVisibleRoom(4, 4);
        assertFalse(DungeonEnteredRooms.allEntered(grayRoom, plan(rooms)));
    }

    private static final DoorRenderInfo WITHER = new DoorRenderInfo(DungeonDoorKind.WITHER, RoomType.NORMAL, false, true);
    // Coloured as the puzzle it leads into - exactly what an unentered room must not reveal.
    private static final DoorRenderInfo INTO_PUZZLE = new DoorRenderInfo(DungeonDoorKind.OPEN, RoomType.PUZZLE, true, false);

    private static MatchRenderPlan plan(Set<CellKey> visited) {
        return plan(visited, RoomType.PUZZLE);
    }

    private static MatchRenderPlan plan(Set<CellKey> visited, RoomType betaType) {
        var alpha = new DungeonKnownRoomCatalog.MatchedRoom(template("Alpha"), List.of(component(ALPHA), component(ALPHA_2)));
        var gamma = new DungeonKnownRoomCatalog.MatchedRoom(template("Gamma"), List.of(component(GAMMA)));
        var beta = new DungeonKnownRoomCatalog.MatchedRoom(template("Beta"), List.of(component(BETA)));
        var predictedBeta = new DungeonKnownRoomCatalog.MatchedRoom(template("Beta"), List.of(component(BETA)));
        Set<CellKey> cells = Set.of(ALPHA, ALPHA_2, GAMMA, BETA);
        Map<CellKey, RoomType> types = Map.of(ALPHA, RoomType.NORMAL, ALPHA_2, RoomType.NORMAL,
            GAMMA, RoomType.NORMAL, BETA, betaType);
        Map<CellKey, String> owners = Map.of(ALPHA, "alpha", ALPHA_2, "alpha", GAMMA, "gamma", BETA, "beta");
        return new MatchRenderPlan(List.of(alpha, gamma, beta), List.of(predictedBeta), cells,
            Set.of(new CellKey(1, 0)), Map.of(ALPHA_GAMMA_DOOR, WITHER, GAMMA_BETA_DOOR, INTO_PUZZLE,
                BETA_OUTER_DOOR, INTO_PUZZLE), types, owners,
            Map.of(BETA, new DungeonKnownRoomCatalog.KnownCoreHint("Beta", RoomType.PUZZLE, 0, 0, false)),
            Set.of(BETA), Map.of(), visited, Set.of(), Set.of(), Set.of(GAMMA_BETA_DOOR), GAMMA_BETA_DOOR,
            List.of(GAMMA_BETA_DOOR), Map.of(BETA, "Blaze"));
    }

    /** Every room cell scanned and fully loaded: rooms with a core, the rest empty. */
    private static DungeonMapSnapshot snapshot(Set<CellKey> rooms, Set<CellKey> unloaded) {
        List<DungeonScanPoint> points = new ArrayList<>();
        for (int z = 0; z <= DungeonScanUtils.SCAN_GRID_SIZE / 2; z++) {
            for (int x = 0; x <= DungeonScanUtils.SCAN_GRID_SIZE / 2; x++) {
                CellKey cell = new CellKey(x, z);
                if (unloaded.contains(cell)) continue;
                points.add(new DungeonScanPoint(x * 2, z * 2, 0, 0, DungeonScanPointKind.ROOM, true,
                    rooms.contains(cell) ? 1000 + x : 0, 0, 0, DungeonDoorKind.NONE, true));
            }
        }
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        snapshot.addScan(1, 1L, -1, -1, points);
        return snapshot;
    }

    private static DungeonKnownRoomCatalog.RoomTemplate template(String name) {
        return new DungeonKnownRoomCatalog.RoomTemplate(name, RoomType.NORMAL, 3, 1, false, 0, List.of());
    }

    private static DungeonKnownRoomCatalog.MatchedComponent component(CellKey cell) {
        return new DungeonKnownRoomCatalog.MatchedComponent(cell.x(), cell.z(), 0);
    }
}
