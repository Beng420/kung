package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.dungeon.DungeonLiveMapWriter.CellKey;
import com.github.beng420.kung.feature.dungeon.DungeonLiveMapWriter.MatchRenderPlan;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class DungeonScoreReadinessTest {
    private DungeonConfig previousConfig;
    private static final CellKey SPECIAL = new CellKey(3, 0);

    @Before public void isolateConfig() {
        previousConfig = KungConfig.get().dungeon;
        KungConfig.get().dungeon = new DungeonConfig();
    }

    @After public void restoreConfig() {
        KungConfig.get().dungeon = previousConfig;
    }

    private static DungeonRunStats readySecrets() {
        var stats = new DungeonRunStats();
        stats.configureForFloor(7, true);
        stats.observeTabLine(null, "Completed Rooms: 34/36", null);
        stats.observeTabLine(null, "Opened Rooms: 35", null);
        stats.observeTabLine(null, "Crypts: 5", null);
        stats.observeStatLine(null, "Secrets: 35/40");
        stats.observeTabLine(null, "Secrets Found: 87.5%", null);
        stats.observeMessage(null, "[BOSS] The Watcher: You have proven yourself. You may pass.", 100L);
        return stats;
    }

    @Test public void discoveredPuzzleKeepsItsRoomAndTenSkillPointsOutstanding() {
        var stats = readySecrets();
        var open = plan(RoomType.PUZZLE, false, false);
        assertEquals(285, stats.score(open, 40));
        stats.observeTabLine(null, "Puzzles: (1)", null);
        stats.observeTabLine(null, "Water Board: [✦]", null);
        assertEquals(285, stats.score(open, 40)); // Map and tab are the same outstanding puzzle.
        stats.observeTabLine(null, "Water Board: [✔]", null);
        stats.observeTabLine(null, "Completed Rooms: 35/36", null);
        assertEquals(300, stats.score(open, 40)); // Tab can finish the puzzle before the map refresh.
        assertEquals(300, stats.score(plan(RoomType.PUZZLE, true, true), 40));
    }

    @Test public void tabTracksOpenPuzzlesEvenWhenTheirMapTypeIsNotKnown() {
        var stats = readySecrets();
        stats.observeTabLine(null, "Completed Rooms: 36/36", null);
        stats.observeTabLine(null, "Puzzles: (1)", null);
        stats.observeTabLine(null, "Blaze: [✦]", null);
        assertEquals(290, stats.score(plan(RoomType.UNKNOWN, true, true), 40));
        stats.observeTabLine(null, "Blaze: [✦]", null);
        stats.observeTabLine(null, "Team Deaths: 0", null);
        assertEquals(290, stats.score(plan(RoomType.UNKNOWN, true, true), 40));
        stats.observeTabLine(null, "Blaze: [✓]", null);
        assertEquals(300, stats.score(plan(RoomType.UNKNOWN, true, true), 40));
    }

    @Test public void allSecretsCannotHideTheOutstandingPuzzlePenalty() {
        var stats = readySecrets();
        stats.observeStatLine(null, "Secrets: 40/40");
        stats.observeTabLine(null, "Secrets Found: 100.0%", null);
        stats.observeTabLine(null, "Puzzles: (1)", null);
        stats.observeTabLine(null, "Water Board: [✦]", null);
        // Previously 302: all secrets and bonuses crossed 300 without the puzzle's ten-point penalty.
        assertEquals(292, stats.score(plan(RoomType.PUZZLE, false, false), 40));
        stats.observeTabLine(null, "Water Board: [✔]", null);
        stats.observeTabLine(null, "Completed Rooms: 35/36", null);
        assertEquals(307, stats.score(plan(RoomType.PUZZLE, true, true), 40));
    }

    @Test public void visitingYellowTrapOrNormalNeverCompletesTheirClear() {
        for (var type : List.of(RoomType.YELLOW, RoomType.TRAP, RoomType.NORMAL)) {
            var stats = readySecrets();
            assertEquals(type.name(), 295, stats.score(plan(type, false, false), 40));
            stats.observeTabLine(null, "Completed Rooms: 35/36", null);
            assertEquals(type.name(), 300, stats.score(plan(type, true, false), 40));
        }
    }

    @Test public void bloodAndBossProjectionCannotCoverAnOpenOrdinaryRoom() {
        var stats = new DungeonRunStats();
        stats.configureForFloor(7, true);
        stats.observeTabLine(null, "Completed Rooms: 34/36", null);
        stats.observeTabLine(null, "Crypts: 5", null);
        stats.observeTabLine(null, "Secrets Found: 87.5%", null);
        assertEquals(295, stats.score(plan(RoomType.TRAP, false, false), 40));
        assertEquals(300, stats.score(plan(RoomType.TRAP, true, false), 40));
    }

    @Test public void aHighServerClearPercentageCannotBypassAnOpenPuzzle() {
        var stats = readySecrets();
        stats.observeScoreboardLine(null, "§aCleared: §f100% §7(300)");
        assertEquals(100.0, stats.clearedPercent(), 0.0);
        assertEquals(285, stats.score(plan(RoomType.PUZZLE, false, false), 40));
        stats.observeScoreboardLine(null, "Party > Alice: Cleared: 1% (100)");
        assertEquals(100.0, stats.clearedPercent(), 0.0);
    }

    @Test public void failedPuzzleIsChargedOnceAndDoesNotBecomeSolvedByARedMapCross() {
        var stats = readySecrets();
        stats.observeTabLine(null, "Puzzles: (1)", null);
        stats.observeTabLine(null, "Blaze: [✖] (Alice)", null);
        stats.observeTabLine(null, "Blaze: [✖] (Alice)", null);
        assertEquals(285, stats.score(plan(RoomType.PUZZLE, true, false), 40));
        stats.observeStatLine(null, "Team Score: 287 (S)");
        assertEquals(287, stats.score(plan(RoomType.PUZZLE, true, false), 40));
    }

    @Test public void roundedSidebarDenominatorDoesNotTreatTheScannedGridAsTheServerTotal() {
        var stats = new DungeonRunStats();
        stats.configureForFloor(7, true);
        stats.observeTabLine(null, "Completed Rooms: 34", null);
        stats.observeTabLine(null, "Opened Rooms: 34", null);
        stats.observeTabLine(null, "Secrets Found: 87.5%", null);
        stats.observeTabLine(null, "Crypts: 5", null);
        stats.observeScoreboardLine(null, "Cleared: 97% (295)");
        stats.observeMessage(null, "[BOSS] The Watcher: You have proven yourself. You may pass.", 100L);
        assertEquals(300, stats.score(null, 40)); // 34/35 plus the boss, not an invented 36-cell total.
    }

    @Test public void completedRoomCounterAloneDoesNotInventAFullDungeon() {
        var stats = new DungeonRunStats();
        stats.observeTabLine(null, "Completed Rooms: 3", null);
        stats.observeTabLine(null, "Secrets Found: 87.5%", null);
        stats.observeTabLine(null, "Crypts: 5", null);
        assertTrue(stats.score(null, 40) < 300);
    }

    @Test public void mapFallbackCreditsWhiteClearedCellsIncludingEveryCellOfLargeRooms() {
        var stats = new DungeonRunStats();
        stats.configureForFloor(7, true);
        stats.observeTabLine(null, "Secrets Found: 87.5%", null);
        stats.observeTabLine(null, "Crypts: 5", null);
        var base = plan(RoomType.NORMAL, true, false);
        var owners = new HashMap<>(base.roomOwners());
        for (int x = 0; x < 4; x++) owners.put(new CellKey(x, 1), "large-room");
        var large = new MatchRenderPlan(base.matches(), base.predictedRooms(), base.matchedRoomCells(), base.internalDoors(),
            base.externalDoors(), base.roomTypes(), Map.copyOf(owners), base.hints(), base.remoteRoomCells(),
            base.remoteRoomProgress(), base.visitedRooms(), base.clearedRooms(), base.completedRooms(),
            base.openedSpecialDoorCells(), base.fairyEntranceDoor(), base.bloodRushPath());
        assertEquals(300, stats.score(large, 40));
        assertEquals(300, stats.score(large, 40));
        stats.reset();
        assertTrue(stats.score(null, 40) < 300);
    }

    private static MatchRenderPlan plan(RoomType specialType, boolean specialCleared, boolean specialCompleted) {
        Map<CellKey, String> owners = new HashMap<>();
        Map<CellKey, RoomType> types = new HashMap<>();
        Set<CellKey> cleared = new HashSet<>();
        Set<CellKey> completed = new HashSet<>();
        for (int z = 0; z < 6; z++) for (int x = 0; x < 6; x++) {
            var cell = new CellKey(x, z);
            owners.put(cell, "room:" + x + "," + z);
            types.put(cell, RoomType.NORMAL);
            cleared.add(cell);
        }
        types.put(new CellKey(0, 0), RoomType.START);
        types.put(new CellKey(1, 0), RoomType.FAIRY);
        types.put(new CellKey(2, 0), RoomType.BLOOD);
        // The server automatically credits special entrance cells without ordinary checkmarks.
        cleared.remove(new CellKey(0, 0));
        cleared.remove(new CellKey(1, 0));
        types.put(SPECIAL, specialType);
        if (!specialCleared) cleared.remove(SPECIAL);
        if (specialCompleted) completed.add(SPECIAL);
        return new MatchRenderPlan(List.of(), List.of(), Set.of(), Set.of(), Map.of(), Map.copyOf(types), Map.copyOf(owners),
            Map.of(), Set.of(), Map.of(), Set.copyOf(owners.keySet()), Set.copyOf(cleared), Set.copyOf(completed),
            Set.of(), null, List.of());
    }
}
