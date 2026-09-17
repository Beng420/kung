package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.FeastConfig;
import java.util.List;
import org.junit.Test;

public class FeastKernelRateTest {
    private static final FeastContext.Event GRAND = new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:513");
    private static final String TED = "[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse.";

    @Test
    public void firstEstimateWaitsForFiveFarmingMinutesAndDoesNotJumpOnDrops() {
        var rate = ready();
        assertFalse(rate.observeMessage(TED, 30_000));
        var idle = rate.snapshot(120_000);
        assertEquals(0, idle.farmingMillis());
        assertTrue(idle.paused());
        assertNull(idle.perHour());
        rate.crop(120_000);
        rate.observeMessage(TED, 120_000);
        for (int second = 1; second < 300; second++) rate.crop(120_000 + second * 1_000L);
        assertNull(rate.snapshot(419_999).perHour());
        rate.crop(420_000);
        assertEquals(Long.valueOf(12), rate.snapshot(420_000).perHour());
        assertTrue(rate.observeMessage(TED, 420_000));
        assertEquals(Long.valueOf(12), rate.snapshot(420_000).perHour());
        assertFalse(rate.snapshot(420_000).paused());
    }

    @Test
    public void savedRateHoldsBetweenBlocksAndGentlyAdaptsToFasterAndSlowerFarming() {
        var rate = ready();
        rate.restore(60.0);
        rate.crop(0);
        for (int second = 1; second < 300; second++) {
            long now = second * 1_000L;
            rate.crop(now);
            if (second % 30 == 0 || second == 299) rate.observeMessage(TED, now);
            assertEquals("Individual gains and empty seconds do not move the saved estimate",
                Long.valueOf(60), rate.snapshot(now).perHour());
        }
        rate.crop(300_000);
        assertEquals("Ten gains in five minutes measure 120/h, blended into the prior 60/h",
            Double.valueOf(72), rate.value());
        for (int second = 301; second <= 600; second++) rate.crop(second * 1_000L);
        assertEquals("A full empty farming block still counts, but lowers the estimate gradually",
            57.6, rate.value(), 1e-10);
    }

    @Test
    public void delayedTickSplitsAnActiveIntervalAcrossBlockBoundaries() {
        var rate = ready();
        rate.restore(60.0);
        rate.setTimeoutSeconds(300, 0);
        rate.crop(0);
        rate.observeMessage(TED, 1_000);
        rate.crop(250_000);
        var delayed = rate.snapshot(3_600_000);
        assertEquals(Long.valueOf(50), delayed.perHour()); // 80% * 60 + 20% * 12 = 50.4
        assertEquals(250_000, delayed.farmingMillis());
        assertEquals(0, delayed.kernels());
        assertEquals(delayed, rate.snapshot(7_200_000));
    }

    @Test
    public void renderAndTickCadenceDoNotChangeTheMeasuredRate() {
        var frequent = ready();
        var sparse = ready();
        frequent.crop(0);
        sparse.crop(0);
        for (long now = 10; now <= 600_000; now += 10) {
            if (now % 17_000 == 0) {
                frequent.crop(now);
                sparse.crop(now);
            }
            if (now % 13_000 == 0) {
                frequent.observeMessage(TED, now);
                sparse.observeMessage(TED, now);
            }
            frequent.snapshot(now);
        }
        var a = frequent.snapshot(600_000);
        var b = sparse.snapshot(600_000);
        assertEquals(Long.valueOf(276), a.perHour());
        assertEquals(a, b);
    }

    @Test
    public void farmingWithoutAnyGainsShowsZeroAfterWarmup() {
        var rate = ready();
        rate.crop(0);
        for (int second = 1; second < 300; second++) rate.crop(second * 1_000L);
        assertNull(rate.snapshot(299_999).perHour());
        rate.crop(300_000);
        assertEquals(Long.valueOf(0), rate.snapshot(300_000).perHour());
    }

    @Test
    public void defaultTimeoutKeepsTwentySecondsOfPestFarmingAndPausesAtOneMinute() {
        var rate = ready();
        rate.restore(60.0);
        farmMinute(rate);
        assertFalse("Twenty seconds hunting pests is still part of farming", rate.snapshot(80_000).paused());
        assertEquals(80_000, rate.snapshot(80_000).farmingMillis());
        assertFalse(rate.snapshot(119_999).paused());
        var paused = rate.snapshot(120_000);
        assertTrue(paused.paused());
        assertEquals(120_000, paused.farmingMillis());
        assertEquals(paused, rate.snapshot(180_000));
        rate.crop(180_000);
        assertEquals(paused.perHour(), rate.snapshot(180_000).perHour());
        assertFalse(rate.snapshot(180_000).paused());
        rate.crop(184_000);
        assertEquals(124_000, rate.snapshot(184_000).farmingMillis());
    }

