package com.github.beng420.kung.feature.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Session-only aliases from same-cell transitions into a directly known room. */
final class DungeonPreloadHints {
    private final Map<HashPair, DungeonKnownRoomCatalog.KnownCoreHint> hints = new HashMap<>();
    private final Set<HashPair> conflicts = new HashSet<>();

    boolean observe(int core, int stable, DungeonKnownRoomCatalog.KnownCoreHint room) {
        if (DungeonRoomClassifier.isEmptyCore(core) || stable == 0 || room == null) return false;
        HashPair pair = new HashPair(core, stable);
        if (conflicts.contains(pair)) return false;
        var previous = hints.putIfAbsent(pair, room);
        if (previous == null) return true;
        if (previous.name().equals(room.name()) && previous.type() == room.type()
            && previous.secrets() == room.secrets()) return false;
        hints.remove(pair);
        conflicts.add(pair);
        return true;
    }

    DungeonKnownRoomCatalog.KnownCoreHint get(int core, int stable) {
        return hints.get(new HashPair(core, stable));
    }

    void clear() { hints.clear(); conflicts.clear(); }

    private record HashPair(int core, int stable) { }
}
