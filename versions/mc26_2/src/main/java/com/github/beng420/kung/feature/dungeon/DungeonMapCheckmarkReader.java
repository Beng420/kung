package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.feature.dungeon.room.RoomType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

final class DungeonMapCheckmarkReader {
    private static final int MAP_SIZE = 128;
    private static final int MAP_ROOM_GAP = 4;
    private static final int MAP_SEARCH_STEP = 10;
    private static final int MIN_ROOM_SIZE = 5;
    private static final int CHECKMARK_SCAN_RADIUS = 4;
    private static final int CHECKMARK_MIN_PIXELS = 3;
    private static final int CHECKMARK_MAX_PIXELS = 44;
    private static final int RED_CHECK_COLOR = 18;
    private static final int GREEN_CHECK_COLOR = 30;
    private static final int ENTRANCE_COLOR = GREEN_CHECK_COLOR;
    private static final int WHITE_CHECK_COLOR = 34;
    private static final int PLAYER_DECORATION_SEARCH_RADIUS = 3;

    private DungeonMapCheckmarkReader() {
    }

    static void observe(Minecraft client, DungeonMapSnapshot snapshot) {
        MapItemSavedData map = mapData(client);
        if (map == null) {
            return;
        }

        MapAnchor anchor = mapAnchor(map, snapshot);
        if (anchor == null) {
            return;
        }

        int stride = anchor.roomSize() + MAP_ROOM_GAP;
        for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                int physicalX = physicalRoomX(roomGridX);
                int physicalZ = physicalRoomZ(roomGridZ);
                int mapX = anchor.mapEntranceX()
                    + ((physicalX - anchor.physicalEntranceX()) / DungeonScanUtils.ROOM_SIZE_BLOCKS) * stride;
                int mapZ = anchor.mapEntranceZ()
                    + ((physicalZ - anchor.physicalEntranceZ()) / DungeonScanUtils.ROOM_SIZE_BLOCKS) * stride;
                DungeonMapClearState clearState = checkmarkState(map, mapX, mapZ, anchor.roomSize());
                snapshot.observeRoomClearState(roomGridX, roomGridZ, clearState);
                if (clearState != DungeonMapClearState.UNCLEARED || isVisitedRoom(map, mapX, mapZ, anchor.roomSize())) {
                    snapshot.observeVisitedRoom(roomGridX, roomGridZ);
                }
            }
        }
    }

    static MapRoom roomFromDecoration(MapDecoration decoration, Minecraft client, DungeonMapSnapshot snapshot) {
        MapItemSavedData map = mapData(client);
        if (map == null) {
            return null;
        }

        MapAnchor anchor = mapAnchor(map, snapshot);
        if (anchor == null) {
            return null;
        }

        MapPixel center = decorationPixel(decoration);
        int bestDistance = Integer.MAX_VALUE;
        MapRoom bestRoom = null;
        for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                MapPixel roomCenter = mapRoomCenter(anchor, roomGridX, roomGridZ);
                int dx = roomCenter.x() - center.x();
                int dz = roomCenter.z() - center.z();
                int distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestRoom = new MapRoom(roomGridX, roomGridZ, roomCenter.x(), roomCenter.z(), anchor.roomSize());
                }
            }
        }

        int radius = Math.max(anchor.roomSize() / 2 + PLAYER_DECORATION_SEARCH_RADIUS, 6);
        return bestDistance <= radius * radius ? bestRoom : null;
    }

    static MapPixel mapPixelForDecoration(MapDecoration decoration) {
        return decorationPixel(decoration);
    }

    static MapPixel overlayPixelForDecoration(
        MapDecoration decoration,
        Minecraft client,
        DungeonMapSnapshot snapshot,
        int overlayRoomSize,
        int overlayDoorSize,
        int overlayCellGap
    ) {
        MapItemSavedData map = mapData(client);
        if (map == null) {
            return null;
        }

        MapAnchor anchor = mapAnchor(map, snapshot);
        DungeonMapSnapshot.GridKey entranceRoom = entranceRoom(snapshot);
        if (anchor == null || entranceRoom == null) {
            return null;
        }

        MapPixel decorationPixel = decorationPixel(decoration);
        int mapStride = anchor.roomSize() + MAP_ROOM_GAP;
        int overlayStride = overlayRoomSize + overlayDoorSize + overlayCellGap * 2;
        int overlayEntranceCenterX = scanGridToOverlayPixel(entranceRoom.gridX() * 2, overlayRoomSize, overlayDoorSize, overlayCellGap)
            + overlayRoomSize / 2;
        int overlayEntranceCenterZ = scanGridToOverlayPixel(entranceRoom.gridZ() * 2, overlayRoomSize, overlayDoorSize, overlayCellGap)
            + overlayRoomSize / 2;
        int mapEntranceCenterX = anchor.mapEntranceX() + anchor.roomSize() / 2;
        int mapEntranceCenterZ = anchor.mapEntranceZ() + anchor.roomSize() / 2;
        int overlayGridSize = overlayGridPixelSize(overlayRoomSize, overlayDoorSize, overlayCellGap);
        int overlayX = overlayEntranceCenterX
            + Math.round((decorationPixel.x() - mapEntranceCenterX) * overlayStride / (float) mapStride);
        int overlayZ = overlayEntranceCenterZ
            + Math.round((decorationPixel.z() - mapEntranceCenterZ) * overlayStride / (float) mapStride);
        return new MapPixel(
            Math.clamp(overlayX, 0, overlayGridSize),
            Math.clamp(overlayZ, 0, overlayGridSize)
        );
    }

    private static MapItemSavedData mapData(Minecraft client) {
        if (client.level == null || client.player == null) {
            return null;
        }
        return DungeonMapItems.mapData(client);
    }

    private static MapAnchor mapAnchor(MapItemSavedData map, DungeonMapSnapshot snapshot) {
        DungeonMapSnapshot.GridKey entranceRoom = entranceRoom(snapshot);
        if (entranceRoom == null) {
            return null;
        }

        MapEntrance mapEntrance = mapEntrance(map);
        if (mapEntrance == null) {
            return null;
        }

        return new MapAnchor(
            mapEntrance.x(),
            mapEntrance.z(),
            mapEntrance.roomSize(),
            physicalRoomX(entranceRoom.gridX()),
            physicalRoomZ(entranceRoom.gridZ())
        );
    }

    private static DungeonMapSnapshot.GridKey entranceRoom(DungeonMapSnapshot snapshot) {
        for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                DungeonMapSnapshot.ObservedPoint point = snapshot.pointAt(roomGridX * 2, roomGridZ * 2);
                if (point == null
                    || point.point().kind() != DungeonScanPointKind.ROOM
                    || DungeonRoomClassifier.isEmptyCore(point.point().coreHash())) {
                    continue;
                }
                if (DungeonRoomClassifier.classifyRoom(point.point().coreHash()) == RoomType.START) {
                    return new DungeonMapSnapshot.GridKey(roomGridX, roomGridZ);
                }
            }
        }

        DungeonMapSnapshot.GridKey startRoom = snapshot.startRoom();
        if (startRoom == null) {
            return null;
        }
        return new DungeonMapSnapshot.GridKey(startRoom.gridX() / 2, startRoom.gridZ() / 2);
    }

    private static MapEntrance mapEntrance(MapItemSavedData map) {
        MapPixel mapPlayerPos = mapPlayerPos(map);
        if (mapPlayerPos == null) {
            return null;
        }

        Queue<MapPixel> toCheck = new ArrayDeque<>();
        Set<MapPixel> checked = new HashSet<>();
        toCheck.add(mapPlayerPos);
        checked.add(mapPlayerPos);

        while (!toCheck.isEmpty()) {
            MapPixel pos = toCheck.remove();
            if (isEntranceColor(map, pos.x(), pos.z())) {
                MapEntrance entrance = mapEntranceAt(map, pos);
                if (entrance != null) {
                    return entrance;
                }
            }

            addEntranceSearchPos(toCheck, checked, pos.x() - MAP_SEARCH_STEP, pos.z());
            addEntranceSearchPos(toCheck, checked, pos.x(), pos.z() - MAP_SEARCH_STEP);
            addEntranceSearchPos(toCheck, checked, pos.x() + MAP_SEARCH_STEP, pos.z());
            addEntranceSearchPos(toCheck, checked, pos.x(), pos.z() + MAP_SEARCH_STEP);
        }

        return null;
    }

    private static void addEntranceSearchPos(Queue<MapPixel> toCheck, Set<MapPixel> checked, int x, int z) {
        if (!insideMap(x, z)) {
            return;
        }

        MapPixel pos = new MapPixel(x, z);
        if (checked.add(pos)) {
            toCheck.add(pos);
        }
    }

    private static MapEntrance mapEntranceAt(MapItemSavedData map, MapPixel pos) {
        int x = pos.x();
        int z = pos.z();
        while (isEntranceColor(map, x - 1, z)) {
            x--;
        }
        while (isEntranceColor(map, x, z - 1)) {
            z--;
        }

        int roomSize = mapRoomSize(map, x, z);
        if (roomSize <= MIN_ROOM_SIZE) {
            return null;
        }
        return new MapEntrance(x, z, roomSize);
    }

    private static int mapRoomSize(MapItemSavedData map, int roomX, int roomZ) {
        int size = 0;
        while (isEntranceColor(map, roomX + size, roomZ)) {
            size++;
        }
        return size;
    }

    private static MapPixel mapPlayerPos(MapItemSavedData map) {
        for (MapDecoration decoration : map.getDecorations()) {
            if (decoration.type().equals(MapDecorationTypes.FRAME)) {
                return decorationPixel(decoration);
            }
        }
        return null;
    }

    private static MapPixel decorationPixel(MapDecoration decoration) {
        return new MapPixel((decoration.x() >> 1) + MAP_SIZE / 2, (decoration.y() >> 1) + MAP_SIZE / 2);
    }

    private static MapPixel mapRoomCenter(MapAnchor anchor, int roomGridX, int roomGridZ) {
        int stride = anchor.roomSize() + MAP_ROOM_GAP;
        int physicalX = physicalRoomX(roomGridX);
        int physicalZ = physicalRoomZ(roomGridZ);
        int mapX = anchor.mapEntranceX()
            + ((physicalX - anchor.physicalEntranceX()) / DungeonScanUtils.ROOM_SIZE_BLOCKS) * stride
            + anchor.roomSize() / 2;
        int mapZ = anchor.mapEntranceZ()
            + ((physicalZ - anchor.physicalEntranceZ()) / DungeonScanUtils.ROOM_SIZE_BLOCKS) * stride
            + anchor.roomSize() / 2;
        return new MapPixel(mapX, mapZ);
    }

    private static int scanGridToOverlayPixel(int gridPosition, int roomSize, int doorSize, int cellGap) {
        int pixel = 0;
        for (int index = 0; index < gridPosition; index++) {
            pixel += (index & 1) == 0 ? roomSize : doorSize;
            pixel += cellGap;
        }
        return pixel;
    }

    private static int overlayGridPixelSize(int roomSize, int doorSize, int cellGap) {
        return scanGridToOverlayPixel(DungeonScanUtils.SCAN_GRID_SIZE, roomSize, doorSize, cellGap);
    }

    private static DungeonMapClearState checkmarkState(MapItemSavedData map, int roomX, int roomZ, int roomSize) {
        int halfRoomSize = roomSize / 2;
        int x = roomX + halfRoomSize;
        int z = roomZ + halfRoomSize;
        for (int offset = 0; offset < halfRoomSize; offset++) {
            int checkX = x;
            int checkZ = z + offset;
            int color = colorAt(map, checkX, checkZ);
            if (color == GREEN_CHECK_COLOR) {
                if (isCheckmarkGlyph(map, checkX, checkZ, GREEN_CHECK_COLOR)) {
                    return DungeonMapClearState.COMPLETED;
                }
                continue;
            }
            if (color == WHITE_CHECK_COLOR || color == RED_CHECK_COLOR) {
                if (isCheckmarkGlyph(map, checkX, checkZ, color)) {
                    return DungeonMapClearState.CLEARED;
                }
            }
        }
        return DungeonMapClearState.UNCLEARED;
    }

    private static boolean isCheckmarkGlyph(MapItemSavedData map, int centerX, int centerZ, int checkColor) {
        int count = 0;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int z = centerZ - CHECKMARK_SCAN_RADIUS; z <= centerZ + CHECKMARK_SCAN_RADIUS; z++) {
            for (int x = centerX - CHECKMARK_SCAN_RADIUS; x <= centerX + CHECKMARK_SCAN_RADIUS; x++) {
                if (colorAt(map, x, z) != checkColor) {
                    continue;
                }
                count++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }

        if (count < CHECKMARK_MIN_PIXELS || count > CHECKMARK_MAX_PIXELS) {
            return false;
        }
        return maxX - minX >= 1 && maxZ - minZ >= 1;
    }

    private static boolean isVisitedRoom(MapItemSavedData map, int roomX, int roomZ, int roomSize) {
        int interiorStart = Math.max(1, roomSize / 5);
        int interiorEnd = Math.max(interiorStart + 1, roomSize - interiorStart);
        int visitedPixels = 0;
        int sampledPixels = 0;
        for (int z = roomZ + interiorStart; z < roomZ + interiorEnd; z++) {
            for (int x = roomX + interiorStart; x < roomX + interiorEnd; x++) {
                int color = colorAt(map, x, z);
                if (color < 0) {
                    continue;
                }
                sampledPixels++;
                if (isVisitedRoomColor(color)) {
                    visitedPixels++;
                }
            }
        }
        return sampledPixels > 0 && visitedPixels >= Math.max(3, sampledPixels / 3);
    }

    private static boolean isVisitedRoomColor(int color) {
        if (color == RED_CHECK_COLOR || color == GREEN_CHECK_COLOR || color == WHITE_CHECK_COLOR) {
            return true;
        }

        int baseColor = color >> 2;
        int brightness = color & 3;
        if (baseColor == 0 || brightness == 0) {
            return false;
        }

        return switch (baseColor) {
            case 4, 7, 10, 13, 15, 16, 17, 18, 19, 20, 23, 24, 25, 26, 27, 28, 30, 31, 34, 35, 36, 37, 38, 39,
                40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55 -> true;
            default -> false;
        };
    }

    private static boolean isEntranceColor(MapItemSavedData map, int x, int z) {
        return colorAt(map, x, z) == ENTRANCE_COLOR;
    }

    private static int physicalRoomX(int roomGridX) {
        return physicalRoomCoordinate(DungeonScanUtils.worldXForScanGrid(roomGridX * 2));
    }

    private static int physicalRoomZ(int roomGridZ) {
        return physicalRoomCoordinate(DungeonScanUtils.worldZForScanGrid(roomGridZ * 2));
    }

    private static int physicalRoomCoordinate(int coordinate) {
        int shifted = (int) (coordinate + 8.5);
        return shifted - Math.floorMod(shifted, DungeonScanUtils.ROOM_SIZE_BLOCKS) - 8;
    }

    private static boolean insideMap(int x, int z) {
        return x >= 0 && z >= 0 && x < MAP_SIZE && z < MAP_SIZE;
    }

    private static int colorAt(MapItemSavedData map, int x, int z) {
        if (x < 0 || z < 0 || x >= MAP_SIZE || z >= MAP_SIZE) {
            return -1;
        }
        return map.colors[x + (z << 7)] & 0xFF;
    }

    private record MapAnchor(int mapEntranceX, int mapEntranceZ, int roomSize, int physicalEntranceX, int physicalEntranceZ) {
    }

    private record MapEntrance(int x, int z, int roomSize) {
    }

    record MapPixel(int x, int z) {
    }

    record MapRoom(int roomGridX, int roomGridZ, int mapX, int mapZ, int roomSize) {
    }
}
