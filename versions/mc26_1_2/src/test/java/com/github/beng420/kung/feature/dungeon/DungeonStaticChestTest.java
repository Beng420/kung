package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import org.junit.Test;

public final class DungeonStaticChestTest {
    @Test public void blockTransformsRoundTripAtNegativeCoordinatesAndKeepAbsoluteHeight() {
        for (int rotation = 0; rotation < 4; rotation++) {
            var transform = new DungeonRoomTransform(-200, -136, 95, 31, rotation);
            for (var pos : List.of(new BlockPos(0, 12, 0), new BlockPos(94, 70, 30), new BlockPos(45, 100, 8))) {
                assertEquals(pos, transform.local(transform.world(pos)));
                assertEquals(pos.getY(), transform.world(pos).getY());
            }
        }
        int[][] expected = {{-194, -191}, {-179, -194}, {-176, -179}, {-191, -176}};
        for (int rotation = 0; rotation < 4; rotation++) {
            var pos = new DungeonRoomTransform(-200, -200, 31, 31, rotation).world(new BlockPos(6, 70, 9));
            assertEquals(new BlockPos(expected[rotation][0], 70, expected[rotation][1]), pos);
        }
    }

    @Test public void fixedChestMatchesEveryRotationAndTranslationButAnExtraChestDoesNot() {
        var pattern = pattern(false);
        for (int origin : new int[] {-200, -104, -40}) for (int rotation = 0; rotation < 4; rotation++) {
            var transform = new DungeonRoomTransform(origin, -168, 31, 31, rotation);
            var room = room(pattern, transform);
            var chest = transform.world(pattern.chest().blockPos());
            var result = pattern.resolve(room, world(pattern, transform));
            assertEquals(Set.of(chest), result.fixedPositions());
            assertEquals(1, result.confirmedRotations());
            assertFalse(result.fixedPositions().contains(chest.above()));
            assertFalse(result.fixedPositions().contains(chest.east()));
        }
    }

    @Test public void buttonsSizedRoomKeepsEachCellsVariantBindingWhenRotated() {
        var cells = List.of(cell(0, 0, 10), cell(1, 0, 11), cell(0, 1, 12), cell(1, 1, 13));
        var base = pattern(false);
        var buttons = new DungeonStaticChestPattern("buttons", cells, new DungeonStaticChestPattern.Point(42, 70, 55), base.probes());
        for (int rotation = 0; rotation < 4; rotation++) {
            var transform = new DungeonRoomTransform(-168, -104, 63, 63, rotation);
            var room = room(buttons, transform);
            var result = buttons.resolve(room, world(buttons, transform));
            assertEquals(Set.of(transform.world(buttons.chest().blockPos())), result.fixedPositions());
            var changed = new ArrayList<>(room.cells());
            var first = changed.getFirst();
            changed.set(0, cell(first.x(), first.z(), 999));
            assertTrue(buttons.resolve(new DungeonStaticChestPattern.Room("buttons", -168, -104, changed),
                world(buttons, transform)).fixedPositions().isEmpty());
        }
    }

    @Test public void sameNameWithDifferentHashesIsNeverExcluded() {
        var pattern = pattern(false);
        var room = new DungeonStaticChestPattern.Room(pattern.room(), -200, -200, List.of(cell(0, 0, 999)));
        assertTrue(pattern.resolve(room, pos -> "minecraft:stone").fixedPositions().isEmpty());
        assertTrue(pattern.transforms(room).isEmpty());
    }

    @Test public void confirmedPreRunAndRunHashesCanShareTheSameTemplate() {
        var original = pattern(false);
        var pattern = new DungeonStaticChestPattern(original.room(),
            List.of(new DungeonStaticChestPattern.Cell(0, 0, Set.of(123, 456), Set.of(789))), original.chest(), original.probes());
        var transform = new DungeonRoomTransform(-168, -136, 31, 31, 3);
        for (int core : new int[] {123, 456}) {
            var room = new DungeonStaticChestPattern.Room(pattern.room(), -168, -136, List.of(cell(0, 0, core)));
            assertEquals(Set.of(transform.world(pattern.chest().blockPos())), pattern.resolve(room, world(pattern, transform)).fixedPositions());
        }
    }

