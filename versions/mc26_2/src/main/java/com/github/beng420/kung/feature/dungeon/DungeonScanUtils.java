package com.github.beng420.kung.feature.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;

public final class DungeonScanUtils {
    public static final int ROOM_SIZE_BLOCKS = 32;
    public static final int SCAN_GRID_SIZE = 11;
    public static final int SCAN_STEP_BLOCKS = ROOM_SIZE_BLOCKS / 2;
    public static final int START_X = -185;
    public static final int START_Z = -185;

    private static final int MIN_SCAN_Y = 12;
    private static final int MIN_CORE_HEIGHT = 11;
    private static final int MAX_CORE_HEIGHT = 140;
    private static final int DOOR_FLOOR_Y = 68;
    private static final int MIN_DOOR_SCAN_Y = 69;
    private static final int MAX_DOOR_SCAN_Y = 72;
    private static final int DOOR_CEILING_Y = 73;
    private static final int DOOR_SKY_Y = 74;
    private static final int AIR_ID = 0;
    private static final int BEDROCK_ID = 7;
    private static final int PLANKS_ID = 5;
    private static final int CHEST_ID = 54;
    private static final int TRAPPED_CHEST_ID = 146;

    private DungeonScanUtils() {
    }

    public static int worldXForScanGrid(int gridX) {
        return START_X + gridX * SCAN_STEP_BLOCKS;
    }

    public static int worldZForScanGrid(int gridZ) {
        return START_Z + gridZ * SCAN_STEP_BLOCKS;
    }

    public static boolean isRoomScanPoint(int gridX, int gridZ) {
        return isEven(gridX) && isEven(gridZ);
    }

    public static boolean isDoorScanPoint(int gridX, int gridZ) {
        return isEven(gridX) != isEven(gridZ);
    }

    public static boolean isSeparatorScanPoint(int gridX, int gridZ) {
        return !isEven(gridX) && !isEven(gridZ);
    }

    public static boolean isChunkLoaded(ClientLevel level, int worldX, int worldZ) {
        return level.hasChunk(worldX >> 4, worldZ >> 4);
    }

    public static RoomCenter getRoomCenter(int worldX, int worldZ) {
        int roomX = Math.round((worldX - START_X) / (float) ROOM_SIZE_BLOCKS);
        int roomZ = Math.round((worldZ - START_Z) / (float) ROOM_SIZE_BLOCKS);
        return new RoomCenter(
            roomX * ROOM_SIZE_BLOCKS + START_X,
            roomZ * ROOM_SIZE_BLOCKS + START_Z
        );
    }

    public static GridPosition getRoomGridPosition(BlockPos pos) {
        int gridX = (pos.getX() - START_X + 15) >> 5;
        int gridZ = (pos.getZ() - START_Z + 15) >> 5;
        return new GridPosition(gridX, gridZ);
    }

    public static int getCoreHash(ClientLevel level, int worldX, int worldZ) {
        int height = getClampedHeight(level, worldX, worldZ);
        StringBuilder signature = new StringBuilder(150);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(worldX, height, worldZ);

        appendZeros(signature, MAX_CORE_HEIGHT - height);

        int consecutiveBedrock = 0;
        for (int y = height; y >= MIN_SCAN_Y; y--) {
            int blockId = getBlockId(level, pos.set(worldX, y, worldZ));

            if (blockId == AIR_ID && consecutiveBedrock >= 2 && y < 69) {
                appendZeros(signature, y - MIN_CORE_HEIGHT);
                break;
            }

            if (blockId == BEDROCK_ID) {
                consecutiveBedrock++;
            } else {
                consecutiveBedrock = 0;
                if (shouldIgnoreInCore(blockId)) {
                    continue;
                }
            }

            signature.append(blockId);
        }

        return signature.toString().hashCode();
    }

    public static int getStableCoreHash(ClientLevel level, int worldX, int worldZ) {
        int height = getClampedHeight(level, worldX, worldZ);
        StringBuilder signature = new StringBuilder(700);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(worldX, height, worldZ);

        appendZeros(signature, MAX_CORE_HEIGHT - height);

        int consecutiveBedrock = 0;
        for (int y = height; y >= MIN_SCAN_Y; y--) {
            BlockState state = level.getBlockState(pos.set(worldX, y, worldZ));
            String blockName = blockName(state);

            if (state.isAir() && consecutiveBedrock >= 2 && y < 69) {
                appendZeros(signature, y - MIN_CORE_HEIGHT);
                break;
            }

            if ("bedrock".equals(blockName)) {
                consecutiveBedrock++;
            } else {
                consecutiveBedrock = 0;
                if (shouldIgnoreInStableCore(blockName)) {
                    continue;
                }
            }

            signature.append(stableStateName(state)).append(';');
        }

        return signature.toString().hashCode();
    }

