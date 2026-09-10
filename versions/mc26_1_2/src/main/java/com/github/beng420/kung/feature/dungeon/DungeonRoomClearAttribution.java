package com.github.beng420.kung.feature.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps the first clear observation while late room recognition merges map cells into rooms. */
final class DungeonRoomClearAttribution {
    private final Map<Integer, Evidence> cells = new HashMap<>();

    void reset() { cells.clear(); }

    boolean exclude(Set<Integer> roomCells) {
        boolean changed = false;
        for (int cell : roomCells) {
            Evidence evidence = cells.remove(cell);
            if (evidence != null) {
                evidence.cells.remove(cell);
                changed = true;
            }
        }
        return changed;
    }

    boolean observe(Set<Integer> roomCells, Set<UUID> present, UUID fallback) {
        Set<Evidence> previous = new HashSet<>();
        for (int cell : roomCells) {
            Evidence evidence = cells.get(cell);
            if (evidence != null) previous.add(evidence);
        }
        Evidence evidence;
        if (previous.isEmpty()) {
            Set<UUID> candidates = new HashSet<>(present);
            if (candidates.isEmpty() && fallback != null) candidates.add(fallback);
            evidence = new Evidence(new HashSet<>(), candidates,
                present.size() == 1 ? present.iterator().next() : null);
        } else {
            evidence = previous.iterator().next();
            if (previous.size() == 1 && evidence.cells.containsAll(roomCells)) return false;
            for (Evidence old : previous) {
                if (old == evidence) continue;
                evidence.cells.addAll(old.cells);
                evidence.candidates.addAll(old.candidates);
                if (!java.util.Objects.equals(evidence.solo, old.solo)) evidence.solo = null;
            }
            if (evidence.candidates.size() != 1) evidence.solo = null;
        }
        evidence.cells.addAll(roomCells);
        for (int cell : evidence.cells) cells.put(cell, evidence);
        return true;
    }

    void remapPlayer(UUID oldUuid, UUID newUuid) {
        for (Evidence evidence : new HashSet<>(cells.values())) {
            if (evidence.candidates.remove(oldUuid)) evidence.candidates.add(newUuid);
            if (oldUuid.equals(evidence.solo)) evidence.solo = newUuid;
        }
    }

    Map<UUID, Bounds> bounds() {
        Map<UUID, Bounds> result = new HashMap<>();
        for (Evidence evidence : new HashSet<>(cells.values())) {
            for (UUID uuid : evidence.candidates) {
                Bounds old = result.getOrDefault(uuid, new Bounds(0, 0));
                result.put(uuid, new Bounds(old.minimum + (uuid.equals(evidence.solo) ? 1 : 0), old.maximum + 1));
            }
        }
        return Map.copyOf(result);
    }

    record Bounds(int minimum, int maximum) { }

    private static final class Evidence {
        private final Set<Integer> cells;
        private final Set<UUID> candidates;
        private UUID solo;

        private Evidence(Set<Integer> cells, Set<UUID> candidates, UUID solo) {
            this.cells = cells;
            this.candidates = candidates;
            this.solo = solo;
        }
    }
}
