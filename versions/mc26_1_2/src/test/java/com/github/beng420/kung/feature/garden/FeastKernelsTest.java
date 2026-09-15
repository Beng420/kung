package com.github.beng420.kung.feature.garden;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeastKernelsTest {
    private static final String TED = "[17:36:58] §e[NPC] Feast Chef Ted§f: Thanks for the donation! "
        + "I've added a §eKernel §fto your purse.";

    @Test
    public void readsTheOwnedBalanceInsteadOfMilestoneRewardsOrShopPrices() {
        var items = new ArrayList<>(FeastProgressTest.menu(27, List.of(5, 25, 75, 150, 250, 350, 450, 550, 750)));
        items.add(bakery("§7Your Kernels: §e1,234"));
        items.add(new FeastProgress.MenuItem("Feast I", List.of("Cost:", "25 Kernels")));
        assertEquals(Long.valueOf(1_234), FeastKernels.readMenu("§6Grand Feast", items));
        assertEquals(Long.valueOf(1_234), FeastKernels.readMenu("Grand Bakery", items));
        assertNull(FeastKernels.readMenu("Harvest Feast", items));
        assertNull(FeastKernels.readMenu("Auction House", items));
    }

    @Test
    public void recognizesZeroAndFormattedBalancesButRejectsMissingBrokenOrConflictingAmounts() {
        assertEquals(Long.valueOf(0), FeastKernels.readMenu("Grand Feast", List.of(bakery("Your Kernels: 0"))));
        assertEquals(Long.valueOf(12_345), FeastKernels.readMenu("Grand Feast",
            List.of(bakery("Your\u00a0Kernels:\u00a012,345"))));
        assertNull(FeastKernels.readMenu("Grand Feast", List.of()));
        for (String amount : List.of("-1", "1,23", "1.234", "999999999999999999999", "unknown")) {
            assertNull(amount, FeastKernels.readMenu("Grand Feast", List.of(bakery("Your Kernels: " + amount))));
        }
        assertNull(FeastKernels.readMenu("Grand Feast", List.of(bakery("Your Kernels: 100"), bakery("Your Kernels: 101"))));
    }

    @Test
    public void countsOnlyConfirmedKernelGainsAfterAnAbsoluteBaseline() {
        var kernels = new FeastKernels();
        assertFalse(kernels.observeMessage(TED));
        assertNull(kernels.balance());
        kernels.observeMenu(new Object(), 1_234L);
        for (String message : List.of("RARE CROP! Seasoning (+80\uE02B) (automatically donated)",
            "[NPC] Feast Chef Ted: You've hit a new Feast Donation Tier!",
            "Guild > Friend: " + TED,
            "[NPC] Other: Thanks for the donation! I've added a Kernel to your purse.")) {
            assertFalse(message, kernels.observeMessage(message));
        }
        assertTrue(kernels.observeMessage(TED));
        assertTrue(kernels.observeMessage(TED));
        assertEquals(Long.valueOf(1_236), kernels.balance());
    }

    @Test
    public void repeatedMenuSnapshotsDoNotUndoGainsAndFreshBalancesCorrectRewardsOrSpending() {
        var kernels = new FeastKernels();
        Object menu = new Object();
        kernels.observeMenu(menu, 1_234L);
        kernels.observeMessage(TED);
        assertFalse(kernels.observeMenu(menu, 1_234L));
        assertEquals(Long.valueOf(1_235), kernels.balance());
        assertFalse(kernels.observeMenu(menu, 1_235L));
        assertEquals(Long.valueOf(1_235), kernels.balance());
        assertTrue(kernels.observeMenu(menu, 1_460L));
        assertEquals(Long.valueOf(1_460), kernels.balance());
        assertTrue(kernels.observeMenu(menu, 960L));
        assertEquals(Long.valueOf(960), kernels.balance());
        kernels.closeMenu();
        assertTrue(kernels.observeMenu(new Object(), 900L));
        assertEquals(Long.valueOf(900), kernels.balance());
    }

    @Test
    public void closingMenusRetainsCurrencyButProfileInvalidationAndResetRequireANewBaseline() {
        var kernels = new FeastKernels();
        Object menu = new Object();
        kernels.observeMenu(menu, 1_234L);
        kernels.closeMenu();
        assertEquals(Long.valueOf(1_234), kernels.balance());
        kernels.observeMenu(menu, 1_234L);
        kernels.invalidate();
        assertNull(kernels.balance());
        assertFalse(kernels.observeMessage(TED));
        assertFalse(kernels.observeMenu(menu, 1_234L));
        assertNull(kernels.balance());
        kernels.closeMenu();
        assertTrue(kernels.observeMenu(new Object(), 25L));
        kernels.reset();
        assertNull(kernels.balance());
    }

    @Test
    public void learnsTheServerSidebarBalanceWithoutOpeningAnyMenu() {
        var kernels = new FeastKernels();
        assertFalse(kernels.observeSidebar(List.of("Purse: 1,234", "Rewards: Kernels x25")));
        assertNull(kernels.balance());
        assertTrue(kernels.observeSidebar(List.of("§eKernels: §f1,234")));
        assertEquals(Long.valueOf(1_234), kernels.balance());
        kernels.observeMessage(TED);
        assertFalse(kernels.observeSidebar(List.of("Kernels: 1,234", "Farming: 999")));
        assertEquals(Long.valueOf(1_235), kernels.balance());
        assertFalse(kernels.observeSidebar(List.of("Kernels: 1,235")));
        assertEquals(Long.valueOf(1_235), kernels.balance());
        assertTrue(kernels.observeSidebar(List.of("Kernels: 1,210")));
        assertEquals(Long.valueOf(1_210), kernels.balance());
        kernels.worldChanged();
        assertEquals(Long.valueOf(1_210), kernels.balance());
        assertTrue(kernels.observeSidebar(List.of("Kernels: 0")));
        assertEquals(Long.valueOf(0), kernels.balance());
    }

    @Test
    public void hubBalanceReadsTheTotalWithoutAddingItsDisplayedRecentGainAgain() {
        var kernels = new FeastKernels();
        assertTrue(kernels.observeSidebar(List.of("§e123 §6(+3) §fKernels")));
        assertEquals(Long.valueOf(123), kernels.balance());
        assertTrue(kernels.observeSidebar(List.of("1,234 Kernels")));
        assertEquals(Long.valueOf(1_234), kernels.balance());
        assertFalse(kernels.observeSidebar(List.of("1.2k Kernels")));
    }

    @Test
    public void serverBalanceWithRecentGainsAndUnicodeSpacesSyncsWithoutScott() {
        var kernels = new FeastKernels();
        assertTrue(kernels.observeSidebar(List.of("§eKernels: §f123 §a(+3)")));
        assertEquals(Long.valueOf(123), kernels.balance());
        assertTrue(kernels.observeSidebar(List.of("Kernels:\u00a01,234\u00a0(+3)")));
        assertEquals(Long.valueOf(1_234), kernels.balance());
        assertFalse(kernels.observeSidebar(List.of("Kernels: 1.2k (+3)")));
        assertFalse(kernels.observeSidebar(List.of("Kernels: 123 from a friend")));
    }

    @Test
    public void delayedSidebarCannotEraseDonationsAlreadyConfirmedByTed() {
        var kernels = new FeastKernels();
        kernels.observeSidebar(List.of("Kernels: 134"));
        assertTrue(kernels.observeMessage(TED));
        assertTrue(kernels.observeMessage(TED));
        assertFalse(kernels.observeSidebar(List.of("Kernels: 135")));
        assertEquals(Long.valueOf(136), kernels.balance());
        assertEquals(1, kernels.pendingGains());
        assertFalse(kernels.observeSidebar(List.of("Kernels: 135", "Purse: 100")));
        assertFalse(kernels.observeSidebar(List.of("Kernels: 136")));
        assertEquals(Long.valueOf(136), kernels.balance());
        assertEquals(0, kernels.pendingGains());
    }

    @Test
    public void donationReadsTheAlreadyAppliedSidebarBeforeTheSharedTickCacheCatchesUp() {
        var kernels = new FeastKernels();
        kernels.observeSidebar(List.of("Kernels: 135"));
        assertTrue(kernels.observeMessage(TED, List.of("Kernels: 136")));
        assertEquals(Long.valueOf(137), kernels.balance());
        assertEquals(1, kernels.pendingGains());
        assertFalse(kernels.observeSidebar(List.of("Kernels: 136")));
        assertEquals(Long.valueOf(137), kernels.balance());
    }

    @Test
    public void aDonationCanEstablishItsBaselineWithoutScottOrAnotherMod() {
        var kernels = new FeastKernels();
        assertTrue(kernels.observeMessage(TED, List.of("Kernels: 135")));
        assertEquals(Long.valueOf(136), kernels.balance());
        var unknown = new FeastKernels();
        assertFalse(unknown.observeMessage(TED, List.of("Purse: 135")));
        assertNull(unknown.balance());
        assertFalse(unknown.observeMessage("Guild > Friend: " + TED, List.of("Kernels: 135")));
        assertNull(unknown.balance());
    }

    @Test
    public void sidebarBeforeTheNextDonationAndLagAcrossAWorldChangeKeepConfirmedGains() {
        var kernels = new FeastKernels();
        kernels.observeMenu(new Object(), 134L);
        kernels.observeMessage(TED);
        assertFalse(kernels.observeSidebar(List.of("Kernels: 135")));
        kernels.observeMessage(TED);
        kernels.worldChanged();
        assertFalse(kernels.observeSidebar(List.of("Kernels: 135")));
        assertEquals(Long.valueOf(136), kernels.balance());
        assertEquals(1, kernels.pendingGains());
    }

    @Test
    public void missingDonationEvidenceNeverCreatesAnAutomaticPlusOneCorrection() {
        var kernels = new FeastKernels();
        assertTrue(kernels.observeSidebar(List.of("Kernels: 135")));
        assertEquals(Long.valueOf(135), kernels.balance());
        kernels.observeMenu(new Object(), 136L);
        kernels.worldChanged();
        assertTrue(kernels.observeSidebar(List.of("Kernels: 135")));
        assertEquals(Long.valueOf(135), kernels.balance());
        assertEquals(0, kernels.pendingGains());
    }

    @Test
    public void freshMenuAndLargerSidebarDecreasesStillCorrectSpending() {
        var kernels = new FeastKernels();
        kernels.observeMenu(new Object(), 135L);
        kernels.observeMessage(TED);
        assertTrue(kernels.observeSidebar(List.of("Kernels: 100")));
        assertEquals(Long.valueOf(100), kernels.balance());
        assertEquals(0, kernels.pendingGains());
        kernels.observeMessage(TED);
        assertTrue(kernels.observeMenu(new Object(), 100L));
        assertEquals(Long.valueOf(100), kernels.balance());
        assertEquals(0, kernels.pendingGains());
        kernels.observeMessage(TED);
        assertTrue(kernels.observeMenu(new Object(), 0L));
        assertEquals(Long.valueOf(0), kernels.balance());
        assertEquals(0, kernels.pendingGains());
    }

    @Test
    public void staleOrInvalidSidebarRowsCannotRestoreAnInvalidatedBalance() {
        var kernels = new FeastKernels();
        kernels.observeSidebar(List.of("Kernels: 100"));
        kernels.invalidate();
        assertFalse(kernels.observeSidebar(List.of("Kernels: 100")));
        assertNull(kernels.balance());
        kernels.worldChanged();
        assertFalse(kernels.observeSidebar(List.of("Kernels: 1.2k")));
        assertFalse(kernels.observeSidebar(List.of("Kernels: 10", "Kernels: 20")));
        assertFalse(kernels.observeSidebar(List.of("Kernels: 999999999999999999999")));
        assertTrue(kernels.observeSidebar(List.of("Kernels: 100")));
        assertEquals(Long.valueOf(100), kernels.balance());
    }

    @Test
    public void balanceDoesNotOverflow() {
        var kernels = new FeastKernels();
        kernels.observeMenu(new Object(), Long.MAX_VALUE);
        assertFalse(kernels.observeMessage(TED));
        assertEquals(Long.valueOf(Long.MAX_VALUE), kernels.balance());
    }

    @Test
    public void onlyGrandFeastShowsKernelsBesideTheNextTier() {
        var grand = new FeastProgress.Snapshot(FeastProgress.Kind.GRAND, 234,
            List.of(5, 25, 75, 150, 250, 350, 450, 550, 750));
        assertEquals("16 to next - 1,234 Kernels", FeastOverlayFeature.footer(FeastProgress.Kind.GRAND, grand, 1_234L));
        assertEquals("16 to next - -- Kernels", FeastOverlayFeature.footer(FeastProgress.Kind.GRAND, grand, null));
        assertEquals("16 to next - 0 Kernels", FeastOverlayFeature.footer(FeastProgress.Kind.GRAND, grand, 0L));
        var harvest = new FeastProgress.Snapshot(FeastProgress.Kind.HARVEST, 234, List.of(5, 25, 75, 150, 250));
        assertEquals("16 to next", FeastOverlayFeature.footer(FeastProgress.Kind.HARVEST, harvest, 1_234L));
        var complete = new FeastProgress.Snapshot(FeastProgress.Kind.GRAND, 750, grand.goals());
        assertEquals("All milestones reached - 1,234 Kernels",
            FeastOverlayFeature.footer(FeastProgress.Kind.GRAND, complete, 1_234L));
    }

    private static FeastProgress.MenuItem bakery(String balance) {
        return new FeastProgress.MenuItem("§6Grand Bakery", List.of("Spend Kernels on rewards!", "", balance));
    }
}
