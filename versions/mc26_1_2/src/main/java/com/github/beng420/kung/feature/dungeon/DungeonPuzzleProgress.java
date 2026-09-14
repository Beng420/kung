package com.github.beng420.kung.feature.dungeon;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Tab evidence survives temporary missing rows, but is replaced by newer states of the same puzzle. */
final class DungeonPuzzleProgress {
    private static final Pattern TOTAL = Pattern.compile("^Puzzles:\\s*\\((\\d+)\\)$");
    private static final Pattern STATE = Pattern.compile("^([^:>]+):\\s*\\[([✓✔✦✖xX?])]\\s*(?:\\([^)]*\\))?$");
    private final Map<String, Character> states = new HashMap<>();
    private int total;

    boolean observe(String line) {
        var count = TOTAL.matcher(line.strip());
        if (count.matches()) {
            total = Math.max(total, Integer.parseInt(count.group(1)));
            return true;
        }
        var state = STATE.matcher(line.strip());
        if (!state.matches()) return false;
        // Undiscovered rows share placeholder names; the header supplies their count.
        if (state.group(1).contains("?")) return true;
        states.put(state.group(1).strip().toLowerCase(Locale.ROOT), state.group(2).charAt(0));
        return true;
    }

    int completed() {
        return (int) states.values().stream().filter(state -> state == '✓' || state == '✔').count();
    }

    int failed() {
        return (int) states.values().stream().filter(state -> state == '✖' || state == 'x' || state == 'X').count();
    }

    int unfinished(int mapTotal, int mapCompleted) {
        int knownTotal = Math.max(Math.max(total, states.size()), mapTotal);
        // Both sources describe the same puzzles; never add their counts together.
        int solved = Math.max(completed(), mapCompleted);
        return Math.max(failed(), Math.max(0, knownTotal - solved));
    }

    void reset() {
        total = 0;
        states.clear();
    }
}
