package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.KungConfig;

/** The shared lifecycle stays active; expensive consumers opt into their data sources. */
record DungeonWorkload(boolean rooms, boolean mapData, boolean players, boolean roomSync) {
    static DungeonWorkload current() { return from(KungConfig.get()); }

    static DungeonWorkload from(KungConfig config) {
        var dungeon = config.dungeon;
        boolean sync = dungeon.roomSyncEnabled();
        boolean rooms = dungeon.enabled() || config.bloodRush.enabled() || dungeon.playerTrackingEnabled() || sync;
        boolean players = rooms || config.splits.enabled() || dungeon.playerTrackingEnabled()
            || dungeon.deathMessagesEnabled() || dungeon.fiveCryptPartyMessageEnabled()
            || dungeon.cryptProgressPartyMessageEnabled() || dungeon.fiveCryptTitleEnabled();
        return new DungeonWorkload(rooms, rooms, players, sync);
    }
}
