package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.github.beng420.kung.runtime.KungFileLayout;
import com.github.beng420.kung.runtime.KungPaths;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DungeonRoomLocalLearningTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void laterLearnDoesNotEraseEarlierLearnWhileLocalRecognitionIsDisabled() throws Exception {
        var previousConfig = KungConfig.get().dungeon;
        var layoutField = KungPaths.class.getDeclaredField("resolvedLayout");
        layoutField.setAccessible(true);
        var previousLayout = layoutField.get(null);
        var root = temporary.getRoot().toPath();
        try {
            layoutField.set(null, new KungFileLayout(root, root.resolve("config")));
            KungConfig.get().dungeon = new DungeonConfig();
            KungConfig.get().dungeon.onChange(() -> {});
            DungeonKnownRoomCatalog.reload();
            assertFalse(KungConfig.get().dungeon.localRoomDataEnabled());
            long time = 1789766765125L;
            var current = new DungeonKnownRoomCatalog.LearnedRoom(time,
                "Local learning test", RoomType.PUZZLE, 1, 0, 123456789, 987654321, 0, 5);
            var initial = new DungeonKnownRoomCatalog.LearnedRoom(time + 1,
                current.name(), current.type(), 1, 0, 123456788, 987654320, 0, 5);
            DungeonKnownRoomCatalog.append(current);
            DungeonKnownRoomCatalog.append(initial);
            var file = KungPaths.dungeonDataDirectory().resolve("known-rooms.json");
            String saved = Files.readString(file);
            assertTrue("The current hash must survive saving the initial hash", saved.contains("123456789"));
            assertTrue(saved.contains("123456788"));
            assertNull("Saving must not enable local recognition", DungeonKnownRoomCatalog.knownCoreHint(current.coreHash()));
            DungeonKnownRoomCatalog.updateRoomCrypts(current.name(), current.type(), 1, 2);
            assertTrue(DungeonKnownRoomCatalog.deleteRoom("Quiz").deleted());
            saved = Files.readString(file);
            assertTrue("Other edits must preserve local observations too", saved.contains("123456789"));
            assertTrue(saved.contains("123456788"));
            KungConfig.get().dungeon.setLocalRoomDataEnabled(true);
            DungeonKnownRoomCatalog.reload();
            assertEquals(current.name(), DungeonKnownRoomCatalog.knownCoreHint(current.coreHash()).name());
            assertEquals(2, DungeonKnownRoomCatalog.knownCoreHint(current.coreHash()).crypts());
            assertEquals(initial.name(), DungeonKnownRoomCatalog.knownCoreHint(initial.coreHash()).name());
        } finally {
            KungConfig.get().dungeon = previousConfig;
            layoutField.set(null, previousLayout);
            DungeonKnownRoomCatalog.reload();
        }
    }
}