    private static int getClampedHeight(ClientLevel level, int worldX, int worldZ) {
        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        return Math.clamp(height, MIN_CORE_HEIGHT, MAX_CORE_HEIGHT);
    }

    public static int getBlockId(ClientLevel level, int worldX, int y, int worldZ) {
        return Block.getId(level.getBlockState(new BlockPos(worldX, y, worldZ)));
    }

    private static int getBlockId(ClientLevel level, BlockPos pos) {
        return Block.getId(level.getBlockState(pos));
    }

    public static DungeonDoorKind detectDoorKind(ClientLevel level, int gridX, int gridZ, int worldX, int worldZ) {
        if (!isDoorScanPoint(gridX, gridZ)) {
            return DungeonDoorKind.NONE;
        }

        if (!hasDoorFrame(level, worldX, worldZ)) {
            return DungeonDoorKind.NONE;
        }

        int clearBlocks = 0;
        int witherBlocks = 0;
        int bloodBlocks = 0;
        for (int y = MIN_DOOR_SCAN_Y; y <= MAX_DOOR_SCAN_Y; y++) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            BlockState state = level.getBlockState(pos);
            if (isClear(level, pos)) {
                clearBlocks++;
                continue;
            }

            String blockName = blockName(state);
            if (isWitherDoorBlock(blockName)) {
                witherBlocks++;
                continue;
            }
            if (isBloodDoorBlock(blockName)) {
                bloodBlocks++;
                continue;
            }

            return DungeonDoorKind.NONE;
        }

        if (bloodBlocks >= 3) {
            return DungeonDoorKind.BLOOD;
        }
        if (witherBlocks >= 3) {
            return DungeonDoorKind.WITHER;
        }
        if (clearBlocks == 4) {
            return DungeonDoorKind.OPEN;
        }

        return DungeonDoorKind.NONE;
    }

    private static boolean hasDoorFrame(ClientLevel level, int worldX, int worldZ) {
        // Dungeon doors have a solid floor, a four-block vertical opening, a ceiling block,
        // then open space above the center column. This keeps the detector strict enough to
        // avoid drawing doors just because two room cells are adjacent.
        return !isClear(level, new BlockPos(worldX, DOOR_FLOOR_Y, worldZ))
            && !isClear(level, new BlockPos(worldX, DOOR_CEILING_Y, worldZ))
            && isClear(level, new BlockPos(worldX, DOOR_SKY_Y, worldZ));
    }

    private static boolean isClear(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static String blockName(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static boolean isWitherDoorBlock(String blockName) {
        return blockName.contains("coal");
    }

    private static boolean isBloodDoorBlock(String blockName) {
        return blockName.contains("red")
            || blockName.contains("crimson")
            || blockName.contains("nether_wart");
    }

    private static boolean shouldIgnoreInCore(int blockId) {
        return blockId == PLANKS_ID || blockId == CHEST_ID || blockId == TRAPPED_CHEST_ID;
    }

    private static boolean shouldIgnoreInStableCore(String blockName) {
        return blockName.endsWith("_planks")
            || "chest".equals(blockName)
            || "trapped_chest".equals(blockName);
    }

    private static String stableStateName(BlockState state) {
        StringBuilder stableName = new StringBuilder(blockName(state));
        List<String> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            properties.add(stablePropertyName(state, property));
        }
        if (!properties.isEmpty()) {
            Collections.sort(properties);
            stableName.append('[');
            for (int index = 0; index < properties.size(); index++) {
                if (index > 0) {
                    stableName.append(',');
                }
                stableName.append(properties.get(index));
            }
            stableName.append(']');
        }
        return stableName.toString();
    }

    private static <T extends Comparable<T>> String stablePropertyName(BlockState state, Property<T> property) {
        return property.getName() + '=' + property.getName(state.getValue(property));
    }

    private static boolean isEven(int value) {
        return (value & 1) == 0;
    }

    private static void appendZeros(StringBuilder signature, int amount) {
        for (int i = 0; i < amount; i++) {
            signature.append('0');
        }
    }

    public record RoomCenter(int worldX, int worldZ) {
    }

    public record GridPosition(int gridX, int gridZ) {
    }
}
