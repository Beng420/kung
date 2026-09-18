package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DungeonRoomProjectCatalogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private DungeonConfig previousConfig;
    private Path project;
    private Path database;

    @Before public void linkProject() throws Exception {
        previousConfig = KungConfig.get().dungeon;
        project = temporary.newFolder("project").toPath();
        database = project.resolve("versions/mc26_1_2/src/main/resources/kung-dungeon-scans/known-rooms.json");
        Files.createDirectories(database.getParent());
        try (var resource = getClass().getResourceAsStream("/kung-dungeon-scans/known-rooms.json")) {
            assertNotNull(resource);
            Files.copy(resource, database);
        }
        JsonObject root = read();
        root.addProperty("projectNote", "Preserve external edits");
        JsonObject variant = room(root, "Black Flag", RoomType.NORMAL).getAsJsonArray("variants").get(0).getAsJsonObject();
        variant.addProperty("id", "preserved-variant-id");
        firstHashes(root, "Black Flag").get(0).getAsJsonObject().addProperty("source", "user-observation");
        Files.writeString(database, root.toString());
        Files.writeString(database.resolveSibling("known-room-preloads.jsonl"),
            "{\"coreHash\":1371371379,\"name\":\"Pirate\",\"type\":\"NORMAL\",\"secrets\":6}\n");
        freshConfig();
    }

    @After public void restoreConfig() {
        KungConfig.get().dungeon = previousConfig;
        DungeonKnownRoomCatalog.reload();
        DungeonRoomClassifier.reload();
    }

    @Test public void princeEditsPreserveRawMetadataAndSurviveFreshConfigWithSeparateNamesakes() throws Exception {
        JsonObject expected = read();
        room(expected, "Supertall", RoomType.NORMAL).addProperty("prince", false);
        room(expected, "Black Flag", RoomType.NORMAL).addProperty("prince", true);
        room(expected, "Black Flag", RoomType.NORMAL).addProperty("crypts", 2);
        room(expected, "Lava Pit", RoomType.RARE).addProperty("prince", true);
        room(expected, "Pirate", RoomType.NORMAL).addProperty("prince", true);

        DungeonKnownRoomCatalog.updateRoomPrince("Supertall", RoomType.NORMAL, 6, false);
        DungeonKnownRoomCatalog.updateRoomPrince("Black Flag", RoomType.NORMAL, 3, true);
        DungeonKnownRoomCatalog.updateRoomCrypts("Black Flag", RoomType.NORMAL, 3, 2);
        DungeonKnownRoomCatalog.updateRoomPrince("Lava Pit", RoomType.RARE, 0, true);
        DungeonKnownRoomCatalog.updateRoomPrince("Pirate", RoomType.NORMAL, 6, true);
        assertEquals(expected, read());
        freshConfig();
        assertNull(KungConfig.get().dungeon.roomPrinceOverride("supertall|NORMAL|6"));
        assertFalse(template("Supertall", RoomType.NORMAL).prince());
        assertTrue(template("Black Flag", RoomType.NORMAL).prince());
        assertEquals(2, template("Black Flag", RoomType.NORMAL).crypts());
        assertTrue(DungeonKnownRoomCatalog.knownCoreHint(1_371_371_379).prince());
        assertTrue(template("Lava Pit", RoomType.RARE).prince());
        assertFalse(template("Lava Pit", RoomType.NORMAL).prince());
        assertEquals(expected, read());
    }

    @Test public void learningAddsObservationsWithoutRewritingExistingRoomsOrHashMetadata() throws Exception {
        JsonObject before = read();
        var known = template("Black Flag", RoomType.NORMAL);
        int core = known.components().getFirst().coreHash();
        int stable = 1_717_171_719;
        long order = System.currentTimeMillis();
        DungeonKnownRoomCatalog.append(new DungeonKnownRoomCatalog.LearnedRoom(order,
            known.name(), known.type(), known.secrets(), known.crypts(), core, stable, 0, 0));
        DungeonKnownRoomCatalog.append(learned("Project Test Room", order + 20_000));

        JsonObject after = read();
        JsonArray oldHashes = firstHashes(before, "Black Flag");
        JsonArray newHashes = firstHashes(after, "Black Flag");
        assertEquals(oldHashes.size() + 1, newHashes.size());
        for (int index = 0; index < oldHashes.size(); index++) assertEquals(oldHashes.get(index), newHashes.get(index));
        JsonObject newHash = newHashes.get(newHashes.size() - 1).getAsJsonObject();
        assertEquals(core, newHash.get("core").getAsInt());
        assertEquals(stable, newHash.get("stable").getAsInt());
        assertFalse(room(after, "Black Flag", RoomType.NORMAL).get("prince").getAsBoolean());
        assertNotNull(template("Project Test Room", RoomType.NORMAL));

        JsonObject expected = before.deepCopy();
        firstHashes(expected, "Black Flag").add(newHash.deepCopy());
        expected.getAsJsonArray("rooms").add(room(after, "Project Test Room", RoomType.NORMAL).deepCopy());
        assertEquals(expected, after);
    }

    @Test public void undoAndDeletionDoNotRestoreBundledRoomsOrTouchTheOtherLavaPit() throws Exception {
        JsonObject before = read();
        DungeonKnownRoomCatalog.append(learned("Project Undo Room", System.currentTimeMillis() + 60_000));
        assertTrue(DungeonKnownRoomCatalog.undoLast().undone());
        assertEquals(before, read());
        assertTrue(DungeonKnownRoomCatalog.knownRoomInfos("Project Undo Room").isEmpty());

        JsonObject rare = room(before, "Lava Pit", RoomType.RARE).deepCopy();
        assertTrue(DungeonKnownRoomCatalog.deleteRoom("Lava Pit", RoomType.NORMAL, 3).deleted());
        freshConfig();
        assertEquals(rare, room(read(), "Lava Pit", RoomType.RARE));
        assertTrue(DungeonKnownRoomCatalog.knownRoomInfos("Lava Pit").stream().noneMatch(info -> info.type() == RoomType.NORMAL));
        assertEquals(1, DungeonKnownRoomCatalog.knownRoomInfos("Lava Pit").size());
        assertNotNull(DungeonKnownRoomCatalog.knownCoreHint(1_371_371_379));
        assertTrue(DungeonKnownRoomCatalog.deleteRoom("Pirate", RoomType.NORMAL, 6).deleted());
        freshConfig();
        assertNull(DungeonKnownRoomCatalog.knownCoreHint(1_371_371_379));
        assertTrue(DungeonKnownRoomCatalog.knownRoomInfos("Pirate").isEmpty());
    }

    @Test public void invalidOrMissingProjectDataCannotBeReplacedByAMutation() throws Exception {
        String original = Files.readString(database);
        template("Black Flag", RoomType.NORMAL); // Keep a recognized room cached before the project becomes unavailable.
        for (String invalid : List.of("broken", "{\"schema\":1,\"rooms\":[{\"name\":\"Broken\"}]}")) {
            Files.writeString(database, invalid);
            assertThrows(IOException.class, () -> DungeonKnownRoomCatalog.updateRoomPrince("Black Flag", RoomType.NORMAL, 3, true));
            assertThrows(IOException.class, () -> DungeonKnownRoomCatalog.append(learned("Rejected", System.currentTimeMillis())));
            assertThrows(IOException.class, DungeonKnownRoomCatalog::undoLast);
            assertThrows(IOException.class, () -> DungeonKnownRoomCatalog.deleteRoom("Black Flag", RoomType.NORMAL, 3));
            assertEquals(invalid, Files.readString(database));
            assertNull(KungConfig.get().dungeon.roomPrinceOverride("blackflag|NORMAL|3"));
        }
        Files.writeString(database, original);
        Path moved = database.resolveSibling("preserved.json");
        Files.move(database, moved);
        assertThrows(IOException.class, () -> DungeonKnownRoomCatalog.updateRoomPrince("Black Flag", RoomType.NORMAL, 3, true));
        assertThrows(IOException.class, () -> DungeonKnownRoomCatalog.append(learned("Rejected", System.currentTimeMillis())));
        assertFalse(Files.exists(database));
        assertEquals(original, Files.readString(moved));
        assertNull(KungConfig.get().dungeon.roomPrinceOverride("blackflag|NORMAL|3"));
    }

    @Test public void automaticStableHashLearningAndPreloadRecordingWorkWithLocalDataOff() throws Exception {
        var known = template("Black Flag", RoomType.NORMAL);
        int core = known.components().getFirst().coreHash();
        int stable = 1_717_171_723;
        var snapshot = new DungeonMapSnapshot();
        snapshot.addScan(0, 0, 5, 5, List.of(new DungeonScanPoint(0, 0, -185, -185,
            DungeonScanPointKind.ROOM, true, core, stable, 0, DungeonDoorKind.NONE)));
        var match = new DungeonKnownRoomCatalog.MatchedRoom(known,
            List.of(new DungeonKnownRoomCatalog.MatchedComponent(0, 0, core)));
        assertFalse(KungConfig.get().dungeon.localRoomDataEnabled());
        assertTrue(DungeonKnownRoomCatalog.autoLearnStableHashes(match, snapshot).learned());
        assertTrue(DungeonKnownRoomCatalog.isKnownCoreHash(stable));
        assertFalse(DungeonKnownRoomCatalog.autoLearnStableHashes(match, snapshot).learned());
        assertFalse(template("Black Flag", RoomType.NORMAL).prince());

        int earlier = 1_818_181_827;
        DungeonKnownRoomCatalog.recordObservedCoreTransition(earlier, core, stable);
        assertTrue(Files.readString(database.resolveSibling("known-room-preloads.jsonl")).contains(Integer.toString(earlier)));
        assertFalse(DungeonKnownRoomCatalog.isKnownCoreHash(earlier));
        DungeonKnownRoomCatalog.recordObservedCoreTransition(earlier, core, stable);
        freshConfig();
        assertEquals("Black Flag", DungeonKnownRoomCatalog.knownCoreHint(earlier).name());
        assertFalse(DungeonKnownRoomCatalog.knownCoreHint(earlier).prince());
    }

    private void freshConfig() {
        KungConfig.get().dungeon = new DungeonConfig();
        KungConfig.get().dungeon.setRoomDataProjectDirectory(project.toString());
        DungeonKnownRoomCatalog.reload();
        DungeonRoomClassifier.reload();
    }

    private JsonObject read() throws IOException {
        return JsonParser.parseString(Files.readString(database)).getAsJsonObject();
    }

    private static JsonObject room(JsonObject root, String name, RoomType type) {
        return root.getAsJsonArray("rooms").asList().stream().map(element -> element.getAsJsonObject())
            .filter(room -> room.get("name").getAsString().equals(name) && room.get("type").getAsString().equals(type.name()))
            .findFirst().orElseThrow();
    }

    private static JsonArray firstHashes(JsonObject root, String name) {
        return room(root, name, RoomType.NORMAL).getAsJsonArray("variants").get(0).getAsJsonObject()
            .getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonArray("hashes");
    }

    private static DungeonKnownRoomCatalog.RoomTemplate template(String name, RoomType type) {
        return DungeonKnownRoomCatalog.loadTemplates().stream()
            .filter(room -> room.name().equals(name) && room.type() == type).findFirst().orElseThrow();
    }

    private static DungeonKnownRoomCatalog.LearnedRoom learned(String name, long order) {
        return new DungeonKnownRoomCatalog.LearnedRoom(order, name, RoomType.NORMAL, 1, 2, 1_515_151_519, 1_616_161_621, 0, 0);
    }
}
