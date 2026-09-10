package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import net.minecraft.client.Minecraft;

/** Dungeon consumers use the shared packet-driven instance state, never coordinate or item guesses. */
public final class DungeonRunDetector {
    public boolean isDungeonInstanceCandidate(Minecraft client) {
        return hasClientWorld(client) && HypixelInstanceTracker.INSTANCE.catacombs();
    }

    public boolean isActiveCatacombsInstance(Minecraft client) { return isDungeonInstanceCandidate(client); }
    public boolean isInDungeonRun(Minecraft client) { return isDungeonInstanceCandidate(client); }
    public boolean isInDungeonArea(Minecraft client) { return isDungeonInstanceCandidate(client); }
    public boolean hasCatacombsContext(Minecraft client) { return isDungeonInstanceCandidate(client); }
    public boolean hasClientWorld(Minecraft client) { return client != null && client.level != null && client.player != null; }
    public boolean hasDungeonMap(Minecraft client) { return hasClientWorld(client) && DungeonMapItems.liveMapData(client) != null; }

    public boolean isInsideDungeonGrid(Minecraft client) {
        if (!hasClientWorld(client) || !HypixelInstanceTracker.INSTANCE.positionKnown()) return false;
        int min = DungeonScanUtils.START_X - DungeonScanUtils.ROOM_SIZE_BLOCKS / 2;
        int max = DungeonScanUtils.START_X
            + DungeonScanUtils.ROOM_SIZE_BLOCKS * (DungeonScanUtils.SCAN_GRID_SIZE / 2)
            + DungeonScanUtils.ROOM_SIZE_BLOCKS / 2;
        int x = client.player.blockPosition().getX();
        int z = client.player.blockPosition().getZ();
        return x >= min && x <= max && z >= min && z <= max;
    }
}
