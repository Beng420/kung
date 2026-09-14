package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Incremental discovery, retaining unprocessed chunks between ticks. Never loads chunks. */
final class DungeonMimicChestScanner {
    private static final int MIN_CHUNK = Math.floorDiv(DungeonScanUtils.START_X - 15, 16);
    private static final int MAX_CHUNK = Math.floorDiv(DungeonScanUtils.START_X + 5 * 32 + 15, 16);
    private static final int CHUNK_WIDTH = MAX_CHUNK - MIN_CHUNK + 1;
    private final DungeonScanSchedule schedule = new DungeonScanSchedule(CHUNK_WIDTH * CHUNK_WIDTH);
    private final Map<Integer, ChunkChests> chunks = new HashMap<>();
    private ClientLevel level;
    private int fallbackScans;
    private int checkedBlockEntityPositions;

    void clear() {
        chunks.clear();
        schedule.reset();
        level = null;
        fallbackScans = 0;
        checkedBlockEntityPositions = 0;
    }

    void tick(ClientLevel currentLevel, BlockPos playerPos, long tick) {
        if (level != currentLevel) {
            clear();
            level = currentLevel;
        }
        fallbackScans = 0;
        checkedBlockEntityPositions = 0;
        long started = System.nanoTime();
        long deadline = started + 2_000_000L;
        // Nearby block entities are cheap and give immediate discovery when entering a room.
        int playerX = Math.floorDiv(playerPos.getX(), 16);
        int playerZ = Math.floorDiv(playerPos.getZ(), 16);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            inspect(playerX + dx, playerZ + dz, tick, false);
        }
        // At most one full chunk fallback per tick, instead of hundreds every half-second.
        for (int checked = 0; checked < 4; checked++) {
            int index = schedule.cursor();
            schedule.advance(1);
            inspect(MIN_CHUNK + index / CHUNK_WIDTH, MIN_CHUNK + index % CHUNK_WIDTH, tick, fallbackScans == 0);
            if (System.nanoTime() >= deadline) break;
        }
        DungeonTimings.record("mimic-scan", started);
    }

    private void inspect(int x, int z, long tick, boolean allowFallback) {
        if (x < MIN_CHUNK || x > MAX_CHUNK || z < MIN_CHUNK || z > MAX_CHUNK) return;
        int key = (x - MIN_CHUNK) * CHUNK_WIDTH + z - MIN_CHUNK;
        LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty()) {
            chunks.remove(key);
            return;
        }
        ChunkChests previous = chunks.get(key);
        if (previous != null && previous.chunk() != chunk) previous = null;
        Set<BlockPos> found = new LinkedHashSet<>();
        long lastFallback = Long.MIN_VALUE;
        if (previous != null) {
            lastFallback = previous.fallbackTick();
            for (BlockPos pos : previous.positions()) {
                if (chunk.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) found.add(pos);
            }
        }
        // The complete index also contains serialized block entities awaiting initialization.
        // Those chests must not wait for the slower full-block fallback.
        Set<BlockPos> indexedPositions = chunk.getBlockEntitiesPos();
        checkedBlockEntityPositions += indexedPositions.size();
        for (BlockPos pos : indexedPositions) {
            if (chunk.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) found.add(pos.immutable());
        }
        if (allowFallback && DungeonScanSchedule.due(tick, lastFallback, 100)) {
            chunk.findBlocks(state -> state.is(Blocks.TRAPPED_CHEST), (pos, state) -> found.add(pos.immutable()));
            lastFallback = tick;
            fallbackScans++;
        }
        long firstSeenTick = previous == null ? tick : previous.firstSeenTick();
        for (BlockPos pos : found) {
            if (previous != null && previous.positions().contains(pos)) continue;
            String source = !indexedPositions.contains(pos) ? "block-state-fallback"
                : chunk.getBlockEntities().containsKey(pos) ? "block-entity" : "pending-block-entity";
            KungDebugRecorder.event("mimic-discovery", "pos=" + pos.toShortString()
                + " source=" + source + " tick=" + tick + " chunkFirstSeenTick=" + firstSeenTick
                + " previousInspectTick=" + (previous == null ? "none" : previous.inspectionTick())
                + " previousFullScanTick=" + (previous == null || previous.fallbackTick() == Long.MIN_VALUE
                    ? "none" : previous.fallbackTick()));
        }
        chunks.put(key, new ChunkChests(chunk, List.copyOf(found), lastFallback, firstSeenTick, tick));
    }

    List<BlockPos> positions() {
        List<BlockPos> result = new ArrayList<>();
        for (ChunkChests observed : chunks.values()) {
            for (BlockPos pos : observed.positions()) {
                LevelChunk loaded = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
                if (loaded == observed.chunk() && loaded.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) result.add(pos);
            }
        }
        return List.copyOf(result);
    }

    int fallbackScans() { return fallbackScans; }
    int cachedChunks() { return chunks.size(); }
    int checkedBlockEntityPositions() { return checkedBlockEntityPositions; }

    private record ChunkChests(LevelChunk chunk, List<BlockPos> positions, long fallbackTick,
                               long firstSeenTick, long inspectionTick) { }
}