    @Test
    public void delayedTickCannotCountTheWholeIdleGap() {
        var rate = ready();
        rate.crop(1_000);
        assertTrue(rate.observeMessage(TED, 2_000));
        assertEquals(60_000, rate.snapshot(3_600_000).farmingMillis());
        assertFalse(rate.observeMessage(TED, 3_600_000));
        assertEquals(1, rate.snapshot(3_600_000).kernels());
    }

    @Test
    public void customTimeoutsPauseAtTheConfiguredBoundaryAndClampTheRange() {
        for (int seconds : new int[] {0, 10, 75, 300, 900}) {
            var rate = ready();
            rate.setTimeoutSeconds(seconds, 0);
            rate.crop(0);
            long timeout = Math.clamp(seconds, 10, 300) * 1_000L;
            assertFalse(rate.snapshot(timeout - 1).paused());
            var paused = rate.snapshot(timeout);
            assertTrue(paused.paused());
            assertEquals(timeout % FeastKernelRate.BLOCK_MILLIS, paused.farmingMillis());
            assertEquals(paused, rate.snapshot(timeout + 60_000));
        }
    }

    @Test
    public void changingTimeoutUpdatesTheCurrentDeadlineWithoutRewritingTheRateHistory() {
        var rate = ready();
        farmMinute(rate);
        rate.setTimeoutSeconds(120, 90_000);
        assertFalse(rate.snapshot(140_000).paused());
        assertEquals(140_000, rate.snapshot(140_000).farmingMillis());
        rate.setTimeoutSeconds(10, 140_000);
        assertTrue(rate.snapshot(140_000).paused());
        assertEquals(6, rate.snapshot(140_000).kernels());
        rate.setTimeoutSeconds(300, 160_000);
        assertFalse(rate.snapshot(160_000).paused());
        assertEquals("Already paused time is not backfilled", 140_000, rate.snapshot(160_000).farmingMillis());
        assertEquals(6, rate.snapshot(160_000).kernels());
        rate.pause(160_000);
        rate.setTimeoutSeconds(120, 170_000);
        rate.updateContext(true, GRAND, 170_000);
        assertTrue("Editing after a world pause must not resume without a new crop", rate.snapshot(170_000).paused());
    }

    @Test
    public void hubAndWorldTransfersKeepTheEstimateAndUnfinishedBlock() {
        var rate = ready();
        rate.restore(72.5);
        farmMinute(rate);
        rate.pause(61_000);
        var paused = rate.snapshot(61_000);
        rate.updateContext(false, GRAND, 62_000);
        rate.crop(90_000);
        assertFalse(rate.observeMessage(TED, 90_000));
        assertEquals(paused, rate.snapshot(120_000));
        rate.selectProfile("account", "COCONUT");
        rate.updateContext(true, GRAND, 120_000);
        assertEquals(paused, rate.snapshot(120_000));
        rate.crop(120_000);
        rate.crop(122_000);
        assertEquals(63_000, rate.snapshot(122_000).farmingMillis());
        assertEquals(6, rate.snapshot(122_000).kernels());
    }

    @Test
    public void onlyConfirmedDonationMessagesCountIndependentlyOfTheBalanceAndTierCap() {
        var rate = ready();
        var kernels = new FeastKernels();
        var session = new FeastSession();
        session.select(GRAND, true);
        session.observeMenu(new Object(), new FeastProgress.Snapshot(FeastProgress.Kind.GRAND, 750,
            List.of(5, 25, 75, 150, 250, 350, 450, 550, 750)));
        rate.crop(0);
        for (String message : List.of("RARE CROP! Seasoning (+80\uE02B) (automatically donated)",
                "- Kernels x125", "Your Kernels: 579", "Kernels: 579 (+125)", "Guild > Friend: " + TED)) {
            assertFalse(message, rate.observeMessage(message, 1_000));
        }
        assertFalse("A missing balance does not prevent rate measurement", kernels.observeMessage(TED));
        assertTrue(rate.observeMessage(TED, 1_000));
        assertTrue("Separate identical events are separate gains", rate.observeMessage("[03:15:03] §e" + TED, 1_000));
        assertEquals(2, rate.snapshot(1_000).kernels());
        assertNull(kernels.balance());
        assertEquals(750, session.snapshot().donations());
    }

