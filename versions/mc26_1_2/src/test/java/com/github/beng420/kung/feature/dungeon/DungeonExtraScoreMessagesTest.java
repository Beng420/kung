package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.google.gson.Gson;
import org.junit.Test;

public final class DungeonExtraScoreMessagesTest {
    @Test
    public void switchesGateEachAnnouncementWithoutSuppressingBonusTracking() throws Exception {
        var previous = KungConfig.get().dungeon;
        try {
            for (int switches = 0; switches < 16; switches++) {
                var config = new DungeonConfig();
                KungConfig.get().dungeon = config;
                config.setExtraScoreMessagesEnabled((switches & 8) != 0);
                config.setMimicMessageEnabled((switches & 4) != 0);
                config.setPrinceMessageEnabled((switches & 2) != 0);
                config.setBatMessageEnabled((switches & 1) != 0);
                var stats = new DungeonRunStats();
                stats.observeMimicEspKill(null);
                stats.observeMessage(null, "A Prince falls. +1 Bonus Score", 1L);
                stats.observeMessage(null, "A Bat has been slain. +1 Bonus Score", 2L);
                assertTrue(stats.mimicKilled());
                assertTrue(stats.princeKilled());
                assertTrue(stats.batScoreKilled());
                // Null client prevents network sends; these flags record the
                // actual run-statistics branches that request an announcement.
                assertEquals(config.extraScoreMessagesEnabled() && config.mimicMessageEnabled(), requested(stats, "mimicMessageSent"));
                assertEquals(config.extraScoreMessagesEnabled() && config.princeMessageEnabled(), requested(stats, "princeMessageSent"));
                assertEquals(config.extraScoreMessagesEnabled() && config.batMessageEnabled(), requested(stats, "batMessageSent"));
            }
        } finally {
            KungConfig.get().dungeon = previous;
        }
    }

    @Test
    public void extraScoreMessagesWorkWithoutEnablingWorldScans() {
        var config = new Gson().fromJson("{}", KungConfig.class);
        config.dungeon.onChange(null);
        config.dungeon.setExtraScoreMessagesEnabled(true);
        var workload = DungeonWorkload.from(config);
        assertTrue(workload.players());
        assertFalse(workload.rooms());
        assertFalse(workload.mapData());
    }

    private static boolean requested(DungeonRunStats stats, String fieldName) throws Exception {
        var field = DungeonRunStats.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(stats);
    }
}
