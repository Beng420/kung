package com.github.beng420.kung.feature.dungeon;

public record DungeonScanPoint(
    int gridX,
    int gridZ,
    int worldX,
    int worldZ,
    DungeonScanPointKind kind,
    boolean loaded,
    int coreHash,
    int stableCoreHash,
    int doorBlockId,
    DungeonDoorKind doorKind,
    boolean roomFullyLoaded
) {
    public DungeonScanPoint(int gridX, int gridZ, int worldX, int worldZ, DungeonScanPointKind kind,
                            boolean loaded, int coreHash, int stableCoreHash, int doorBlockId, DungeonDoorKind doorKind) {
        this(gridX, gridZ, worldX, worldZ, kind, loaded, coreHash, stableCoreHash, doorBlockId, doorKind, false);
    }
}
