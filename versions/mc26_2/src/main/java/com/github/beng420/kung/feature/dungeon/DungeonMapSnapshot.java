package com.github.beng420.kung.feature.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.github.beng420.kung.feature.dungeon.room.RoomType;

public final class DungeonMapSnapshot {
    private final Map<GridKey, ObservedPoint> points = new HashMap<>();
    private final Map<GridKey, ObservedPoint> initialRoomPoints = new HashMap<>();
    private final Set<GridKey> traversedDoors = new HashSet<>();
    private final Set<GridKey> openedLockedDoors = new HashSet<>();
    private final Set<GridKey> visitedRooms = new HashSet<>();
    private final Set<GridKey> clearedRooms = new HashSet<>();
    private final Set<GridKey> completedRooms = new HashSet<>();
    private final Set<GridKey> mapPlayerRooms = new HashSet<>();
    private int lastScanNumber = -1;
    private long lastTimestamp;
    private int playerGridX;
    private int playerGridZ;
    private long revision;
    private GridKey previousPlayerRoom;
    private GridKey startRoom;

    public void reset() {
        points.clear();
        initialRoomPoints.clear();
        traversedDoors.clear();
        openedLockedDoors.clear();
        visitedRooms.clear();
        clearedRooms.clear();
        completedRooms.clear();
        mapPlayerRooms.clear();
        lastScanNumber = -1;
        lastTimestamp = 0;
        playerGridX = 0;
        playerGridZ = 0;
        revision++;
        previousPlayerRoom = null;
        startRoom = null;
    }

