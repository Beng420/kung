package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.skyblock.HypixelServerMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class DungeonRunDetector {
    public boolean isInDungeonRun(Minecraft client) {
        return isConnectedToHypixel(client)
            && !isDungeonHub(client)
            && (hasDungeonMap(client) || hasCatacombsContext(client));
    }

    public boolean isInDungeonArea(Minecraft client) {
        return isConnectedToHypixel(client)
            && isInsideDungeonGrid(client)
            && hasCatacombsContext(client)
            && !isDungeonHub(client);
    }

    public boolean hasCatacombsContext(Minecraft client) {
        return isConnectedToHypixel(client)
            && !isDungeonHub(client)
            && hasCatacombsScoreboard(client);
    }

    public boolean isConnectedToHypixel(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return server != null && HypixelServerMatcher.isHypixelAddress(stripPort(server.ip));
    }

    private boolean hasDungeonMap(Minecraft client) {
        return DungeonMapItems.mapData(client) != null;
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
        for (String line : sidebarLines(client)) {
            if (line.toLowerCase(java.util.Locale.ROOT).contains("dungeon hub")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCatacombsScoreboard(Minecraft client) {
        boolean hasFloorLine = false;
        boolean hasDungeonStatLine = false;
        for (String line : sidebarLines(client)) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("catacombs") || lower.contains("the catacombs")) {
                hasFloorLine = true;
            }
            if (lower.contains("cleared:")
                || lower.contains("secrets found:")
                || lower.contains("score:")
                || lower.contains("crypts:")
                || lower.contains("deaths:")) {
                hasDungeonStatLine = true;
            }
        }
        return hasFloorLine || hasDungeonStatLine;
    }

    private java.util.List<String> sidebarLines(Minecraft client) {
        return DungeonSidebarReader.lines(client);
    }

    private static String stripPort(String address) {
        if (address == null) {
            return "";
        }

        int portStart = address.indexOf(':');
        if (portStart == -1) {
            return address;
        }
        return address.substring(0, portStart);
    }
}
