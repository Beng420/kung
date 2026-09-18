package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.runtime.KungPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DungeonRoomProjectTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private DungeonConfig previousConfig;

    @Before public void isolateConfig() {
        previousConfig = KungConfig.get().dungeon;
        KungConfig.get().dungeon = new DungeonConfig();
    }

    @After public void restoreConfig() {
        KungConfig.get().dungeon = previousConfig;
    }

    @Test public void explicitLinkSelectsOnlyTheActiveProjectAndDefaultsToProfile() throws Exception {
        assertFalse(DungeonRoomProject.enabled());
        assertEquals(KungPaths.dungeonDataDirectory(), DungeonRoomProject.dataDirectory());
        Path project = temporary.newFolder("Kung Project").toPath();
        Path directory = project.resolve("versions/mc26_1_2/src/main/resources/kung-dungeon-scans");
        Files.createDirectories(directory);
        Path database = directory.resolve("known-rooms.json");
        String json = "{\"schema\":1,\"rooms\":[]}";
        Files.writeString(database, json);
        Path validated = DungeonRoomProject.validateProject(project.resolve(".").toString());
        assertEquals(project.toRealPath(), validated);
        assertEquals(json, Files.readString(database));

        KungConfig.get().dungeon.setRoomDataProjectDirectory(validated.toString());
        assertTrue(DungeonRoomProject.enabled());
        assertEquals(directory.toRealPath(), DungeonRoomProject.dataDirectory());
    }

    @Test public void rejectsMissingAndMalformedProjectDataWithoutWriting() throws Exception {
        Path project = temporary.newFolder("invalid").toPath();
        assertThrows(IOException.class, () -> DungeonRoomProject.validateProject(""));
        assertThrows(IOException.class, () -> DungeonRoomProject.validateProject(project.toString()));
        Path directory = project.resolve("versions/mc26_1_2/src/main/resources/kung-dungeon-scans");
        Files.createDirectories(directory);
        Path database = directory.resolve("known-rooms.json");
        for (String json : new String[] {"broken", "[]", "{}", "{\"schema\":2,\"rooms\":[]}",
            "{\"schema\":1.5,\"rooms\":[]}", "{\"schema\":\"1\",\"rooms\":[]}", "{\"schema\":1,\"rooms\":{}}"}) {
            Files.writeString(database, json);
            assertThrows(IOException.class, () -> DungeonRoomProject.validateProject(project.toString()));
            assertEquals(json, Files.readString(database));
        }
    }

    @Test public void projectWritesReplaceAtomicallyAndDoNotRecreateMissingParents() throws Exception {
        Path directory = temporary.newFolder("data").toPath();
        KungConfig.get().dungeon.setRoomDataProjectDirectory(temporary.getRoot().toString());
        Path database = directory.resolve("known-rooms.json");
        Files.writeString(database, "before");
        DungeonRoomProject.write(database, "after");
        assertEquals("after", Files.readString(database));

        Path blocked = directory.resolve("blocked.json");
        Files.createDirectory(blocked);
        Files.writeString(blocked.resolve("existing"), "preserve");
        assertThrows(IOException.class, () -> DungeonRoomProject.write(blocked, "replacement"));
        assertEquals("preserve", Files.readString(blocked.resolve("existing")));
        assertEquals("after", Files.readString(database));
        try (var entries = Files.list(directory)) {
            assertEquals(2, entries.count());
        }

        Path missing = directory.resolve("missing/known-rooms.json");
        assertThrows(IOException.class, () -> DungeonRoomProject.write(missing, "replacement"));
        assertFalse(Files.exists(missing.getParent()));
        KungConfig.get().dungeon.setRoomDataProjectDirectory("");
        DungeonRoomProject.write(missing, "profile");
        assertEquals("profile", Files.readString(missing));
    }
}