    public void addScan(
        int scanNumber,
        long timestamp,
        int currentPlayerGridX,
        int currentPlayerGridZ,
        List<DungeonScanPoint> scanPoints
    ) {
        lastScanNumber = scanNumber;
        lastTimestamp = timestamp;
        observePlayerGrid(currentPlayerGridX, currentPlayerGridZ);
        boolean changed = false;

        for (DungeonScanPoint point : scanPoints) {
            if (!point.loaded()) {
                continue;
            }

            GridKey key = new GridKey(point.gridX(), point.gridZ());
            recordInitialRoomPoint(key, point, scanNumber, timestamp);
            ObservedPoint previous = points.get(key);
            if (previous == null || shouldReplace(previous, point)) {
                recordObservedCoreTransition(previous, point);
                recordDoorTransition(previous, point);
                points.put(key, new ObservedPoint(point, scanNumber, timestamp));
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
    }

    public void observePlayerGrid(int currentPlayerGridX, int currentPlayerGridZ) {
        if (playerGridX != currentPlayerGridX || playerGridZ != currentPlayerGridZ) {
            playerGridX = currentPlayerGridX;
            playerGridZ = currentPlayerGridZ;
            revision++;
        }

        if (!isValidRoomGrid(currentPlayerGridX, currentPlayerGridZ)) {
            return;
        }

        GridKey currentPlayerRoom = new GridKey(currentPlayerGridX, currentPlayerGridZ);
        observeVisitedRoom(currentPlayerRoom);
        if (startRoom == null) {
            startRoom = toScanRoom(currentPlayerRoom);
            revision++;
        }

        if (previousPlayerRoom != null && isAdjacent(previousPlayerRoom, currentPlayerRoom)) {
            if (traversedDoors.add(doorBetween(previousPlayerRoom, currentPlayerRoom))) {
                revision++;
            }
        }

        previousPlayerRoom = currentPlayerRoom;
    }

    public void observeVisitedRoom(int roomGridX, int roomGridZ) {
        if (!isValidRoomGrid(roomGridX, roomGridZ)) {
            return;
        }
        observeVisitedRoom(new GridKey(roomGridX, roomGridZ));
    }

    public void observeMapPlayerRoom(int roomGridX, int roomGridZ) {
        if (!isValidRoomGrid(roomGridX, roomGridZ)) {
            return;
        }

        GridKey room = new GridKey(roomGridX, roomGridZ);
        boolean changed = mapPlayerRooms.add(room);
        if (visitedRooms.add(room)) {
            changed = true;
        }
        if (changed) {
            revision++;
        }
    }

    private void observeVisitedRoom(GridKey roomGrid) {
        if (visitedRooms.add(roomGrid)) {
            revision++;
        }
    }

    public void observeRoomClearState(int roomGridX, int roomGridZ, DungeonMapClearState clearState) {
        if (!isValidRoomGrid(roomGridX, roomGridZ)) {
            return;
        }

        GridKey room = new GridKey(roomGridX, roomGridZ);
        boolean changed = false;
        if (clearState == DungeonMapClearState.UNCLEARED) {
            if (completedRooms.remove(room)) {
                revision++;
            }
            return;
        }

        changed = clearedRooms.add(room);
        if (clearState == DungeonMapClearState.COMPLETED) {
            changed = completedRooms.add(room) || changed;
        } else {
            changed = completedRooms.remove(room) || changed;
        }
        if (changed) {
            revision++;
        }
    }

    public ObservedPoint pointAt(int gridX, int gridZ) {
        return points.get(new GridKey(gridX, gridZ));
    }

    public ObservedPoint initialRoomPointAt(int roomGridX, int roomGridZ) {
        return initialRoomPoints.get(new GridKey(roomGridX * 2, roomGridZ * 2));
    }

    public boolean isStartRoom(int gridX, int gridZ) {
        return startRoom != null && startRoom.gridX() == gridX && startRoom.gridZ() == gridZ;
    }

    public boolean isTraversedDoor(int gridX, int gridZ) {
        return traversedDoors.contains(new GridKey(gridX, gridZ));
    }

    public boolean isVisitedRoom(int roomGridX, int roomGridZ) {
        return visitedRooms.contains(new GridKey(roomGridX, roomGridZ));
    }

    public boolean isClearedRoom(int roomGridX, int roomGridZ) {
        return clearedRooms.contains(new GridKey(roomGridX, roomGridZ));
    }

    public boolean isCompletedRoom(int roomGridX, int roomGridZ) {
        return completedRooms.contains(new GridKey(roomGridX, roomGridZ));
    }

    public Set<GridKey> mapPlayerRooms() {
        return Set.copyOf(mapPlayerRooms);
    }

    public GridKey startRoom() {
        return startRoom;
    }

    public Set<GridKey> traversedDoors() {
        return Set.copyOf(traversedDoors);
    }

    public Set<GridKey> openedLockedDoors() {
        return Set.copyOf(openedLockedDoors);
    }

    public int lastScanNumber() {
        return lastScanNumber;
    }

    public long lastTimestamp() {
        return lastTimestamp;
    }

    public int playerGridX() {
        return playerGridX;
    }

    public int playerGridZ() {
        return playerGridZ;
    }

    public int observedRoomCount() {
        int count = 0;
        for (ObservedPoint point : points.values()) {
            if (point.point().kind() == DungeonScanPointKind.ROOM
                && !DungeonRoomClassifier.isEmptyCore(point.point().coreHash())) {
                count++;
            }
        }
        return count;
    }

    public long revision() {
        return revision;
    }

    private static boolean shouldReplace(ObservedPoint previous, DungeonScanPoint next) {
        if (next.kind() != previous.point().kind()) {
            return true;
        }

        if (next.kind() == DungeonScanPointKind.ROOM) {
            return shouldReplaceRoom(previous.point().coreHash(), next.coreHash());
        }

        if (next.kind() == DungeonScanPointKind.DOOR) {
            return shouldReplaceDoor(
                previous.point().doorBlockId(),
                previous.point().doorKind(),
                next.doorBlockId(),
                next.doorKind()
            );
        }

        return false;
    }

    private void recordInitialRoomPoint(GridKey key, DungeonScanPoint point, int scanNumber, long timestamp) {
        if (point.kind() != DungeonScanPointKind.ROOM
            || DungeonRoomClassifier.isEmptyCore(point.coreHash())
            || initialRoomPoints.containsKey(key)) {
            return;
        }
        initialRoomPoints.put(key, new ObservedPoint(point, scanNumber, timestamp));
    }

    private static boolean shouldReplaceRoom(int previousCoreHash, int nextCoreHash) {
        if (previousCoreHash == nextCoreHash) {
            return false;
        }

        boolean previousEmpty = DungeonRoomClassifier.isEmptyCore(previousCoreHash);
        boolean nextEmpty = DungeonRoomClassifier.isEmptyCore(nextCoreHash);
        if (previousEmpty != nextEmpty) {
            return previousEmpty;
        }
        if (previousEmpty) {
            return true;
        }

        boolean previousKnownRoom = DungeonKnownRoomCatalog.isKnownCoreHash(previousCoreHash);
        boolean nextKnownRoom = DungeonKnownRoomCatalog.isKnownCoreHash(nextCoreHash);
        if (previousKnownRoom != nextKnownRoom) {
            return !previousKnownRoom;
        }

        RoomType previousType = DungeonRoomClassifier.classifyRoom(previousCoreHash);
        RoomType nextType = DungeonRoomClassifier.classifyRoom(nextCoreHash);
        return previousType == nextType || previousType == RoomType.NORMAL;
    }

    private static boolean shouldReplaceDoor(
        int previousDoorBlockId,
        DungeonDoorKind previousKind,
        int nextDoorBlockId,
        DungeonDoorKind nextKind
    ) {
        if (previousDoorBlockId == nextDoorBlockId && previousKind == nextKind) {
            return false;
        }

        boolean previousVisible = previousKind.visible();
        boolean nextVisible = nextKind.visible();
        if (previousVisible && !nextVisible) {
            return false;
        }
        if (previousVisible != nextVisible) {
            return nextVisible;
        }
        return true;
    }

    private static void recordObservedCoreTransition(ObservedPoint previous, DungeonScanPoint next) {
        if (previous == null || next.kind() != DungeonScanPointKind.ROOM) {
            return;
        }

        DungeonKnownRoomCatalog.recordObservedCoreTransition(
            previous.point().coreHash(),
            next.coreHash()
        );
    }

    private void recordDoorTransition(ObservedPoint previous, DungeonScanPoint next) {
        if (previous == null || next.kind() != DungeonScanPointKind.DOOR) {
            return;
        }
        DungeonDoorKind previousKind = previous.point().doorKind();
        if ((previousKind == DungeonDoorKind.WITHER || previousKind == DungeonDoorKind.BLOOD)
            && next.doorKind() == DungeonDoorKind.OPEN) {
            openedLockedDoors.add(new GridKey(next.gridX(), next.gridZ()));
        }
    }

    private static boolean isValidRoomGrid(int gridX, int gridZ) {
        return gridX >= 0
            && gridZ >= 0
            && gridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && gridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    private static boolean isAdjacent(GridKey firstRoom, GridKey secondRoom) {
        int distanceX = Math.abs(firstRoom.gridX() - secondRoom.gridX());
        int distanceZ = Math.abs(firstRoom.gridZ() - secondRoom.gridZ());
        return distanceX + distanceZ == 1;
    }

    private static GridKey doorBetween(GridKey firstRoom, GridKey secondRoom) {
        return new GridKey(
            firstRoom.gridX() + secondRoom.gridX(),
            firstRoom.gridZ() + secondRoom.gridZ()
        );
    }

    private static GridKey toScanRoom(GridKey roomGrid) {
        return new GridKey(roomGrid.gridX() * 2, roomGrid.gridZ() * 2);
    }

    public record GridKey(int gridX, int gridZ) {
    }

    public record ObservedPoint(DungeonScanPoint point, int scanNumber, long timestamp) {
    }
}
