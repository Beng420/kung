package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DungeonRoomProjectTypesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private DungeonConfig previousConfig;
    private Path directory;

    @Before public void linkProject() throws Exception {
        previousConfig = KungConfig.get().dungeon;
        KungConfig.get().dungeon = new DungeonConfig();
        Path project = temporary.newFolder("project").toPath();
        directory = project.resolve("versions/mc26_1_2/src/main/resources/kung-dungeon-scans");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("known-rooms.json"), "{\"schema\":1,\"rooms\":[]}");
        KungConfig.get().dungeon.setRoomDataProjectDirectory(project.toString());
        DungeonRoomClassifier.reload();
    }

    @After public void restoreConfig() {
        KungConfig.get().dungeon = previousConfig;
        DungeonRoomClassifier.reload();
    }

    @Test public void projectTypesAreAuthoritativeWithLocalDataOffAndMissingFilesStayMissing() throws Exception {
        Path file = directory.resolve("known-room-types.properties");
        assertEquals(RoomType.NORMAL, DungeonRoomClassifier.classifyRoom(400135283));
        assertFalse(Files.exists(file));
        Files.writeString(file, "400135283=RARE\n123=PUZZLE\n");
        DungeonRoomClassifier.reload();
        assertEquals(RoomType.RARE, DungeonRoomClassifier.classifyRoom(400135283));
        assertEquals(RoomType.PUZZLE, DungeonRoomClassifier.classifyRoom(123));
        assertEquals(RoomType.NORMAL, DungeonRoomClassifier.classifyRoom(-706847847));

        DungeonRoomClassifier.learnRoomType(123, RoomType.YELLOW);
        DungeonRoomClassifier.learnRoomType(456, RoomType.TRAP);
        DungeonRoomClassifier.removeRoomTypes(List.of(400135283));
        DungeonRoomClassifier.reload();
        assertEquals(RoomType.YELLOW, DungeonRoomClassifier.classifyRoom(123));
        assertEquals(RoomType.TRAP, DungeonRoomClassifier.classifyRoom(456));
        assertEquals(RoomType.NORMAL, DungeonRoomClassifier.classifyRoom(400135283));
        assertFalse(Files.readString(file).contains("400135283="));
        assertFalse(KungConfig.get().dungeon.localRoomDataEnabled());
    }

    @Test public void failedProjectWritesLeaveCachedAndStoredTypesUnchanged() throws Exception {
        Path file = directory.resolve("known-room-types.properties");
        String original = "123=RARE\n";
        Files.writeString(file, original);
        assertEquals(RoomType.RARE, DungeonRoomClassifier.classifyRoom(123));
        Files.delete(directory.resolve("known-rooms.json"));
        assertThrows(IOException.class, () -> DungeonRoomClassifier.learnRoomType(123, RoomType.YELLOW));
        assertThrows(IOException.class, () -> DungeonRoomClassifier.removeRoomTypes(List.of(123)));
        assertEquals(RoomType.RARE, DungeonRoomClassifier.classifyRoom(123));
        assertEquals(original, Files.readString(file));
    }
}
