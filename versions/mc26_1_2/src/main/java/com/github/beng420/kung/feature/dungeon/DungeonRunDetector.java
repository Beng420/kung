package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import net.minecraft.client.Minecraft;

public final class DungeonRunDetector {
    public boolean isInDungeonRun(Minecraft client) {
        return isDungeonInstanceCandidate(client);
    }

    public boolean isInDungeonArea(Minecraft client) {
        return isDungeonInstanceCandidate(client);
    }

    public boolean isDungeonInstanceCandidate(Minecraft client) {
        HypixelInstanceTracker tracker = HypixelInstanceTracker.INSTANCE;
        if (tracker.catacombs()) {
            return true;
        }
        if (hasCatacombsSidebar(client)) {
            return true;
        }
        if (hasDungeonMap(client) && isInsideDungeonGrid(client)) {
            return true;
        }
        return tracker.tracking()
            && !tracker.dungeonHub()
            && tracker.dungeonRunContext();
    }

    public boolean isActiveCatacombsInstance(Minecraft client) {
        return HypixelInstanceTracker.INSTANCE.catacombs();
    }

    public boolean hasCatacombsContext(Minecraft client) {
        return HypixelInstanceTracker.INSTANCE.catacombs() || hasCatacombsSidebar(client);
    }

    public boolean hasClientWorld(Minecraft client) {
        return client != null && client.level != null && client.player != null;
    }

    public boolean hasDungeonMap(Minecraft client) {
        return DungeonMapItems.liveMapData(client) != null;
    }

    public boolean isInsideDungeonGrid(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }

        int min = DungeonScanUtils.START_X - DungeonScanUtils.ROOM_SIZE_BLOCKS / 2;
        int max = DungeonScanUtils.START_X
            + DungeonScanUtils.ROOM_SIZE_BLOCKS * (DungeonScanUtils.SCAN_GRID_SIZE / 2)
            + DungeonScanUtils.ROOM_SIZE_BLOCKS / 2;
        int x = client.player.blockPosition().getX();
        int z = client.player.blockPosition().getZ();
        return x >= min && x <= max && z >= min && z <= max;
    }

    public boolean isDungeonHub(Minecraft client) {
        return HypixelInstanceTracker.INSTANCE.dungeonHub()
            && !(hasDungeonMap(client) && isInsideDungeonGrid(client));
    }

    public boolean isKnownNonDungeonInstance() {
        HypixelInstanceTracker tracker = HypixelInstanceTracker.INSTANCE;
        if (!tracker.tracking()) {
            return false;
        }

        String instance = tracker.instanceLine().toLowerCase(java.util.Locale.ROOT);
        if (instance.isBlank() || tracker.catacombs()) {
            return false;
        }

        return instance.contains("dungeon hub")
            || instance.contains("private island")
            || instance.contains("your island")
            || instance.contains("hub")
            || instance.contains("the garden")
            || instance.contains("crimson isle")
            || instance.contains("the end")
            || instance.contains("spider's den")
            || instance.contains("the park")
            || instance.contains("gold mine")
            || instance.contains("deep caverns")
            || instance.contains("dwarven mines")
            || instance.contains("crystal hollows")
            || instance.contains("the farming islands")
            || instance.contains("jerry's workshop");
    }

    private static boolean hasCatacombsSidebar(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return false;
        }
        for (String line : DungeonSidebarReader.lines(client)) {
            String clean = line == null
                ? ""
                : line.replaceAll("\u00a7.", "").toLowerCase(java.util.Locale.ROOT);
            if (clean.contains("catacombs")) {
                return true;
            }
        }
        return false;
    }
}