    @Test public void missingChunksPreserveOtherPossibleRotationsInsteadOfProvingTheFirstOne() {
        var pattern = pattern(true);
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 0);
        Map<BlockPos, String> loaded = probeMap(pattern, transform);
        var incomplete = pattern.resolve(room(pattern, transform), loaded::get);
        assertEquals(4, incomplete.possibleRotations());
        assertEquals(1, incomplete.confirmedRotations());
        assertTrue(incomplete.fixedPositions().isEmpty());
        assertEquals(Set.of(transform.world(pattern.chest().blockPos())),
            pattern.resolve(room(pattern, transform), pos -> loaded.getOrDefault(pos, "minecraft:air")).fixedPositions());
    }

    @Test public void symmetricRoomDoesNotExcludeTheUnionOfAllPossibleChestPositions() {
        var pattern = pattern(true);
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 0);
        var ambiguous = pattern.resolve(room(pattern, transform), pos -> "minecraft:stone");
        assertEquals(4, ambiguous.confirmedRotations());
        assertTrue(ambiguous.fixedPositions().isEmpty());
        var central = new DungeonStaticChestPattern(pattern.room(), pattern.cells(),
            new DungeonStaticChestPattern.Point(15, 70, 15), pattern.probes());
        assertEquals(Set.of(new BlockPos(-185, 70, -185)), central.resolve(room(pattern, transform),
            pos -> "minecraft:stone").fixedPositions());
    }

    @Test public void structuralMismatchAndAbsentWorldCannotEstablishAnExclusion() {
        var pattern = pattern(false);
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 0);
        assertTrue(pattern.resolve(room(pattern, transform), pos -> null).fixedPositions().isEmpty());
        assertTrue(pattern.resolve(room(pattern, transform), pos -> "minecraft:air").fixedPositions().isEmpty());
        var blocks = probeMap(pattern, transform);
        blocks.put(transform.world(pattern.probes().getFirst().pos().blockPos()), "minecraft:gold_block");
        assertTrue(pattern.resolve(room(pattern, transform), pos -> blocks.getOrDefault(pos, "minecraft:air")).fixedPositions().isEmpty());
    }

    @Test public void capturedBuildingProbesResolveTheRoomWithoutUsingTheChestAsAnAnchor() {
        var room = new DungeonStaticChestPattern.Room("testroom", -200, -200, List.of(cell(0, 0, 123)));
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 0);
        var chest = new BlockPos(-194, 70, -191);
        Function<BlockPos, String> blocks = pos -> {
            if (pos.equals(chest)) return "minecraft:trapped_chest";
            return Math.floorMod(pos.getX() + pos.getZ() * 7 + pos.getY() * 3, 11) < 3 ? "minecraft:obsidian" : "minecraft:stone";
        };
        var pattern = DungeonMimicStaticChests.capture(room, chest, blocks);
        assertEquals(48, pattern.probes().size());
        assertEquals(Set.of(chest), pattern.resolve(room, blocks).fixedPositions());
        assertTrue(pattern.probes().stream().noneMatch(p -> p.block().contains("chest")));
        assertTrue(pattern.probes().stream().noneMatch(p -> transform.world(p.pos().blockPos()).equals(chest)));
    }

    @Test public void ambiguousCaptureAndDynamicProbesAreRejectedBeforeSaving() {
        var room = new DungeonStaticChestPattern.Room("testroom", -200, -200, List.of(cell(0, 0, 123)));
        assertThrows(IllegalArgumentException.class, () -> DungeonMimicStaticChests.capture(room,
            new BlockPos(-194, 70, -191), pos -> "minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> DungeonMimicStaticChests.capture(room,
            new BlockPos(-194, 70, -191), pos -> null));
        assertThrows(IllegalArgumentException.class, () -> new DungeonStaticChestPattern.Probe(
            new DungeonStaticChestPattern.Point(5, 70, 5), "minecraft:trapped_chest"));
    }

    @Test public void sessionCapturesExportWithoutChangingBundledDataOrSurvivingRestart() throws Exception {
        var bundled = pattern(false);
        var capture = pattern(true);
        var catalog = new DungeonStaticChestCatalog(List.of(bundled));
        assertNull(catalog.undo());
        assertFalse(catalog.add(bundled));
        assertTrue(catalog.add(capture));
        assertFalse(catalog.add(capture));
        catalog.load();
        assertEquals(1, catalog.bundledCount());
        assertEquals(1, catalog.sessionCount());
        assertEquals(List.of(capture), DungeonStaticChestCatalog.read(catalog.exportSession()));
        var restarted = new DungeonStaticChestCatalog(List.of(bundled));
        assertEquals(List.of(bundled), restarted.patterns());
        assertEquals(0, restarted.sessionCount());
        assertEquals(capture, catalog.undo());
        assertNull(catalog.undo());
        assertEquals(List.of(bundled), catalog.patterns());
        assertTrue(DungeonStaticChestCatalog.read(catalog.exportSession()).isEmpty());
    }

    @Test public void invalidBundledDataIsRejected() {
        for (String json : List.of("{broken", "null", "{\"schema\":2,\"patterns\":[]}",
            "{\"schema\":1,\"patterns\":[null]}", "{\"schema\":1,\"patterns\":[{}]}")) {
            assertThrows(IOException.class, () -> DungeonStaticChestCatalog.read(json));
        }
    }

    @Test public void capturedFixedChestsLoadFromResourcesAndCannotBeUndone() throws Exception {
        var catalog = new DungeonStaticChestCatalog();
        catalog.load();
        assertEquals(3, catalog.bundledCount());
        assertEquals(Set.of("buttons", "dueces", "redstonekey"), catalog.patterns().stream()
            .map(DungeonStaticChestPattern::room).collect(java.util.stream.Collectors.toSet()));
        for (var pattern : catalog.patterns()) {
            assertEquals(48, pattern.probes().size());
            assertEquals(pattern.room().equals("buttons") ? 4 : 1, pattern.cells().size());
            var expected = switch (pattern.room()) {
                case "buttons" -> new DungeonStaticChestPattern.Point(48, 81, 59);
                case "dueces" -> new DungeonStaticChestPattern.Point(15, 79, 12);
                case "redstonekey" -> new DungeonStaticChestPattern.Point(18, 69, 29);
                default -> throw new AssertionError(pattern.room());
            };
            assertEquals(expected, pattern.chest());
        }
        assertEquals(0, catalog.sessionCount());
        assertNull(catalog.undo());
        assertEquals(3, catalog.patterns().size());
        var restarted = new DungeonStaticChestCatalog();
        restarted.load();
        assertEquals(catalog.patterns(), restarted.patterns());
        assertTrue(DungeonStaticChestCatalog.read(catalog.exportSession()).isEmpty());
    }

    @Test public void actualBundledCapturesResolveAllRotationsAndHashAliasesButKeepExtraChests() throws Exception {
        var catalog = new DungeonStaticChestCatalog();
        catalog.load();
        for (var pattern : catalog.patterns()) for (int origin : new int[] {-200, -104}) {
            int width = DungeonStaticChestPattern.span(pattern.cells(), true);
            int depth = DungeonStaticChestPattern.span(pattern.cells(), false);
            for (int rotation = 0; rotation < 4; rotation++) for (int hashMode = 0; hashMode < 3; hashMode++) {
                var transform = new DungeonRoomTransform(origin, -168, width, depth, rotation);
                var observed = room(pattern, transform);
                var cells = new ArrayList<DungeonStaticChestPattern.Cell>();
                for (var cell : observed.cells()) {
                    var cores = cell.cores().stream().sorted().toList();
                    cells.add(new DungeonStaticChestPattern.Cell(cell.x(), cell.z(),
                        hashMode == 2 ? Set.of() : Set.of(cores.get(hashMode % cores.size())),
                        hashMode == 2 ? cell.stableCores() : Set.of()));
                }
                var room = new DungeonStaticChestPattern.Room(pattern.room(), origin, -168, cells);
                var filter = new DungeonMimicStaticChests(catalog);
                filter.prepare();
                var chest = transform.world(pattern.chest().blockPos());
                var blocks = world(pattern, transform);
                assertTrue(pattern.room() + " rotation=" + rotation + " hashMode=" + hashMode,
                    filter.check(room, chest, 0, blocks).fixed());
                assertTrue(filter.check(room, chest.east(), 0, blocks).hasPattern());
                assertFalse(filter.check(room, chest.east(), 0, blocks).fixed());
                assertFalse(filter.check(room, chest.above(), 0, blocks).fixed());
            }
        }
    }

    @Test public void bundledCapturesRemainCompatibleWithCanonicalRoomHashes() throws Exception {
        var catalog = new DungeonStaticChestCatalog();
        catalog.load();
        try (var stream = getClass().getClassLoader().getResourceAsStream("kung-dungeon-scans/known-rooms.json")) {
            assertNotNull(stream);
            var json = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            for (var pattern : catalog.patterns()) {
                boolean compatible = false;
                for (var entry : json.getAsJsonObject().getAsJsonArray("rooms")) {
                    var room = entry.getAsJsonObject();
                    String name = room.get("name").getAsString().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (!name.equals(pattern.room())) continue;
                    for (var variant : room.getAsJsonArray("variants")) {
                        var cells = new ArrayList<DungeonStaticChestPattern.Cell>();
                        for (var element : variant.getAsJsonObject().getAsJsonArray("components")) {
                            var component = element.getAsJsonObject();
                            var cores = new java.util.HashSet<Integer>();
                            var stable = new java.util.HashSet<Integer>();
                            for (var hashElement : component.getAsJsonArray("hashes")) {
                                var hash = hashElement.getAsJsonObject();
                                if (hash.has("core") && hash.get("core").getAsInt() != 0) cores.add(hash.get("core").getAsInt());
                                if (hash.has("stable") && hash.get("stable").getAsInt() != 0) stable.add(hash.get("stable").getAsInt());
                            }
                            cells.add(new DungeonStaticChestPattern.Cell(component.get("dx").getAsInt(),
                                component.get("dz").getAsInt(), cores, stable));
                        }
                        compatible |= !pattern.transforms(new DungeonStaticChestPattern.Room(pattern.room(), -200, -200, cells)).isEmpty();
                    }
                }
                assertTrue("Captured hashes must still match the shipped " + pattern.room() + " room", compatible);
            }
        }
    }

    @Test public void resolvedRoomIsCachedAcrossUnloadsButNotAcrossAnInstanceResetOrUndo() throws Exception {
        var catalog = new DungeonStaticChestCatalog(List.of());
        var pattern = pattern(false);
        catalog.add(pattern);
        var filter = new DungeonMimicStaticChests(catalog);
        filter.prepare();
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 1);
        var room = room(pattern, transform);
        var chest = transform.world(pattern.chest().blockPos());
        AtomicInteger reads = new AtomicInteger();
        var blocks = world(pattern, transform);
        Function<BlockPos, String> counted = pos -> { reads.incrementAndGet(); return blocks.apply(pos); };
        assertTrue(filter.check(room, chest, 0, counted).fixed());
        int firstReads = reads.get();
        for (int tick = 1; tick < 1000; tick++) {
            assertTrue(filter.check(room, chest, tick, pos -> { fail("Resolved room must not rescan"); return null; }).fixed());
            assertFalse(filter.check(room, chest.above(), tick, counted).fixed());
        }
        assertEquals(firstReads, reads.get());
        filter.clear();
        filter.prepare();
        assertFalse(filter.check(room, chest, 0, pos -> null).fixed());
        assertFalse(filter.check(room, chest, 1, counted).fixed());
        assertTrue(filter.check(room, chest, 20, counted).fixed());
        catalog.undo();
        filter.prepare();
        assertFalse(filter.check(room, chest, 21, counted).fixed());
        assertFalse(filter.check(room, chest, 21, counted).hasPattern());
    }

    @Test public void lateStaticResolutionRemovesFalseRoomAmbiguityAndKeepsTheActualMimic() throws Exception {
        var catalog = new DungeonStaticChestCatalog(List.of());
        var pattern = pattern(false);
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 0);
        var room = room(pattern, transform);
        var fixed = transform.world(pattern.chest().blockPos());
        var realMimic = new BlockPos(-104, 70, -104);
        var memory = new DungeonMimicChestMemory();
        memory.observe(List.of(fixed, realMimic), pos -> true);
        assertNull(memory.mapChest(pos -> pos.equals(fixed) ? "fixed-room" : "mimic-room"));
        catalog.add(pattern);
        var filter = new DungeonMimicStaticChests(catalog);
        filter.prepare();
        memory.observe(List.of(), pos -> !filter.check(room, pos, 0, world(pattern, transform)).fixed());
        assertEquals(List.of(realMimic), memory.positions());
        assertEquals(realMimic, memory.mapChest(pos -> "mimic-room"));
    }

    @Test public void blockReadBudgetDefersWorkAndEventuallyResolvesEveryChest() throws Exception {
        var catalog = new DungeonStaticChestCatalog(List.of());
        var base = pattern(false);
        List<BlockPos> targets = new ArrayList<>();
        var transform = new DungeonRoomTransform(-200, -200, 31, 31, 0);
        for (int i = 0; i < 40; i++) {
            var point = new DungeonStaticChestPattern.Point(i % 30, 70, 10 + i / 30);
            catalog.add(new DungeonStaticChestPattern(base.room(), base.cells(), point, base.probes()));
            targets.add(transform.world(point.blockPos()));
        }
        var room = room(base, transform);
        var filter = new DungeonMimicStaticChests(catalog);
        var blocks = world(base, transform);
        AtomicInteger reads = new AtomicInteger();
        Function<BlockPos, String> counted = pos -> { reads.incrementAndGet(); return blocks.apply(pos); };
        for (int tick = 0; tick < 10; tick++) {
            filter.prepare();
            reads.set(0);
            for (var target : targets) filter.check(room, target, tick, counted);
            assertTrue("At most 512 world block reads in an update, got " + reads.get(), reads.get() <= 512);
        }
        filter.prepare();
        for (var target : targets) assertTrue(filter.check(room, target, 10, counted).fixed());
    }

    private static DungeonStaticChestPattern.Cell cell(int x, int z, int core) {
        return new DungeonStaticChestPattern.Cell(x, z, Set.of(core), Set.of());
    }

    private static DungeonStaticChestPattern pattern(boolean uniform) {
        List<DungeonStaticChestPattern.Probe> probes = new ArrayList<>();
        for (int i = 0; i < 12; i++) probes.add(new DungeonStaticChestPattern.Probe(
            new DungeonStaticChestPattern.Point(2 + i * 2, 68, 3 + i % 3),
            uniform || i % 2 == 0 ? "minecraft:stone" : "minecraft:obsidian"));
        return new DungeonStaticChestPattern("testroom", List.of(cell(0, 0, 123)),
            new DungeonStaticChestPattern.Point(6, 70, 9), probes);
    }

    private static DungeonStaticChestPattern.Room room(DungeonStaticChestPattern pattern, DungeonRoomTransform transform) {
        List<DungeonStaticChestPattern.Cell> cells = new ArrayList<>();
        for (var cell : pattern.cells()) {
            BlockPos pos = transform.world(new BlockPos(cell.x() * 32 + 15, 0, cell.z() * 32 + 15));
            cells.add(new DungeonStaticChestPattern.Cell((pos.getX() - transform.originX() - 15) / 32,
                (pos.getZ() - transform.originZ() - 15) / 32, cell.cores(), cell.stableCores()));
        }
        return new DungeonStaticChestPattern.Room(pattern.room(), transform.originX(), transform.originZ(), cells);
    }

    private static Map<BlockPos, String> probeMap(DungeonStaticChestPattern pattern, DungeonRoomTransform transform) {
        Map<BlockPos, String> blocks = new HashMap<>();
        for (var probe : pattern.probes()) blocks.put(transform.world(probe.pos().blockPos()), probe.block());
        return blocks;
    }

    private static Function<BlockPos, String> world(DungeonStaticChestPattern pattern, DungeonRoomTransform transform) {
        var blocks = probeMap(pattern, transform);
        return pos -> blocks.getOrDefault(pos, "minecraft:air");
    }
}
