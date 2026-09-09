package com.github.beng420.kung.feature.dungeon;

import java.io.IOException;
import java.util.List;

public enum KnownDungeonRoomRepository implements DungeonRoomRepository {
    INSTANCE;

    @Override
    public long revision() {
        return DungeonKnownRoomCatalog.revision();
    }

    @Override
    public List<DungeonKnownRoomCatalog.MatchedRoom> matchKnownRooms(DungeonMapSnapshot snapshot) {
        return DungeonKnownRoomCatalog.matchKnownRooms(snapshot);
    }

    @Override
    public DungeonKnownRoomCatalog.KnownCoreHint knownCoreHint(int coreHash) {
        return DungeonKnownRoomCatalog.knownCoreHint(coreHash);
    }

    @Override
    public DungeonKnownRoomCatalog.AutoLearnResult autoLearnStableHashes(
        DungeonKnownRoomCatalog.MatchedRoom match,
        DungeonMapSnapshot snapshot
    ) throws IOException {
        return DungeonKnownRoomCatalog.autoLearnStableHashes(match, snapshot);
    }

    @Override
    public boolean hasPrince(String roomName) {
        return DungeonKnownRoomCatalog.hasPrince(roomName);
    }
}
