package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class DungeonRuntimeRegressionTest {
    @Test public void firstMapObservationIsNotSkippedBySentinelOverflow() {
        assertTrue(DungeonScanSchedule.due(1, Long.MIN_VALUE, 5));
        assertFalse(DungeonScanSchedule.due(4, 1, 5));
        assertTrue(DungeonScanSchedule.due(6, 1, 5));
        assertTrue(DungeonScanSchedule.due(0, 10, 5));
    }

    @Test public void partialScanBatchesEventuallyVisitEveryPoint() {
        DungeonScanSchedule schedule = new DungeonScanSchedule(121);
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < 121; i++) {
            visited.add(schedule.cursor());
            schedule.advance(1); // A deadline may expire after just one point.
        }
        assertEquals(121, visited.size());
        assertEquals(0, schedule.cursor());
        schedule.advance(8);
        schedule.reset();
        assertEquals(0, schedule.cursor());
    }

    @Test public void allDisabledKeepsExpensiveWorkOff() {
        assertEquals(new DungeonWorkload(false, false, false, false), DungeonWorkload.from(config()));
    }

    @Test public void mapAndBloodRushCanEachRequestTheirOwnData() {
        for (boolean map : List.of(true, false)) {
            KungConfig config = config();
            if (map) config.dungeon.setEnabled(true);
            else config.bloodRush.setEnabled(true);
            assertTrue(DungeonWorkload.from(config).rooms());
            assertTrue(DungeonWorkload.from(config).mapData());
        }
    }

    @Test public void splitsDoNotNeedWorldScans() {
        KungConfig config = config();
        config.splits.setEnabled(true);
        assertFalse(DungeonWorkload.from(config).rooms());
        assertTrue(DungeonWorkload.from(config).players());
    }

    @Test public void togglingLastConsumerSupportsMidRunActivationAndRelease() {
        KungConfig config = config();
        config.dungeon.setEnabled(true);
        assertTrue(DungeonWorkload.from(config).rooms());
        config.bloodRush.setEnabled(true);
        config.dungeon.setEnabled(false);
        assertTrue(DungeonWorkload.from(config).rooms());
        config.bloodRush.setEnabled(false);
        assertFalse(DungeonWorkload.from(config).rooms());
        config.dungeon.setEnabled(true);
        assertTrue(DungeonWorkload.from(config).rooms());
    }

    @Test public void changedStableHashUpdatesCellEvenWhenRawHashIsUnchanged() {
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        snapshot.addScan(0, 0, 5, 5, List.of(room(101, 1001)));
        long revision = snapshot.scanRevision();
        snapshot.addScan(1, 1, 5, 5, List.of(room(101, 1002)));
        assertEquals(1002, snapshot.pointAt(0, 0).point().stableCoreHash());
        assertTrue(snapshot.scanRevision() > revision);
        assertEquals(1001, snapshot.initialRoomPointAt(0, 0).point().stableCoreHash());
    }

    @Test public void midRunActivationUsesRememberedEntranceInsteadOfCurrentRoom() {
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        snapshot.observeStartRoom(1, 5);
        snapshot.observePlayerGrid(3, 2);
        assertEquals(new DungeonMapSnapshot.GridKey(2, 10), snapshot.startRoom());
        snapshot.reset();
        snapshot.observeStartRoom(1, 5);
        snapshot.observePlayerGrid(4, 4);
        assertEquals(new DungeonMapSnapshot.GridKey(2, 10), snapshot.startRoom());
    }

    @Test public void roomTemplateCannotSwallowAnObservedDoor() {
        var hint = new DungeonKnownRoomCatalog.KnownCoreHint("Repeated room", RoomType.NORMAL, 3);
        var template = new DungeonKnownRoomCatalog.RoomTemplate("Repeated room", RoomType.NORMAL, 3, 0,
            false, 1, List.of(new DungeonKnownRoomCatalog.TemplateComponent(0, 0, 101, 1001),
                new DungeonKnownRoomCatalog.TemplateComponent(1, 0, 102, 1002)));
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        snapshot.addScan(0, 0, 5, 5, List.of(room(101, 1001),
            new DungeonScanPoint(2, 0, -153, -185, DungeonScanPointKind.ROOM, true, 102, 1002, 0, DungeonDoorKind.NONE),
            new DungeonScanPoint(1, 0, -169, -185, DungeonScanPointKind.DOOR, true, 0, 0, 42, DungeonDoorKind.WITHER)));
        var matches = DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), java.util.Map.of(101, hint, 102, hint));
        assertEquals(2, matches.size());
        assertTrue(matches.stream().allMatch(match -> match.components().size() == 1));
    }

    @Test public void cachedRoomMatchesSurvivePlayerMovementButNotDoorChangesOrReset() {
        DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
        var repository = KnownDungeonRoomRepository.INSTANCE;
        snapshot.addScan(0, 0, 5, 5, List.of(room(101, 1001)));
        var first = repository.matchKnownRooms(snapshot);
        long revision = snapshot.scanRevision();
        snapshot.observePlayerGrid(4, 5);
        snapshot.observeRoomClearState(0, 0, DungeonMapClearState.COMPLETED);
        assertEquals(revision, snapshot.scanRevision());
        assertSame(first, repository.matchKnownRooms(snapshot));
        snapshot.addScan(1, 1, 4, 5, List.of(new DungeonScanPoint(1, 0, -169, -185,
            DungeonScanPointKind.DOOR, true, 0, 0, 42, DungeonDoorKind.WITHER)));
        assertTrue(snapshot.scanRevision() > revision);
        snapshot.reset();
        assertTrue(snapshot.scanRevision() > revision + 1);
    }

    @Test public void preloadUsesBothHashesAndRejectsConflictingRooms() {
        DungeonPreloadHints hints = new DungeonPreloadHints();
        var first = new DungeonKnownRoomCatalog.KnownCoreHint("First", RoomType.NORMAL, 1);
        var second = new DungeonKnownRoomCatalog.KnownCoreHint("Second", RoomType.NORMAL, 2);
        assertTrue(hints.observe(123, 456, first));
        assertEquals(first, hints.get(123, 456));
        assertNull(hints.get(123, 457));
        assertTrue(hints.observe(123, 456, second));
        assertNull(hints.get(123, 456));
        assertFalse(hints.observe(123, 456, first));
        assertFalse(hints.observe(DungeonRoomClassifier.EMPTY_CORE_HASH, 456, first));
        assertFalse(hints.observe(123, 0, first));
    }

    @Test public void sameCellTransitionRecognizesPreloadOnTheNextSnapshot() {
        DungeonKnownRoomCatalog.reload();
        try {
            var template = DungeonKnownRoomCatalog.loadTemplates().stream()
                .filter(t -> t.type() == RoomType.NORMAL && !t.components().isEmpty()).findFirst().orElseThrow();
            var component = template.components().getFirst();
            DungeonMapSnapshot snapshot = new DungeonMapSnapshot();
            snapshot.addScan(0, 0, 5, 5, List.of(room(123456789, 987654321)));
            snapshot.addScan(1, 1, 5, 5, List.of(room(component.coreHash(), component.stableCoreHash())));
            DungeonMapSnapshot nextRun = new DungeonMapSnapshot();
            nextRun.addScan(0, 0, 5, 5, List.of(room(123456789, 987654321)));
            var match = DungeonKnownRoomCatalog.matchKnownRooms(nextRun).stream()
                .filter(m -> m.contains(0, 0)).findFirst().orElseThrow();
            assertEquals(template.name(), match.template().name());
            for (boolean mapVisible : List.of(true, false)) {
                var visibleRun = new DungeonMapSnapshot();
                visibleRun.addScan(0, 0, 5, 5, List.of(room(123456789, 987654321)));
                var repository = KnownDungeonRoomRepository.INSTANCE;
                var prediction = repository.matchKnownRooms(visibleRun);
                assertFalse(prediction.isEmpty());
                if (mapVisible) visibleRun.observeMapVisibleRoom(0, 0);
                else visibleRun.addScan(1, 1, 5, 5, List.of(new DungeonScanPoint(0, 0, -185, -185,
                    DungeonScanPointKind.ROOM, true, 123456789, 987654321, 0, DungeonDoorKind.NONE, true)));
                assertNotSame(prediction, repository.matchKnownRooms(visibleRun));
                assertTrue(repository.matchKnownRooms(visibleRun).isEmpty());
                assertNull(DungeonLiveMapWriter.MatchRenderPlan.from(visibleRun).hintAt(0, 0));
                visibleRun.addScan(2, 2, 5, 5, List.of(room(component.coreHash(), component.stableCoreHash())));
                assertEquals(template.name(), repository.matchKnownRooms(visibleRun).getFirst().template().name());
            }
        } finally {
            DungeonKnownRoomCatalog.reload();
        }
    }

    @Test public void lockedDoorDoesNotNeedAnOpenSkyBlockAboveItsFrame() {
        assertEquals(DungeonDoorKind.WITHER,
            DungeonScanUtils.classifyDoorColumn(0, 4, 0, true, false, DungeonDoorKind.NONE));
        assertEquals(DungeonDoorKind.BLOOD,
            DungeonScanUtils.classifyDoorColumn(0, 0, 4, true, false, DungeonDoorKind.NONE));
        assertEquals(DungeonDoorKind.OPEN,
            DungeonScanUtils.classifyDoorColumn(4, 0, 0, true, false, DungeonDoorKind.WITHER));
        assertEquals(DungeonDoorKind.NONE,
            DungeonScanUtils.classifyDoorColumn(4, 0, 0, true, false, DungeonDoorKind.NONE));
    }

    @Test public void fullRoomLoadingRequiresEveryIntersectingChunkIncludingTheFarCorner() {
        assertFalse(DungeonScanUtils.isRoomFullyLoaded((x, z) -> x == -12 && z == -12, -185, -185));
        assertFalse(DungeonScanUtils.isRoomFullyLoaded((x, z) -> x != -11 || z != -11, -185, -185));
        Set<String> checked = new HashSet<>();
        assertTrue(DungeonScanUtils.isRoomFullyLoaded((x, z) -> checked.add(x + "," + z), -185, -185));
        assertEquals(Set.of("-13,-13", "-13,-12", "-13,-11", "-12,-13", "-12,-12", "-12,-11",
            "-11,-13", "-11,-12", "-11,-11"), checked);
    }

    @Test public void visibleRoomsDropBundledPreloadsAndAcceptCurrentUnknownHashes() {
        int preload = -1783845896;
        assertNotNull(DungeonKnownRoomCatalog.knownCoreHint(preload));
        assertFalse(DungeonKnownRoomCatalog.isStableKnownCoreHash(preload));
        for (boolean mapVisible : List.of(true, false)) {
            var snapshot = new DungeonMapSnapshot();
            snapshot.addScan(0, 0, 5, 5, List.of(room(preload, 987654321)));
            assertFalse(KnownDungeonRoomRepository.INSTANCE.matchKnownRooms(snapshot).isEmpty());
            if (mapVisible) snapshot.observeMapVisibleRoom(0, 0);
            else snapshot.addScan(1, 1, 5, 5, List.of(new DungeonScanPoint(0, 0, -185, -185,
                DungeonScanPointKind.ROOM, true, preload, 987654321, 0, DungeonDoorKind.NONE, true)));
            var visible = DungeonLiveMapWriter.MatchRenderPlan.from(snapshot);
            assertTrue(visible.matches().isEmpty());
            assertNull("Rendering must not reintroduce a bundled preload", visible.hintAt(0, 0));
            snapshot.addScan(2, 2, 5, 5, List.of(room(123456789, 987654321)));
            assertEquals(123456789, snapshot.pointAt(0, 0).point().coreHash());
            assertFalse("Chunk loss must not restore prediction for an already observed room",
                snapshot.allowsRoomPrediction(0, 0));
            snapshot.reset();
            assertTrue(snapshot.allowsRoomPrediction(0, 0));
        }
    }

    @Test public void mapShapeOverridesEvenAnExactTemplateAndRefreshesCachedMatches() {
        var template = new DungeonKnownRoomCatalog.RoomTemplate("Repeated room", RoomType.NORMAL, 3, 0,
            false, 1, List.of(new DungeonKnownRoomCatalog.TemplateComponent(0, 0, 101, 1001),
                new DungeonKnownRoomCatalog.TemplateComponent(1, 0, 102, 1002)));
        var hint = new DungeonKnownRoomCatalog.KnownCoreHint("Repeated room", RoomType.NORMAL, 3);
        var hints = java.util.Map.of(101, hint, 102, hint);
        var snapshot = new DungeonMapSnapshot();
        snapshot.addScan(0, 0, 5, 5, List.of(room(101, 1001),
            new DungeonScanPoint(2, 0, -153, -185, DungeonScanPointKind.ROOM, true, 102, 1002, 0, DungeonDoorKind.NONE)));
        assertEquals(1, DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), hints).size());
        var known = DungeonKnownRoomCatalog.loadTemplates().getFirst().components().getFirst();
        snapshot.addScan(1, 1, 5, 5, List.of(new DungeonScanPoint(8, 8, -57, -57,
            DungeonScanPointKind.ROOM, true, known.coreHash(), known.stableCoreHash(), 0, DungeonDoorKind.NONE)));
        var repository = KnownDungeonRoomRepository.INSTANCE;
        var cached = repository.matchKnownRooms(snapshot);
        snapshot.observeMapVisibleRoom(0, 0);
        snapshot.observeMapVisibleRoom(1, 0);
        assertNotSame(cached, repository.matchKnownRooms(snapshot));
        assertEquals(2, DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), hints).size());
        var separated = repository.matchKnownRooms(snapshot);
        snapshot.observeMapOpenDoor(1, 0);
        assertNotSame(separated, repository.matchKnownRooms(snapshot));
        assertEquals(2, DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), hints).size());
        var external = repository.matchKnownRooms(snapshot);
        snapshot.observeMapRoomConnection(1, 0);
        assertNotSame(external, repository.matchKnownRooms(snapshot));
        assertEquals(1, DungeonKnownRoomCatalog.matchKnownRooms(snapshot, List.of(template), hints).size());
        var connected = repository.matchKnownRooms(snapshot);
        snapshot.observeMapRoomConnection(1, 0);
        snapshot.observeMapVisibleRoom(0, 0);
        assertSame(connected, repository.matchKnownRooms(snapshot));
    }

    private static DungeonScanPoint room(int core, int stable) {
        return new DungeonScanPoint(0, 0, -185, -185, DungeonScanPointKind.ROOM, true,
            core, stable, 0, DungeonDoorKind.NONE);
    }

    private static KungConfig config() {
        KungConfig config = new Gson().fromJson("{}", KungConfig.class);
        config.dungeon.onChange(() -> {});
        config.bloodRush.onChange(() -> {});
        config.splits.onChange(() -> {});
        return config;
    }
}
