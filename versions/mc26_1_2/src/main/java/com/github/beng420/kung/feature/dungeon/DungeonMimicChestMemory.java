package com.github.beng420.kung.feature.dungeon;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;

/** Run evidence survives chunk unloads; only room classification or a reset invalidates it. */
final class DungeonMimicChestMemory {
    private final Set<BlockPos> positions = new LinkedHashSet<>();

    void observe(List<BlockPos> visible, Predicate<BlockPos> allowedRoom) {
        for (BlockPos pos : visible) positions.add(pos.immutable());
        positions.removeIf(pos -> !allowedRoom.test(pos));
    }

    List<BlockPos> positions() { return List.copyOf(positions); }

    BlockPos mapChest(Function<BlockPos, String> roomKey) {
        String selectedRoom = null;
        BlockPos selected = null;
        for (BlockPos pos : positions) {
            String key = roomKey.apply(pos);
            if (key == null) return positions.size() == 1 ? pos : null;
            if (selectedRoom == null) {
                selectedRoom = key;
                selected = pos;
            } else if (!selectedRoom.equals(key)) {
                return null;
            }
        }
        return selected;
    }

    void clear() { positions.clear(); }
}