    @Test
    public void profilesAccountsAndRecreatedProfilesStartFresh() {
        var rate = ready();
        farmMinute(rate);
        rate.identifyProfile("id-one");
        rate.identifyProfile("id-one");
        assertEquals(6, rate.snapshot(60_000).kernels());
        rate.identifyProfile("id-two");
        assertEquals(0, rate.snapshot(60_000).kernels());
        rate.updateContext(true, GRAND, 60_000);
        rate.crop(60_000);
        rate.observeMessage(TED, 60_000);
        rate.selectProfile("account", "Apple");
        assertEquals(0, rate.snapshot(60_000).kernels());
        rate.updateContext(true, GRAND, 60_000);
        rate.crop(60_000);
        rate.observeMessage(TED, 60_000);
        rate.selectProfile("other-account", "Apple");
        assertEquals(0, rate.snapshot(60_000).farmingMillis());
        assertEquals(0, rate.snapshot(60_000).kernels());
        assertNull(rate.value());
    }

    @Test
    public void changedFeastsClearTheUnfinishedBlockButRetainTheEstimate() {
        var rate = ready();
        rate.restore(60.0);
        farmMinute(rate);
        rate.updateContext(true, null, 60_000);
        rate.crop(90_000);
        assertFalse(rate.observeMessage(TED, 90_000));
        assertEquals(60_000, rate.snapshot(90_000).farmingMillis());
        rate.updateContext(true, GRAND, 90_000);
        assertEquals(6, rate.snapshot(90_000).kernels());
        rate.updateContext(true, new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:514"), 90_000);
        assertEquals(0, rate.snapshot(90_000).kernels());
        assertEquals(Long.valueOf(60), rate.snapshot(90_000).perHour());
        rate.crop(90_000);
        rate.observeMessage(TED, 90_000);
        rate.updateContext(true, new FeastContext.Event(FeastProgress.Kind.HARVEST, "harvest:514"), 90_000);
        rate.crop(90_000);
        assertFalse(rate.observeMessage(TED, 90_000));
        assertEquals(0, rate.snapshot(90_000).kernels());
        assertEquals(Long.valueOf(60), rate.snapshot(90_000).perHour());
        rate.reset();
        assertNull(rate.value());
        rate.updateContext(true, GRAND, 90_000);
        rate.crop(90_000);
        assertFalse("Wait for profile identity after disconnect/disable", rate.observeMessage(TED, 90_000));
    }

    @Test
    public void aLongSteadyStreamStaysFiniteAndCloseToItsKnownRate() {
        var rate = ready();
        rate.crop(0);
        for (int second = 1; second <= 21_600; second++) {
            long now = second * 1_000L;
            rate.crop(now);
            for (int gain = 0; gain < 3; gain++) rate.observeMessage(TED, now);
        }
        var steady = rate.snapshot(21_600_000);
        assertEquals("Boundary gains belong to the next block", 3, steady.kernels());
        assertEquals(0, steady.farmingMillis());
        assertTrue(Double.isFinite(steady.average()));
        assertEquals(Long.valueOf(10_800), steady.perHour());
    }

    @Test
    public void backwardsClockDoesNotAddTimeTwice() {
        var rate = ready();
        rate.crop(10_000);
        assertEquals(1_000, rate.snapshot(11_000).farmingMillis());
        rate.snapshot(10_500);
        assertEquals(2_000, rate.snapshot(12_000).farmingMillis());
    }

    @Test
    public void grandRateLineHasWarmupAndPauseStatesAndFitsTheExpandedHudBounds() {
        assertEquals("Avg Kernels/h: -- (warming up)", FeastOverlayFeature.rateLine(FeastProgress.Kind.GRAND,
            new FeastKernelRate.Snapshot(1, 59_999, null, false)));
        assertEquals("Avg Kernels/h: 1,234", FeastOverlayFeature.rateLine(FeastProgress.Kind.GRAND,
            new FeastKernelRate.Snapshot(0, 0, 1234.0, false)));
        var paused = new FeastKernelRate.Snapshot(0, 0, 120.0, true);
        assertEquals("Avg Kernels/h: 120 (paused)", FeastOverlayFeature.rateLine(FeastProgress.Kind.GRAND, paused));
        assertEquals("", FeastOverlayFeature.rateLine(FeastProgress.Kind.HARVEST, paused));
        var config = new FeastConfig();
        var bounds = FeastOverlayFeature.overlayBounds(config);
        assertEquals(config.x(), bounds.x());
        assertEquals(config.y(), bounds.y());
        assertEquals(180, bounds.width());
        assertEquals(52, bounds.height());
    }

    private static FeastKernelRate ready() {
        var rate = new FeastKernelRate();
        rate.selectProfile("account", "Coconut");
        rate.updateContext(true, GRAND, 0);
        return rate;
    }

    private static void farmMinute(FeastKernelRate rate) {
        rate.crop(0);
        for (int second = 1; second <= 60; second++) {
            long now = second * 1_000L;
            rate.crop(now);
            if (second % 10 == 0) rate.observeMessage(TED, now);
        }
    }
}
