package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Player-facing room totals. Hypixel's score uses a separate count of room cells. */
record DungeonRoomProgress(int cleared, int opened, int total) {
    static DungeonRoomProgress from(DungeonLiveMapWriter.MatchRenderPlan plan) {
        return plan == null ? new DungeonRoomProgress(0, 0, 0)
            : from(plan.roomOwners(), plan.roomTypes(), plan.clearedRooms(), plan.completedRooms(), plan.visitedRooms());
    }

    static DungeonRoomProgress from(
        Map<DungeonLiveMapWriter.CellKey, String> owners,
        Map<DungeonLiveMapWriter.CellKey, RoomType> types,
        Set<DungeonLiveMapWriter.CellKey> clearedCells,
        Set<DungeonLiveMapWriter.CellKey> completedCells,
        Set<DungeonLiveMapWriter.CellKey> visitedCells
    ) {
        Map<String, Room> rooms = new HashMap<>();
        owners.forEach((cell, owner) -> {
            Room room = rooms.computeIfAbsent(owner, ignored -> new Room());
            room.excluded |= !isClearable(types.getOrDefault(cell, RoomType.UNKNOWN));
            room.cleared |= clearedCells.contains(cell) || completedCells.contains(cell);
            room.opened |= visitedCells.contains(cell) || room.cleared;
        });
        int cleared = 0;
        int opened = 0;
        int total = 0;
        for (Room room : rooms.values()) {
            if (room.excluded) continue;
            total++;
            if (room.cleared) cleared++;
            if (room.opened) opened++;
        }
        return new DungeonRoomProgress(cleared, opened, total);
    }

    static boolean isClearable(RoomType type) {
        return type != RoomType.START && type != RoomType.FAIRY && type != RoomType.BLOOD;
    }

    private static final class Room {
        boolean excluded;
        boolean cleared;
        boolean opened;
    }
}
