package com.github.beng420.kung.feature.garden;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Client-thread crop input; initial and continued mining share one position guard. */
final class GardenCropTracker {
    private BlockPos lastCrop;

    boolean observe(BlockPos pos, BlockState block) {
        if (pos.equals(lastCrop) || !harvestable(block)) return false;
        lastCrop = pos.immutable();
        return true;
    }

    void reset() { lastCrop = null; }

    static boolean harvestable(BlockState block) {
        if (block.getBlock() instanceof CropBlock crop) return crop.isMaxAge(block);
        if (block.is(Blocks.NETHER_WART)) return block.getValue(NetherWartBlock.AGE) == 3;
        if (block.is(Blocks.COCOA)) return block.getValue(CocoaBlock.AGE) == 2;
        // Hypixel uses these vanilla blocks for Wild Roses, Sunflower/Moonflower and pumpkins.
        return block.is(Blocks.SUGAR_CANE) || block.is(Blocks.CACTUS) || block.is(Blocks.MELON)
            || block.is(Blocks.PUMPKIN) || block.is(Blocks.CARVED_PUMPKIN)
            || block.is(Blocks.RED_MUSHROOM) || block.is(Blocks.BROWN_MUSHROOM)
            || block.is(Blocks.ROSE_BUSH) || block.is(Blocks.SUNFLOWER);
    }
}
