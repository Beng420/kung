package com.github.beng420.kung.feature.dungeon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;

public final class DungeonScan {
    private static final int NO_CORE_HASH = 0;
    private static final int NO_DOOR_BLOCK = 0;

    public int scanPointCount() {
        return DungeonScanUtils.SCAN_GRID_SIZE * DungeonScanUtils.SCAN_GRID_SIZE;
    }

    public List<DungeonScanPoint> scan(ClientLevel level) {
        return scan(level, null);
    }

    public List<DungeonScanPoint> scan(ClientLevel level, DungeonMapSnapshot snapshot) {
        List<DungeonScanPoint> points = new ArrayList<>();

        for (int gridX = 0; gridX < DungeonScanUtils.SCAN_GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < DungeonScanUtils.SCAN_GRID_SIZE; gridZ++) {
                points.add(scanPoint(level, snapshot, gridX, gridZ));
            }
        }

        return points;
    }

    public List<DungeonScanPoint> scanBatch(ClientLevel level, DungeonMapSnapshot snapshot, int startIndex, int limit) {
        int total = scanPointCount();
        int count = Math.clamp(limit, 1, total);
        List<DungeonScanPoint> points = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) {
            int index = Math.floorMod(startIndex + offset, total);
            int gridX = index / DungeonScanUtils.SCAN_GRID_SIZE;
            int gridZ = index % DungeonScanUtils.SCAN_GRID_SIZE;
            points.add(scanPoint(level, snapshot, gridX, gridZ));
        }
        return points;
    }

    private DungeonScanPoint scanPoint(ClientLevel level, DungeonMapSnapshot snapshot, int gridX, int gridZ) {
        int worldX = DungeonScanUtils.worldXForScanGrid(gridX);
        int worldZ = DungeonScanUtils.worldZForScanGrid(gridZ);
        DungeonScanPointKind kind = kindFor(gridX, gridZ);
        boolean loaded = DungeonScanUtils.isChunkLoaded(level, worldX, worldZ);

        int coreHash = NO_CORE_HASH;
        int stableCoreHash = NO_CORE_HASH;
        int doorBlockId = NO_DOOR_BLOCK;
        DungeonDoorKind doorKind = DungeonDoorKind.NONE;
        DungeonMapSnapshot.ObservedPoint previous = snapshot == null
            ? null
            : snapshot.pointAt(gridX, gridZ);

        if (loaded && kind == DungeonScanPointKind.ROOM) {
            if (canReuseRoomCore(previous)) {
                coreHash = previous.point().coreHash();
                stableCoreHash = previous.point().stableCoreHash();
            } else {
                coreHash = DungeonScanUtils.getCoreHash(level, worldX, worldZ);
                stableCoreHash = DungeonScanUtils.getStableCoreHash(level, worldX, worldZ);
            }
        }

        if (loaded && kind == DungeonScanPointKind.DOOR) {
            if (canReuseDoor(previous)) {
                doorBlockId = previous.point().doorBlockId();
                doorKind = previous.point().doorKind();
            } else {
                doorBlockId = DungeonScanUtils.getBlockId(
                    level,
                    worldX,
                    DungeonScanRecorder.doorSampleY(),
                    worldZ
                );
                doorKind = DungeonScanUtils.detectDoorKind(level, gridX, gridZ, worldX, worldZ);
            }
        }

        return new DungeonScanPoint(
            gridX,
            gridZ,
            worldX,
            worldZ,
            kind,
            loaded,
            coreHash,
            stableCoreHash,
            doorBlockId,
            doorKind
        );
    }

    private static boolean canReuseRoomCore(DungeonMapSnapshot.ObservedPoint previous) {
        if (previous == null || previous.point().kind() != DungeonScanPointKind.ROOM) {
            return false;
        }

        int previousCoreHash = previous.point().coreHash();
        int previousStableCoreHash = previous.point().stableCoreHash();
        return !DungeonRoomClassifier.isEmptyCore(previousCoreHash)
            && previousStableCoreHash != 0
            && DungeonKnownRoomCatalog.isStableKnownCoreHash(previousStableCoreHash);
    }

    private static boolean canReuseDoor(DungeonMapSnapshot.ObservedPoint previous) {
        return previous != null
            && previous.point().kind() == DungeonScanPointKind.DOOR
            && previous.point().doorKind() == DungeonDoorKind.OPEN;
    }

    private static DungeonScanPointKind kindFor(int gridX, int gridZ) {
        if (DungeonScanUtils.isRoomScanPoint(gridX, gridZ)) {
            return DungeonScanPointKind.ROOM;
        }
        if (DungeonScanUtils.isDoorScanPoint(gridX, gridZ)) {
            return DungeonScanPointKind.DOOR;
        }
        return DungeonScanPointKind.SEPARATOR;
    }
}
