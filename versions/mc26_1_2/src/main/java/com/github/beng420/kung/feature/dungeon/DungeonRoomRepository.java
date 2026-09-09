package com.github.beng420.kung.feature.dungeon;

import java.io.IOException;
import java.util.List;

/** Read-oriented boundary around the persisted dungeon room catalog. */
public interface DungeonRoomRepository {
    long revision();

    List<DungeonKnownRoomCatalog.MatchedRoom> matchKnownRooms(DungeonMapSnapshot snapshot);

    DungeonKnownRoomCatalog.KnownCoreHint knownCoreHint(int coreHash);

    DungeonKnownRoomCatalog.AutoLearnResult autoLearnStableHashes(
        DungeonKnownRoomCatalog.MatchedRoom match,
        DungeonMapSnapshot snapshot
    ) throws IOException;

    boolean hasPrince(String roomName);
}
