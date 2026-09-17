package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.DungeonConfig.DragonDebuffScope;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

public final class DungeonDebuffTrackerTest {
    @Test
    public void earlyHitFeedbackKeepsSameTickMultiplicityAndOriginalOffsetsAcrossSelectionDelay() {
        var tracker = new DungeonDebuffTracker();
        UUID red = UUID.randomUUID();
        var dragon = tracker.spawn(red, "Red", new Vec3(32, 6, 59));
        tracker.advance();
        for (int i = 0; i < 3; i++) assertTrue(tracker.arrowHit());
        for (int i = 0; i < 3; i++) tracker.advance();
        assertTrue(tracker.arrowHit());
        assertTrue(tracker.arrowHit());
        assertEquals(5, dragon.arrows.count);
        assertEquals(1, dragon.arrows.first);
        assertEquals(4, dragon.arrows.fifth);
        assertTrue(dragon.arrows.describe().contains("[1, 1, 1, 4, 4]"));
        assertTrue(dragon.arrows.describe().contains("26.7/20t"));
        assertTrue(dragon.arrows.describe().contains("Gaps (ticks): [0, 0, 3, 0]"));
    }

    @Test
    public void liveTraceEarlyThreeHitsGoToOneStatueAndLaterDpsDoesNotCount() {
        var tracker = new DungeonDebuffTracker();
        var blue = tracker.spawn(UUID.randomUUID(), "Blue", new Vec3(85.1099, 6, 96.3198));
        var purple = tracker.spawn(UUID.randomUUID(), "Purple", new Vec3(85.1099, 6, 96.3198));
        // 20:31:01 trace: both dragons previously received [1, 4, 4, 133, 133, ...].
        for (int offset : new int[] {1, 4, 4, 133, 133, 133, 134, 134, 136}) {
            while (tracker.tick() < offset) tracker.advance();
            assertEquals(offset <= 40, tracker.arrowHit());
        }
        assertEquals(3, blue.arrows.count);
        assertEquals(0, purple.arrows.count);
        assertTrue(blue.summary(tracker.tick()).contains("Arrows: 3"));
        assertTrue(purple.summary(tracker.tick()).contains("Arrows: --"));
        assertFalse(blue.summary(tracker.tick()).contains("Hit sounds"));
    }

    @Test
    public void impactSoundsAndRemotePlayerFeedbackAreRejected() {
        var player = new Vec3(56, 6, 120);
        assertTrue(DungeonDebuffTracker.localArrowFeedback("minecraft:entity.arrow.hit_player", player, player));
        assertTrue(DungeonDebuffTracker.localArrowFeedback("minecraft:entity.arrow.hit_player", player.add(0, 1.6, 0), player));
        assertFalse(DungeonDebuffTracker.localArrowFeedback("minecraft:entity.arrow.hit", player, player));
        assertFalse(DungeonDebuffTracker.localArrowFeedback("minecraft:entity.arrow.hit_player", new Vec3(80, 6, 56), player));
    }

    @Test
    public void windowEndsAfterFortyTicksAndMissingPositionCannotInventAnAssignment() {
        var tracker = new DungeonDebuffTracker();
        var dragon = tracker.spawn(UUID.randomUUID(), "Red", new Vec3(32, 6, 59));
        while (tracker.tick() < 40) tracker.advance();
        assertTrue(tracker.arrowHit());
        tracker.advance();
        assertFalse(tracker.arrowHit());
        assertEquals(1, dragon.arrows.count);
        tracker.clearDragons();
        var unknown = tracker.spawn(UUID.randomUUID(), "Blue");
        tracker.arrowHit();
        for (int i = 0; i < 3; i++) tracker.advance();
        assertFalse(tracker.arrowHit());
        assertEquals(0, unknown.arrows.count);
    }

    @Test
    public void sprayUsesOriginalPacketTickExpiresInServerTicksAndKeepsFirstDragonTiming() {
        var tracker = new DungeonDebuffTracker();
        UUID uuid = UUID.randomUUID();
        var dragon = tracker.spawn(uuid, "Purple");
        tracker.advance();
        long packetTick = tracker.tick();
        tracker.advance(); // Delayed metadata/target resolution must not shift the measurement.
        assertTrue(tracker.spray(uuid, packetTick));
        assertEquals(1, dragon.sprayTick);
        assertFalse(tracker.spray(uuid, packetTick));
        while (tracker.tick() < 100) tracker.advance();
        assertTrue(tracker.highlighted().contains(uuid));
        tracker.advance();
        assertFalse(tracker.highlighted().contains(uuid));
        tracker.spray(uuid, tracker.tick());
        assertEquals(1, dragon.sprayTick);
        assertTrue(tracker.highlighted().contains(uuid));
    }

