package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.Test;

public final class DungeonMimicPersistenceTest {
    @Test public void disappearingAndReloadingChunkDoesNotEraseOrDuplicateChestEvidence() {
        var memory = new DungeonMimicChestMemory();
        var chest = new BlockPos(-122, 78, -22);
        memory.observe(List.of(chest), pos -> true);
        for (int scan = 0; scan < 80; scan++) memory.observe(List.of(), pos -> true);
        assertEquals(List.of(chest), memory.positions());
        assertEquals(chest, memory.mapChest(pos -> "mimic-room"));
        memory.observe(List.of(chest), pos -> true);
        assertEquals(List.of(chest), memory.positions());
    }

    @Test public void lateRoomIdentificationCanRejectAStaticChestWithoutLosingTheMimic() {
        var memory = new DungeonMimicChestMemory();
        var mimic = new BlockPos(-122, 78, -22);
        var staticChest = new BlockPos(-185, 72, -185);
        memory.observe(List.of(mimic, staticChest), pos -> true);
        assertNull(memory.mapChest(pos -> pos.equals(mimic) ? "mimic-room" : "static-room"));
        memory.observe(List.of(), pos -> !pos.equals(staticChest));
        assertEquals(List.of(mimic), memory.positions());
        assertEquals(mimic, memory.mapChest(pos -> "mimic-room"));
        memory.clear();
        assertTrue(memory.positions().isEmpty());
        assertNull(memory.mapChest(pos -> "old-room"));
    }

    @Test public void remembersImmutablePositionAndIndependentSnapshot() {
        var memory = new DungeonMimicChestMemory();
        var mutable = new BlockPos.MutableBlockPos(-122, 78, -22);
        memory.observe(List.of(mutable), pos -> true);
        var saved = memory.positions();
        mutable.set(0, 0, 0);
        memory.clear();
        assertEquals(List.of(new BlockPos(-122, 78, -22)), saved);
    }

    @Test public void snapshotGenerationChangesOnlyWhenTheInstanceMapIsReset() {
        var snapshot = new DungeonMapSnapshot();
        long first = snapshot.resetGeneration();
        snapshot.observeMimicRoom(2, 5, "test");
        snapshot.observePlayerGrid(1, 0);
        assertEquals(first, snapshot.resetGeneration());
        snapshot.reset();
        assertEquals(first + 1, snapshot.resetGeneration());
        assertTrue(snapshot.mimicRooms().isEmpty());
        snapshot.reset();
        assertEquals(first + 2, snapshot.resetGeneration());
    }
}
