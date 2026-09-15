package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.FeastConfig;
import java.util.List;
import org.junit.Test;

public class FeastKernelRateTest {
    private static final FeastContext.Event GRAND = new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:513");
    private static final String TED = "[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse.";

    @Test
    public void startsAtHarvestAndWarmsUpForOneMinuteOfFarming() {
        var rate = ready();
        assertFalse(rate.observeMessage(TED, 30_000));
        var idle = rate.snapshot(120_000);
        assertEquals(0, idle.farmingMillis());
        assertTrue(idle.paused());
        assertNull(idle.perHour());
        rate.crop(120_000);
        rate.observeMessage(TED, 120_000);
        for (int second = 1; second < 60; second++) rate.crop(120_000 + second * 1_000L);
        assertNull(rate.snapshot(179_999).perHour());
        rate.crop(180_000);
        assertEquals(Long.valueOf(60), rate.snapshot(180_000).perHour());
        assertFalse(rate.snapshot(180_000).paused());
    }

    @Test
    public void usesObservedTimeFirstThenOnlyTheLastTwentyFarmingMinutes() {
        var rate = ready();
        rate.crop(0);
        for (int second = 1; second <= 2_400; second++) {
            long now = second * 1_000L;
            rate.crop(now);
            if (second <= 1_200 && second % 10 == 0) rate.observeMessage(TED, now);
            if (second == 600) {
                assertEquals(600_000, rate.snapshot(now).farmingMillis());
                assertEquals(Long.valueOf(360), rate.snapshot(now).perHour());
            } else if (second == 1_200) {
                assertEquals(1_200_000, rate.snapshot(now).farmingMillis());
                assertEquals(Long.valueOf(360), rate.snapshot(now).perHour());
            } else if (second == 1_800) {
                assertEquals("The first ten minutes have left the window", 60, rate.snapshot(now).kernels());
                assertEquals(Long.valueOf(180), rate.snapshot(now).perHour());
            }
        }
        assertEquals(Long.valueOf(0), rate.snapshot(2_400_000).perHour());
    }

    @Test
    public void minuteLongBreakFreezesAfterFiveSecondsAndResumesWithoutTheGap() {
        var rate = ready();
        farmMinute(rate);
        assertFalse(rate.snapshot(64_999).paused());
        var paused = rate.snapshot(65_000);
        assertTrue(paused.paused());
        assertEquals(65_000, paused.farmingMillis());
        assertEquals(paused, rate.snapshot(125_000));
        rate.crop(125_000);
        assertEquals(paused.perHour(), rate.snapshot(125_000).perHour());
        assertFalse(rate.snapshot(125_000).paused());
        rate.crop(129_000);
        assertEquals(69_000, rate.snapshot(129_000).farmingMillis());
    }

    @Test
    public void delayedTickCannotCountTheWholeIdleGap() {
        var rate = ready();
        rate.crop(1_000);
        assertTrue(rate.observeMessage(TED, 2_000));
        assertEquals(5_000, rate.snapshot(3_600_000).farmingMillis());
        assertFalse(rate.observeMessage(TED, 3_600_000));
        assertEquals(1, rate.snapshot(3_600_000).kernels());
    }

    @Test
    public void hubAndWorldTransfersPauseButKeepTheRollingHistory() {
        var rate = ready();
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
    }

    @Test
    public void missingEventEvidencePausesButDifferentFeastsAndResetClearTheRate() {
        var rate = ready();
        farmMinute(rate);
        rate.updateContext(true, null, 60_000);
        rate.crop(90_000);
        assertFalse(rate.observeMessage(TED, 90_000));
        assertEquals(60_000, rate.snapshot(90_000).farmingMillis());
        rate.updateContext(true, GRAND, 90_000);
        assertEquals(6, rate.snapshot(90_000).kernels());
        rate.updateContext(true, new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:514"), 90_000);
        assertEquals(0, rate.snapshot(90_000).kernels());
        rate.crop(90_000);
        rate.observeMessage(TED, 90_000);
        rate.updateContext(true, new FeastContext.Event(FeastProgress.Kind.HARVEST, "harvest:514"), 90_000);
        rate.crop(90_000);
        assertFalse(rate.observeMessage(TED, 90_000));
        assertEquals(0, rate.snapshot(90_000).kernels());
        rate.reset();
        rate.updateContext(true, GRAND, 90_000);
        rate.crop(90_000);
        assertFalse("Wait for profile identity after disconnect/disable", rate.observeMessage(TED, 90_000));
    }

    @Test
    public void manyGainsUseBoundedSecondBucketsAndExpireAtTheWindowBoundary() {
        var rate = ready();
        rate.crop(0);
        rate.observeMessage(TED, 0);
        rate.observeMessage(TED, 0);
        assertEquals(1, rate.sampleCount());
        for (int second = 1; second <= 1_300; second++) {
            long now = second * 1_000L;
            rate.crop(now);
            for (int gain = 0; gain < 3; gain++) rate.observeMessage(TED, now);
            assertTrue(rate.sampleCount() <= 1_201);
        }
        assertEquals(3_600, rate.snapshot(1_300_000).kernels());
        assertEquals(Long.valueOf(10_800), rate.snapshot(1_300_000).perHour());
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
            new FeastKernelRate.Snapshot(1, 59_999, false)));
        assertEquals("Avg Kernels/h: 1,234", FeastOverlayFeature.rateLine(FeastProgress.Kind.GRAND,
            new FeastKernelRate.Snapshot(617, 1_800_000, false)));
        var paused = new FeastKernelRate.Snapshot(40, 1_200_000, true);
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
