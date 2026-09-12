package com.github.beng420.kung.feature.dungeon;

import net.minecraft.core.BlockPos;

/** Block coordinates relative to the northwest building corner; Y is absolute. */
record DungeonRoomTransform(int originX, int originZ, int width, int depth, int rotation) {
    DungeonRoomTransform {
        if (width < 1 || depth < 1 || rotation < 0 || rotation > 3) throw new IllegalArgumentException("Invalid room transform");
    }

    BlockPos world(BlockPos local) {
        int x = local.getX(), z = local.getZ();
        return switch (rotation) {
            case 1 -> new BlockPos(originX + depth - 1 - z, local.getY(), originZ + x);
            case 2 -> new BlockPos(originX + width - 1 - x, local.getY(), originZ + depth - 1 - z);
            case 3 -> new BlockPos(originX + z, local.getY(), originZ + width - 1 - x);
            default -> new BlockPos(originX + x, local.getY(), originZ + z);
        };
    }

    BlockPos local(BlockPos world) {
        int x = world.getX() - originX, z = world.getZ() - originZ;
        return switch (rotation) {
            case 1 -> new BlockPos(z, world.getY(), depth - 1 - x);
            case 2 -> new BlockPos(width - 1 - x, world.getY(), depth - 1 - z);
            case 3 -> new BlockPos(width - 1 - z, world.getY(), x);
            default -> new BlockPos(x, world.getY(), z);
        };
    }
}