    @Test
    public void ordinaryMobSprayDoesNotRequireDragonTrackingAndResetClearsBoth() {
        var tracker = new DungeonDebuffTracker();
        UUID mob = UUID.randomUUID();
        assertFalse(tracker.spray(mob, tracker.tick()));
        assertTrue(tracker.highlighted().contains(mob));
        tracker.spawn(UUID.randomUUID(), "Green");
        tracker.clearDragons();
        assertTrue(tracker.highlighted().contains(mob));
        tracker.reset();
        assertTrue(tracker.highlighted().isEmpty());
        assertEquals(0, tracker.tick());
    }

    @Test
    public void unloadedDragonIsNotKilledAndSameUuidCannotRestartItsClock() {
        var tracker = new DungeonDebuffTracker();
        UUID uuid = UUID.randomUUID();
        var dragon = tracker.spawn(uuid, "Orange");
        tracker.advance();
        assertSame(dragon, tracker.spawn(uuid, "Orange"));
        assertEquals(0, dragon.spawnTick);
        assertSame(dragon, tracker.end(uuid, false));
        assertTrue(dragon.details().contains("death not confirmed"));
        assertTrue(dragon.summary(tracker.tick()).contains("Sprayed: no"));
        assertNull(tracker.end(uuid, true));
        assertSame(dragon, tracker.spawn(uuid, "Orange"));
        tracker.arrowHit();
        assertEquals(0, dragon.arrows.count);
        UUID next = UUID.randomUUID();
        assertEquals(1, tracker.spawn(next, "Orange").spawnTick);
    }

