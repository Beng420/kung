package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;
import static com.github.beng420.kung.feature.dungeon.M7DragonTracker.Outcome.*;
import static com.github.beng420.kung.feature.dungeon.M7DragonTracker.Statue.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

public final class M7DragonTrackerTest {
    private static final String CONFIRM = "[BOSS] Wither King: Oh, this one hurts!";

    @Test public void originRangeHasInclusiveEdgesAndSpawnMatchingStaysNearSpawn() {
        for (var statue : M7DragonTracker.Statue.values()) {
            var box = statue.range();
            assertTrue(statue.contains(statue.spawn()));
            assertTrue(statue.contains(new Vec3(box.minX, box.minY, box.minZ)));
            assertTrue(statue.contains(new Vec3(box.maxX, box.maxY, box.maxZ)));
            assertFalse(statue.contains(new Vec3(box.minX - 0.001, box.minY, box.minZ)));
            assertFalse(statue.contains(new Vec3(box.maxX, box.maxY + 0.001, box.maxZ)));
            assertFalse(statue.contains(new Vec3(box.maxX, box.maxY, box.maxZ + 0.001)));
            assertSame(statue, M7DragonTracker.Statue.atSpawn(statue.spawn().add(4, 0, 0)));
            assertNull(M7DragonTracker.Statue.atSpawn(statue.spawn().add(4.001, 0, 0)));
            assertNull(M7DragonTracker.Statue.atSpawn(new Vec3(box.minX, box.minY, box.minZ)));
        }
    }

    @Test public void unloadAndSameUuidReloadPreserveAttemptWithoutInventingADeath() {
        var tracker = new M7DragonTracker();
        UUID uuid = UUID.randomUUID();
        tracker.spawn(uuid, RED, RED.spawn());
        var attempt = tracker.attempt(uuid);
        tracker.unload(uuid);
        assertFalse(attempt.loaded());
        for (int i = 0; i < 80; i++) assertTrue(tracker.advance().isEmpty());
        assertFalse(attempt.dead());
        assertNull(attempt.outcome());
        Vec3 moved = RED.spawn().add(1, 0, 0);
        assertTrue(tracker.spawn(uuid, RED, moved).isEmpty());
        assertSame(attempt, tracker.latest(RED));
        assertTrue(attempt.loaded());
        assertEquals(moved, attempt.position());
        tracker.death(uuid);
        tracker.spawn(uuid, RED, RED.spawn());
        tracker.move(uuid, RED.spawn());
        assertTrue(attempt.dead());
        assertEquals(moved, attempt.position());
    }

    @Test public void deathTimesOutOnceAndLaterStatueEvidenceCanUpgradeUnknown() {
        var tracker = new M7DragonTracker();
        UUID uuid = UUID.randomUUID();
        tracker.spawn(uuid, RED, RED.spawn());
        tracker.death(uuid);
        tracker.unload(uuid);
        for (int i = 0; i < 40; i++) assertTrue(tracker.advance().isEmpty());
        assertEquals(40, tracker.tick());
        tracker.death(uuid); // Duplicate death must not extend the window.
        assertEquals(UNKNOWN, tracker.advance().getFirst().outcome());
        assertTrue(tracker.advance().isEmpty());
        assertEquals(COUNTS, tracker.statueBroken(RED).getFirst().outcome());
        assertEquals(COUNTS, tracker.attempt(uuid).outcome());
        assertTrue(tracker.statueBroken(RED).isEmpty());
    }

    @Test public void exactBossMessagesConfirmUniqueRecentDeathAndDeduplicateStatueEvidence() {
        for (String message : List.of(CONFIRM, "[BOSS] Wither King: I have more of those.",
            "[BOSS] Wither King: My soul is disposable.")) {
            var tracker = new M7DragonTracker();
            UUID uuid = UUID.randomUUID();
            tracker.spawn(uuid, BLUE, BLUE.spawn());
            tracker.death(uuid);
            for (int i = 0; i < 40; i++) tracker.advance();
            var result = tracker.confirmMessage(message).getFirst();
            assertSame(BLUE, result.statue());
            assertEquals(COUNTS, result.outcome());
            assertTrue(tracker.confirmMessage(message).isEmpty());
            assertTrue(tracker.statueBroken(BLUE).isEmpty());
            assertTrue(tracker.advance().isEmpty());
        }
    }

    @Test public void statueBeforeDeathPacketConfirmsWithoutWaitingOrDuplicateNotice() {
        var tracker = new M7DragonTracker();
        UUID uuid = UUID.randomUUID();
        tracker.spawn(uuid, GREEN, GREEN.spawn());
        assertEquals(COUNTS, tracker.statueBroken(GREEN).getFirst().outcome());
        tracker.death(uuid);
        assertTrue(tracker.confirmMessage(CONFIRM).isEmpty());
        assertEquals(COUNTS, tracker.latest(GREEN).outcome());
    }

