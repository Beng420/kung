package com.github.beng420.kung.feature.garden;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FeastPersistenceTest {
    private static final String ACCOUNT = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_ACCOUNT = "00000000-0000-0000-0000-000000000002";
    private static final String PROFILE_ID = "9fe73204-daad-43c6-9d76-a449529e6c47";
    private static final FeastContext.Event EVENT = new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:513");
    private static final String SEASONING = "RARE CROP! Seasoning (+148\uE02B) (automatically donated)";
    private static final String TED = "[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse.";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void restartRestoresProgressAndKernelsAndContinuesCountingWithoutMenus() {
        Path file = file();
        var original = new State(file);
        original.select(ACCOUNT, "Coconut");
        original.sync(234, 123);
        original.persistence.identify(PROFILE_ID);
        original.donate();
        original.store.flush();
        var restarted = new State(file);
        restarted.select(ACCOUNT, "Coconut");
        restarted.persistence.identify(PROFILE_ID);
        assertEquals(235, restarted.session.snapshot().donations());
        assertEquals(Long.valueOf(124), restarted.kernels.balance());
        restarted.donate();
        assertEquals(236, restarted.session.snapshot().donations());
        assertEquals(Long.valueOf(125), restarted.kernels.balance());
    }

    @Test
    public void savedRateSurvivesRestartAndOfflineTimeThenBlendsWithoutAnotherWarmup() {
        var original = new State(file());
        original.select(ACCOUNT, "Coconut");
        original.persistence.identify(PROFILE_ID);
        farmBlock(original.rate, 6);
        assertEquals(Double.valueOf(72), original.rate.value());
        original.rate.pause(300_000);
        original.persistence.save();
        original.store.flush();

        var restarted = new State(file());
        restarted.select(ACCOUNT, "Coconut");
        restarted.persistence.identify(PROFILE_ID);
        assertEquals(Long.valueOf(72), restarted.rate.snapshot(0).perHour());
        assertTrue(restarted.rate.snapshot(0).paused());
        farmBlock(restarted.rate, 3);
        assertEquals(64.8, restarted.rate.value(), 1e-10);
        restarted.rate.pause(300_000);
        assertEquals(Long.valueOf(65), restarted.rate.snapshot(86_400_000).perHour());
        restarted.persistence.save();
        restarted.store.flush();

        var again = new State(file());
        again.select(ACCOUNT, "Coconut");
        assertEquals("Persist full precision, not the rounded HUD text", 64.8, again.rate.value(), 1e-10);
        assertEquals(Long.valueOf(65), again.rate.snapshot(86_400_000).perHour());
        assertEquals(0, again.rate.snapshot(86_400_000).farmingMillis());
    }

    @Test
    public void claimedMilestoneKernelsPersistWithoutAdvancingDonationsOrReplayingTheClaim() {
        Path file = file();
        var original = new State(file);
        original.select(ACCOUNT, "Coconut");
        original.sync(150, 179);
        var claimable = new FeastMilestoneClaims.Milestone(4, 100, true);
        var claimed = new FeastMilestoneClaims.Milestone(4, 100, false);
        assertTrue(original.kernels.beginMilestoneClaim(claimable, 0));
        assertNotNull(original.kernels.observeMilestone(claimed, 100));
        original.persistence.save();
        original.store.flush();
        var restarted = new State(file);
        restarted.select(ACCOUNT, "Coconut");
        assertEquals(150, restarted.session.snapshot().donations());
        assertEquals(Long.valueOf(279), restarted.kernels.balance());
        assertEquals(100, restarted.kernels.pendingGains());
        assertNull(restarted.kernels.observeMilestone(claimed, 200));
        assertFalse(restarted.kernels.beginMilestoneClaim(claimed, 201));
        assertFalse(restarted.kernels.observeSidebar(List.of("Kernels: 179")));
        assertEquals(Long.valueOf(279), restarted.kernels.balance());
        assertFalse(restarted.kernels.observeSidebar(List.of("Kernels: 279")));
        assertEquals(0, restarted.kernels.pendingGains());
    }

    @Test
    public void repeatedLobbyProfileAnnouncementsKeepTheLiveCounter() {
        var state = new State(file());
        var context = new FeastContext();
        String announcement = "[18:09:02] §aYou are playing on profile: §eCoconut";
        context.observeProfileMessage(announcement);
        state.select(ACCOUNT, context.profile());
        state.sync(27, 123);
        state.donate();
        context.worldChanged();
        context.observeProfileMessage(announcement);
        assertFalse(state.persistence.select(ACCOUNT, context.profile()));
        assertEquals(28, state.session.snapshot().donations());
        assertEquals(Long.valueOf(124), state.kernels.balance());
    }

    @Test
    public void profilesAndAccountsHaveSeparateCachedTotals() {
        var state = new State(file());
        state.select(ACCOUNT, "Coconut");
        state.sync(234, 123);
        state.rate.restore(60.25);
        state.select(ACCOUNT, "Apple");
        assertNull(state.session.snapshot());
        assertNull(state.kernels.balance());
        assertNull(state.rate.value());
        state.sync(27, 999);
        state.rate.restore(90.75);
        state.select(ACCOUNT, "Coconut");
        assertEquals(234, state.session.snapshot().donations());
        assertEquals(Long.valueOf(123), state.kernels.balance());
        assertEquals(Double.valueOf(60.25), state.rate.value());
        state.select(OTHER_ACCOUNT, "Coconut");
        assertNull(state.session.snapshot());
        assertNull(state.kernels.balance());
        assertNull(state.rate.value());
        state.select(ACCOUNT, "Apple");
        assertEquals(27, state.session.snapshot().donations());
        assertEquals(Long.valueOf(999), state.kernels.balance());
        assertEquals(Double.valueOf(90.75), state.rate.value());
    }

    @Test
    public void recreatedProfileDoesNotInheritTheOldFruitNamesBalance() {
        var state = new State(file());
        state.select(ACCOUNT, "Coconut");
        state.persistence.identify(PROFILE_ID);
        state.sync(234, 123);
        state.rate.restore(42.5);
        state.persistence.save();
        state = new State(file());
        state.select(ACCOUNT, "Coconut");
        assertEquals(Double.valueOf(42.5), state.rate.value());
        state.persistence.identify(OTHER_ACCOUNT);
        state.persistence.restoreProgress();
        assertNull(state.session.snapshot());
        assertNull(state.kernels.balance());
        assertNull(state.rate.value());
        var restarted = new State(file());
        restarted.select(ACCOUNT, "Coconut");
        assertNull(restarted.session.snapshot());
        assertNull(restarted.kernels.balance());
        assertNull(restarted.rate.value());
    }

    @Test
    public void newFeastStartsUnknownWhileCurrencySurvivesAndOldEventCanRecoverAfterAnOutage() {
        var state = new State(file());
        state.select(ACCOUNT, "Coconut");
        state.sync(234, 123);
        state.session.select(null, true);
        state.persistence.save();
        state.session.select(EVENT, true);
        state.persistence.restoreProgress();
        assertEquals(234, state.session.snapshot().donations());
        state.session.select(new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:514"), true);
        state.persistence.restoreProgress();
        assertNull(state.session.snapshot());
        assertEquals(Long.valueOf(123), state.kernels.balance());
        state.sync(5, 150);
        var restarted = new State(file());
        restarted.session.select(new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:514"), true);
        restarted.select(ACCOUNT, "Coconut");
        assertEquals(5, restarted.session.snapshot().donations());
        assertEquals(Long.valueOf(150), restarted.kernels.balance());
    }

    @Test
    public void profileCanArriveBeforeTheEventAndFreshMenuCanCorrectSavedTotalsDownward() {
        var original = new State(file());
        original.select(ACCOUNT, "Coconut");
        original.sync(234, 123);
        var restarted = new State(file());
        restarted.session.select(null, false);
        restarted.select(ACCOUNT, "Coconut");
        assertNull(restarted.session.snapshot());
        assertEquals(Long.valueOf(123), restarted.kernels.balance());
        restarted.session.select(EVENT, true);
        restarted.persistence.restoreProgress();
        assertEquals(234, restarted.session.snapshot().donations());
        restarted.sync(233, 100);
        var again = new State(file());
        again.select(ACCOUNT, "Coconut");
        assertEquals(233, again.session.snapshot().donations());
        assertEquals(Long.valueOf(100), again.kernels.balance());
    }

    @Test
    public void observationsBeforeTheFirstProfileRowAreSavedOnceIdentityArrives() {
        var state = new State(file());
        state.sync(27, 123);
        state.select(ACCOUNT, "Coconut");
        var restarted = new State(file());
        restarted.select(ACCOUNT, "Coconut");
        assertEquals(27, restarted.session.snapshot().donations());
        assertEquals(Long.valueOf(123), restarted.kernels.balance());
    }

    @Test
    public void unreflectedKernelDonationsSurviveRestartAndAcknowledgeWithoutDoubleCounting() {
        var state = new State(file());
        state.select(ACCOUNT, "Coconut");
        state.sync(58, 135);
        state.donate();
        state.persistence.save();
        var restarted = new State(file());
        restarted.select(ACCOUNT, "Coconut");
        assertEquals(Long.valueOf(136), restarted.kernels.balance());
        assertEquals(1, restarted.kernels.pendingGains());
        assertFalse(restarted.kernels.observeSidebar(List.of("Kernels: 135")));
        assertEquals(Long.valueOf(136), restarted.kernels.balance());
        assertFalse(restarted.kernels.observeSidebar(List.of("Kernels: 136")));
        assertEquals(0, restarted.kernels.pendingGains());
        restarted.persistence.save();
        var again = new State(file());
        again.select(ACCOUNT, "Coconut");
        assertEquals(0, again.kernels.pendingGains());
        assertEquals(Long.valueOf(136), again.kernels.balance());
        assertTrue(again.kernels.observeMessage(TED));
        assertEquals(Long.valueOf(137), again.kernels.balance());
    }

    @Test
    public void olderCacheWithoutPendingGainsStillRestoresItsKnownBalance() throws Exception {
        Files.writeString(file(), "{\"version\":1,\"profiles\":{\"" + ACCOUNT + ":coconut\":{"
            + "\"profileId\":\"\",\"eventKey\":\"\",\"kernels\":136}}}");
        var state = new State(file());
        state.select(ACCOUNT, "Coconut");
        assertEquals(Long.valueOf(136), state.kernels.balance());
        assertEquals(0, state.kernels.pendingGains());
        assertNull(state.rate.value());
    }

    @Test
    public void invalidOptionalRateDoesNotDiscardValidCurrencyAndZeroIsKnown() throws Exception {
        for (String rate : List.of("-1", "1e999", "0")) {
            Files.writeString(file(), "{\"version\":1,\"profiles\":{\"" + ACCOUNT + ":coconut\":{"
                + "\"profileId\":\"\",\"eventKey\":\"\",\"kernels\":136,\"kernelRatePerHour\":" + rate + "}}}");
            var state = new State(file());
            state.select(ACCOUNT, "Coconut");
            assertEquals(Long.valueOf(136), state.kernels.balance());
            if (rate.equals("0")) assertEquals(Long.valueOf(0), state.rate.snapshot(0).perHour());
            else assertNull(state.rate.value());
        }
    }

    @Test
    public void invalidPendingGainsCannotProtectMadeUpDonations() throws Exception {
        for (long pending : List.of(-1L, 137L)) {
            Files.writeString(file(), "{\"version\":1,\"profiles\":{\"" + ACCOUNT + ":coconut\":{"
                + "\"profileId\":\"\",\"eventKey\":\"\",\"kernels\":136,\"pendingKernelGains\":" + pending + "}}}");
            var state = new State(file());
            state.select(ACCOUNT, "Coconut");
            assertNull(state.kernels.balance());
            assertEquals(0, state.kernels.pendingGains());
        }
    }

    @Test
    public void writesCoalesceAndReplaceThePriorFileWithTheLatestSnapshot() {
        var queued = new ArrayDeque<Runnable>();
        var state = new State(file(), queued::add);
        state.select(ACCOUNT, "Coconut");
        state.sync(27, 123);
        state.donate();
        state.donate();
        assertEquals(1, queued.size());
        assertFalse(Files.exists(file()));
        queued.remove().run();
        var restarted = new State(file());
        restarted.select(ACCOUNT, "Coconut");
        assertEquals(29, restarted.session.snapshot().donations());
        assertEquals(Long.valueOf(125), restarted.kernels.balance());
        state.sync(30, 100);
        queued.remove().run();
        var corrected = new State(file());
        corrected.select(ACCOUNT, "Coconut");
        assertEquals(30, corrected.session.snapshot().donations());
        assertEquals(Long.valueOf(100), corrected.kernels.balance());
        assertFalse(Files.exists(file().resolveSibling("feast-progress.json.tmp")));
    }

    @Test
    public void corruptUnsupportedAndInvalidCachesStayUnknown() throws Exception {
        for (String data : List.of("broken", "{\"version\":2,\"profiles\":{}}", "x".repeat(131_073),
            "{\"version\":1,\"profiles\":{\"" + ACCOUNT + ":coconut\":{\"profileId\":\"\",\"eventKey\":\"grand:513\","
                + "\"kernels\":123,\"progress\":{\"kind\":\"GRAND\",\"donations\":27,\"goals\":[5,25,20]}}}}")) {
            Files.writeString(file(), data);
            var state = new State(file());
            state.select(ACCOUNT, "Coconut");
            assertNull(state.session.snapshot());
            assertNull(state.kernels.balance());
        }
    }

    private Path file() { return temporary.getRoot().toPath().resolve("feast-progress.json"); }

    private static void farmBlock(FeastKernelRate rate, int gains) {
        rate.updateContext(true, EVENT, 0);
        for (int second = 0; second < 300; second++) {
            long now = second * 1_000L;
            rate.crop(now);
            if (second % (300 / gains) == 0) assertTrue(rate.observeMessage(TED, now));
        }
        rate.crop(300_000);
    }

    private static final class State {
        final FeastSession session = new FeastSession();
        final FeastKernels kernels = new FeastKernels();
        final FeastKernelRate rate = new FeastKernelRate();
        final FeastStateStore store;
        final FeastPersistence persistence;
        State(Path file) { this(file, Runnable::run); }
        State(Path file, Executor executor) {
            store = new FeastStateStore(file, executor);
            persistence = new FeastPersistence(store, session, kernels, rate);
            session.select(EVENT, true);
        }
        void select(String account, String profile) { persistence.select(account, profile); }
        void sync(int donations, long balance) {
            session.observeMenu(new Object(), new FeastProgress.Snapshot(FeastProgress.Kind.GRAND,
                donations, List.of(5, 25, 75, 150, 250, 350, 450, 550, 750)));
            kernels.observeMenu(new Object(), balance);
            persistence.save();
        }
        void donate() {
            assertTrue(session.donate(SEASONING));
            assertTrue(kernels.observeMessage(TED));
            persistence.save();
        }
    }
}