    @Test
    public void iceMarkerRequiresUniqueNearbyTargetRatherThanEveryNearbyDragon() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var mob = new DungeonDebuffTracker.Target(a, Vec3.ZERO, new AABB(-.5, 0, -.5, .5, 2, .5), false);
        assertEquals(a, DungeonDebuffTracker.uniqueTarget(new Vec3(0, 1, 0), List.of(mob)));
        assertNull(DungeonDebuffTracker.uniqueTarget(new Vec3(5, 1, 0), List.of(mob)));
        var overlap = new DungeonDebuffTracker.Target(b, Vec3.ZERO, mob.bounds(), false);
        assertNull(DungeonDebuffTracker.uniqueTarget(new Vec3(0, 1, 0), List.of(mob, overlap)));
        var dragon = new DungeonDebuffTracker.Target(a, new Vec3(6, 0, 0), mob.bounds(), true);
        var other = new DungeonDebuffTracker.Target(b, new Vec3(-6, 0, 0), mob.bounds(), true);
        assertEquals(a, DungeonDebuffTracker.uniqueTarget(Vec3.ZERO, List.of(dragon)));
        assertNull(DungeonDebuffTracker.uniqueTarget(Vec3.ZERO, List.of(dragon, other)));
        assertNull(DungeonDebuffTracker.uniqueTarget(new Vec3(15, 0, 0), List.of(dragon)));
    }

    @Test
    public void locationAndMemoryBoundsDoNotInventDragonState() {
        assertEquals("Purple", DungeonDebuffTracker.dragonName(new Vec3(56, 14, 125)));
        assertNull(DungeonDebuffTracker.dragonName(new Vec3(56, 100, 125)));
        assertNull(DungeonDebuffTracker.dragonName(Vec3.ZERO));
        var tracker = new DungeonDebuffTracker();
        for (int i = 0; i < 300; i++) tracker.spray(new UUID(0, i), 0);
        assertEquals(DungeonDebuffTracker.MAX_MARKERS, tracker.highlighted().size());
        UUID uuid = UUID.randomUUID();
        var dragon = tracker.spawn(uuid, "Red", new Vec3(32, 6, 59));
        for (int i = 0; i < 300; i++) tracker.arrowHit();
        for (int i = 0; i < 3; i++) tracker.advance();
        assertEquals(128, dragon.arrows.count);
        assertTrue(dragon.arrows.describe().contains("first 64"));
        assertFalse(dragon.arrows.describe().contains("Infinity"));
    }

    @Test
    public void respawnOfSameColorResetsArrowsSprayAndHudTime() {
        var tracker = new DungeonDebuffTracker();
        UUID first = UUID.randomUUID();
        tracker.spawn(first, "Red", new Vec3(32, 6, 59));
        tracker.arrowHit();
        tracker.spray(first, 0);
        for (int i = 0; i < 3; i++) tracker.advance();
        tracker.end(first, true);
        var lines = DragonDebuffHud.lines(tracker.latestDragons(), tracker.tick());
        assertTrue(lines.stream().anyMatch(line -> line.getString().equals("Red: Time: 0.15s | Arrows: 1 | Sprayed: 0t")));
        UUID nextId = UUID.randomUUID();
        var next = tracker.spawn(nextId, "Red", new Vec3(32, 6, 59));
        tracker.spawn(UUID.randomUUID(), "Blue");
        assertEquals(2, tracker.latestDragons().size());
        assertEquals(0, tracker.latestDragons().getFirst().arrows.count);
        assertFalse(tracker.latestDragons().getFirst().ended);
        assertEquals(-1, next.sprayTick);
        assertEquals(3, next.spawnTick);
        assertFalse(next.resultAnnounced);
        tracker.advance();
        assertTrue(tracker.spray(nextId, tracker.tick()));
        tracker.arrowHit();
        for (int i = 0; i < 2; i++) tracker.advance();
        assertEquals(1, next.arrows.count);
        assertEquals(1, next.sprayTick);
    }

    @Test
    public void iceBoxSizeExpandsEveryDimensionAroundItsCenterWithoutMovingTheMob() {
        var vanilla = new AABB(-10, 20, 30, -9, 22, 31);
        var expanded = IceSprayHighlightRenderer.scaledBounds(vanilla, 200);
        assertEquals(vanilla.getCenter(), expanded.getCenter());
        assertEquals(2, expanded.getXsize(), 0.00001);
        assertEquals(4, expanded.getYsize(), 0.00001);
        assertEquals(2, expanded.getZsize(), 0.00001);
        assertEquals(1, vanilla.getXsize(), 0.00001);
        assertEquals(1.5, IceSprayHighlightRenderer.scaledBounds(vanilla, 150).getXsize(), 0.00001);
        assertEquals(vanilla.inflate(0.02), IceSprayHighlightRenderer.scaledBounds(vanilla, 100));
        assertEquals(expanded, IceSprayHighlightRenderer.scaledBounds(vanilla, 900));
        assertEquals(vanilla.inflate(0.02), IceSprayHighlightRenderer.scaledBounds(vanilla, -5));
    }

    @Test
    public void pairedSpawnsChoosePurpleAtItsStatueRegardlessOfPacketOrderOrLaterMovement() {
        for (boolean purpleFirst : new boolean[] {false, true}) {
            var tracker = new DungeonDebuffTracker();
            var first = tracker.spawn(UUID.randomUUID(), purpleFirst ? "Purple" : "Orange", new Vec3(56, 12, 120));
            tracker.advance();
            var second = tracker.spawn(UUID.randomUUID(), purpleFirst ? "Orange" : "Purple", new Vec3(80, 100, 56));
            assertTrue(tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).isEmpty());
            assertEquals(2, tracker.displayedDragons(DragonDebuffScope.ALL_DRAGONS).size());
            tracker.advance();
            tracker.advance();
            var selected = tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).getFirst();
            assertEquals("Purple", selected.name);
            assertTrue(selected.shownIn(DragonDebuffScope.NEAREST_STATUE));
            var other = purpleFirst ? second : first;
            assertFalse(other.shownIn(DragonDebuffScope.NEAREST_STATUE));
            assertTrue(other.shownIn(DragonDebuffScope.ALL_DRAGONS));
            for (int i = 0; i < 20; i++) tracker.advance();
            assertSame(selected, tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).getFirst());
        }
    }

    @Test
    public void nextWaveSelectsAgainAndDeadSelectedResultDoesNotSwitchToTheOtherDragon() {
        var tracker = new DungeonDebuffTracker();
        UUID purpleId = UUID.randomUUID();
        var purple = tracker.spawn(purpleId, "Purple", new Vec3(56, 14, 120));
        var orange = tracker.spawn(UUID.randomUUID(), "Orange", new Vec3(56, 14, 120));
        tracker.spray(purpleId, 0);
        tracker.arrowHit();
        tracker.end(purpleId, true); // Even a kill within the grouping window still gets the correct report.
        for (int i = 0; i < 3; i++) tracker.advance();
        assertTrue(purple.shownIn(DragonDebuffScope.NEAREST_STATUE));
        assertEquals(0, purple.sprayTick);
        assertEquals(1, purple.arrows.count);
        assertFalse(orange.shownIn(DragonDebuffScope.NEAREST_STATUE));
        assertSame(purple, tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).getFirst());
        var next = tracker.spawn(UUID.randomUUID(), "Red", new Vec3(32, 14, 59));
        for (int i = 0; i < 3; i++) tracker.advance();
        assertSame(next, tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).getFirst());
        assertTrue(purple.shownIn(DragonDebuffScope.NEAREST_STATUE));
        tracker.clearDragons();
        assertTrue(tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).isEmpty());
    }

    @Test
    public void nearestSelectionUsesOnlyNewSpawnsAndRequiresKnownPlayerPosition() {
        var tracker = new DungeonDebuffTracker();
        var single = tracker.spawn(UUID.randomUUID(), "Orange", new Vec3(32, 200, 59));
        for (int i = 0; i < 3; i++) tracker.advance();
        assertSame(single, tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).getFirst());
        tracker.spawn(UUID.randomUUID(), "Purple", null);
        for (int i = 0; i < 3; i++) tracker.advance();
        assertTrue(tracker.displayedDragons(DragonDebuffScope.NEAREST_STATUE).isEmpty());
        assertEquals(2, tracker.displayedDragons(DragonDebuffScope.ALL_DRAGONS).size());
        tracker.reset();
        assertTrue(tracker.displayedDragons(DragonDebuffScope.ALL_DRAGONS).isEmpty());
    }
}