    @Test public void simultaneousDeathsNeverGuessAColorOrSwallowSeparateAnonymousConfirmations() {
        var tracker = new M7DragonTracker();
        for (var statue : List.of(RED, BLUE)) {
            UUID uuid = UUID.randomUUID();
            tracker.spawn(uuid, statue, statue.spawn());
            tracker.death(uuid);
        }
        assertNull(tracker.confirmMessage(CONFIRM).getFirst().statue());
        tracker.statueBroken(RED);
        assertNull(tracker.confirmMessage(CONFIRM).getFirst().statue());
        assertNull(tracker.latest(BLUE).outcome());
        tracker.statueBroken(BLUE);
        assertEquals(COUNTS, tracker.latest(BLUE).outcome());
        assertTrue(tracker.confirmMessage("Party > Ben: " + CONFIRM).isEmpty());
        assertTrue(tracker.confirmMessage("[BOSS] Wither King: You... again?").isEmpty());
        assertTrue(tracker.confirmMessage(null).isEmpty());
    }

    @Test public void unknownConfirmedDeathPreventsAssigningItsMessageToTheOnlyKnownDragon() {
        var tracker = new M7DragonTracker();
        UUID red = UUID.randomUUID(), unknown = UUID.randomUUID();
        tracker.spawn(red, RED, RED.spawn());
        tracker.death(red);
        assertTrue(tracker.unknownDeath(unknown));
        assertFalse(tracker.unknownDeath(unknown));
        var result = tracker.confirmMessage(CONFIRM).getFirst();
        assertNull(result.statue());
        assertEquals(COUNTS, result.outcome());
        assertNull(tracker.attempt(red).outcome());
        assertFalse(tracker.statueCounted(RED));
        assertNull(tracker.confirmMessage(CONFIRM).getFirst().statue());
        assertEquals(COUNTS, tracker.statueBroken(RED).getFirst().outcome());
    }

    @Test public void duplicateUnknownDeathDoesNotExtendItsInclusiveFortyTickWindow() {
        var tracker = new M7DragonTracker();
        UUID unknown = UUID.randomUUID(), blue = UUID.randomUUID();
        assertTrue(tracker.unknownDeath(unknown));
        for (int i = 0; i < 39; i++) tracker.advance();
        assertFalse(tracker.unknownDeath(unknown));
        tracker.spawn(blue, BLUE, BLUE.spawn());
        tracker.death(blue);
        tracker.advance();
        assertEquals(40, tracker.tick());
        assertNull(tracker.confirmMessage(CONFIRM).getFirst().statue());
        tracker.advance();
        assertSame(BLUE, tracker.confirmMessage(CONFIRM).getFirst().statue());
        assertEquals(COUNTS, tracker.attempt(blue).outcome());
    }

    @Test public void resetClearsUnknownDeathEvidenceAndDeduplication() {
        var tracker = new M7DragonTracker();
        UUID unknown = UUID.randomUUID(), green = UUID.randomUUID();
        tracker.unknownDeath(unknown);
        tracker.reset();
        tracker.spawn(green, GREEN, GREEN.spawn());
        tracker.death(green);
        assertSame(GREEN, tracker.confirmMessage(CONFIRM).getFirst().statue());
        assertTrue(tracker.unknownDeath(unknown));
    }

    @Test public void respawnCannotProveFailureAndResetDropsEarlierEvidence() {
        var tracker = new M7DragonTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        tracker.spawn(first, PURPLE, PURPLE.spawn());
        tracker.death(first);
        assertEquals(UNKNOWN, tracker.spawn(second, PURPLE, PURPLE.spawn()).getFirst().outcome());
        assertEquals(UNKNOWN, tracker.attempt(first).outcome());
        tracker.statueBroken(PURPLE);
        tracker.death(second);
        assertTrue(tracker.spawn(third, PURPLE, PURPLE.spawn()).isEmpty());
        assertEquals(COUNTS, tracker.attempt(second).outcome());
        tracker.reset();
        assertNull(tracker.latest(PURPLE));
        assertNull(tracker.attempt(first));
        assertEquals(0, tracker.tick());
        assertEquals(COUNTS, tracker.statueBroken(PURPLE).getFirst().outcome());
    }

    @Test public void capacityRetiresOldAttemptsWithoutEvictingLoadedLiveDragons() {
        var tracker = new M7DragonTracker();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < M7DragonTracker.MAX_ATTEMPTS; i++) {
            UUID uuid = UUID.randomUUID();
            ids.add(uuid);
            tracker.spawn(uuid, RED, RED.spawn());
        }
        UUID next = UUID.randomUUID();
        tracker.spawn(next, BLUE, BLUE.spawn());
        assertNull(tracker.attempt(next));
        for (UUID uuid : ids) assertNotNull(tracker.attempt(uuid));
        tracker.unload(ids.getFirst());
        tracker.spawn(next, BLUE, BLUE.spawn());
        assertNull(tracker.attempt(ids.getFirst()));
        assertNotNull(tracker.attempt(next));
        for (UUID uuid : ids.subList(1, ids.size())) assertNotNull(tracker.attempt(uuid));
    }
}
