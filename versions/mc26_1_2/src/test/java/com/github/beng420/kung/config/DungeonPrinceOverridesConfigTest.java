package com.github.beng420.kung.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DungeonPrinceOverridesConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void savesTrueAndFalseImmediatelyWithLocalDataOffAndSeparateRoomIdentities() throws Exception {
        var path = temporary.getRoot().toPath().resolve("kung.json");
        KungConfig config = new KungConfig(path);
        config.load();
        config.dungeon.setX(321);
        config.dungeon.setRoomPrinceOverride("doors|NORMAL|5", true);
        config.dungeon.setRoomPrinceOverride("doors|NORMAL|5", false);
        config.dungeon.setRoomPrinceOverride("lavapit|NORMAL|3", false);
        config.dungeon.setRoomPrinceOverride("lavapit|RARE|0", true);

        KungConfig restored = new KungConfig(path);
        restored.load();
        assertEquals(Boolean.FALSE, restored.dungeon.roomPrinceOverride("doors|NORMAL|5"));
        assertEquals(Boolean.FALSE, restored.dungeon.roomPrinceOverride("lavapit|NORMAL|3"));
        assertEquals(Boolean.TRUE, restored.dungeon.roomPrinceOverride("lavapit|RARE|0"));
        assertNull(restored.dungeon.roomPrinceOverride("lavapit|NORMAL|0"));
        assertNull(restored.dungeon.roomPrinceOverride("skull|NORMAL|1"));
        assertEquals(321, restored.dungeon.x());
        assertFalse(restored.dungeon.localRoomDataEnabled());
        assertFalse(restored.dungeon.enabled());
    }

    @Test public void olderConfigsAndNullMapsStartWithoutOverridesAndCanSaveFalse() throws Exception {
        var path = temporary.getRoot().toPath().resolve("kung.json");
        for (String json : new String[] {"{}", "{\"dungeonMap\":{}}",
            "{\"dungeonMap\":{\"princeRoomOverrides\":null}}"}) {
            Files.writeString(path, json);
            KungConfig config = new KungConfig(path);
            config.load();
            assertNull(config.dungeon.roomPrinceOverride("skull|NORMAL|1"));
            config.dungeon.setRoomPrinceOverride("skull|NORMAL|1", false);

            KungConfig restored = new KungConfig(path);
            restored.load();
            assertEquals(Boolean.FALSE, restored.dungeon.roomPrinceOverride("skull|NORMAL|1"));
            assertNull(restored.dungeon.roomPrinceOverride("doors|NORMAL|5"));
        }
    }

    @Test public void projectLinkDefaultsOffAndPersistsWithoutChangingOtherSettings() throws Exception {
        var path = temporary.getRoot().toPath().resolve("kung.json");
        KungConfig config = new KungConfig(path);
        config.load();
        assertEquals("", config.dungeon.roomDataProjectDirectory());
        config.dungeon.setRoomPrinceOverride("doors|NORMAL|5", false);
        config.dungeon.setRoomDataProjectDirectory("  D:/Kung Project  ");

        KungConfig restored = new KungConfig(path);
        restored.load();
        assertEquals("D:/Kung Project", restored.dungeon.roomDataProjectDirectory());
        assertEquals(Boolean.FALSE, restored.dungeon.roomPrinceOverride("doors|NORMAL|5"));
        assertFalse(restored.dungeon.localRoomDataEnabled());
        restored.dungeon.setRoomDataProjectDirectory(null);
        config.load();
        assertEquals("", config.dungeon.roomDataProjectDirectory());

        Files.writeString(path, "{\"dungeonMap\":{\"roomDataProjectDirectory\":null}}");
        config.load();
        assertEquals("", config.dungeon.roomDataProjectDirectory());
    }
}
