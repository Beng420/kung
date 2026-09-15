package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.BeforeClass;
import org.junit.Test;

public class VisitorCropTrackerTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void wildRoseHarvestsAndLastPestCompleteTheHiddenTimer() {
        var state = fullQueue();
        var withoutHarvests = fullQueue();
        var crops = new GardenCropTracker();
        // Observed pest offsets from the supplied 03:13:25.443 -> 03:14:41.068 cycle.
        long[] pestTimes = {1_690, 62_729, 62_857, 65_771, 68_245, 68_591, 69_797, 75_625};
        state.reduce(30_000, pestTimes[0]);
        // Controlled 450-click fixture: the live trace does not record individual harvests.
        for (int i = 0; i < 450; i++) {
            var pos = new BlockPos(i, 70, 0);
            long now = 3_000 + i * 50L;
            if (crops.observe(pos, Blocks.ROSE_BUSH.defaultBlockState())) state.reduce(100, now);
            if (crops.observe(pos, Blocks.ROSE_BUSH.defaultBlockState())) state.reduce(100, now);
        }
        assertEquals("Each harvested rose reduces once despite both mining hooks", 259_000, state.remaining(26_000));
        for (int i = 1; i < pestTimes.length; i++) {
            assertFalse("No early alert before the final kill", state.ringing());
            state.reduce(30_000, pestTimes[i]);
        }
        assertTrue("Pest reduction must finish the timer after Wild Rose farming", state.ringing());
        for (long now : pestTimes) withoutHarvests.reduce(30_000, now);
        assertEquals("The old missing-harvest path matches the live residual", 44_375, withoutHarvests.remaining(75_625));
        assertFalse(withoutHarvests.ringing());
    }

    @Test
    public void flowerHalvesAndGardenPumpkinsAreHarvestable() {
        for (var block : List.of(Blocks.ROSE_BUSH, Blocks.SUNFLOWER)) {
            for (var half : DoubleBlockHalf.values()) {
                assertTrue(block + " " + half, GardenCropTracker.harvestable(
                    block.defaultBlockState().setValue(DoublePlantBlock.HALF, half)));
            }
        }
        assertTrue(GardenCropTracker.harvestable(Blocks.CARVED_PUMPKIN.defaultBlockState()));
        assertTrue(GardenCropTracker.harvestable(Blocks.PUMPKIN.defaultBlockState()));
    }

    @Test
    public void existingCropsKeepTheirMaturityChecks() {
        for (var block : List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES)) {
            var crop = (CropBlock) block;
            assertFalse(GardenCropTracker.harvestable(crop.getStateForAge(0)));
            assertFalse(GardenCropTracker.harvestable(crop.getStateForAge(crop.getMaxAge() - 1)));
            assertTrue(GardenCropTracker.harvestable(crop.getStateForAge(crop.getMaxAge())));
        }
        for (int age = 0; age <= 3; age++) {
            assertEquals(age == 3, GardenCropTracker.harvestable(Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, age)));
        }
        for (int age = 0; age <= 2; age++) {
            assertEquals(age == 2, GardenCropTracker.harvestable(Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, age)));
        }
        for (var block : List.of(Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.MELON, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM)) {
            assertTrue(block.toString(), GardenCropTracker.harvestable(block.defaultBlockState()));
        }
        for (var block : List.of(Blocks.AIR, Blocks.DIRT, Blocks.STONE, Blocks.OAK_LOG, Blocks.POPPY)) {
            assertFalse(block.toString(), GardenCropTracker.harvestable(block.defaultBlockState()));
        }
    }

    @Test
    public void repeatedMiningAndUnrelatedClicksDoNotCountTheSameCropAgain() {
        var crops = new GardenCropTracker();
        var pos = new BlockPos.MutableBlockPos(1, 70, 1);
        var block = Blocks.ROSE_BUSH.defaultBlockState();
        assertTrue(crops.observe(pos, block));
        assertFalse(crops.observe(pos, block));
        assertFalse(crops.observe(pos.above(), Blocks.AIR.defaultBlockState()));
        assertFalse(crops.observe(pos, block));
        pos.set(2, 70, 1);
        assertTrue("Mutable input positions must be copied", crops.observe(pos, block));
        assertFalse(crops.observe(pos, block));
        assertTrue("Returning after another harvested crop may count again", crops.observe(new BlockPos(1, 70, 1), block));
        crops.reset();
        assertTrue("A new world may reuse the same position", crops.observe(new BlockPos(1, 70, 1), block));
    }

    @Test
    public void flowersCannotShortenAServerVisibleCountdown() {
        var state = new VisitorAlarmState();
        state.observe(new VisitorQueue.Snapshot(Set.of("Stella", "Taylor", "Ludleth", "Maeve"), 360_000L, false), 0);
        var crops = new GardenCropTracker();
        for (int i = 0; i < 100; i++) {
            if (crops.observe(new BlockPos(i, 70, 0), Blocks.ROSE_BUSH.defaultBlockState())) state.reduce(100, 1_000);
        }
        assertEquals(359_000, state.remaining(1_000));
        assertFalse(state.ringing());
    }

    private static VisitorAlarmState fullQueue() {
        var state = new VisitorAlarmState();
        state.observe(new VisitorQueue.Snapshot(Set.of("Stella", "Maeve", "Iron Forger", "Ludleth", "Taylor"), null, true), 0);
        state.seed(360_000, 360_000, 0);
        return state;
    }
}
