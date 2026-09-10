package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import java.util.List;
import net.minecraft.client.Minecraft;

final class DungeonSidebarReader {
    private DungeonSidebarReader() {}
    static List<String> lines(Minecraft client) {
        return client == null || client.level == null ? List.of() : HypixelInstanceTracker.INSTANCE.sidebarLines();
    }
}
