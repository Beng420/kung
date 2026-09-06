package com.github.beng420.kung.feature.dungeon;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public final class DungeonSplitTracker {
    private static final String FIRST_SPLIT = "Blood Open";
    private static final String[] DEFAULT_SPLITS = {
        "Blood Open",
        "Blood Clear",
        "Portal Entry",
        "Cleared"
    };
    private static final String[] FLOOR_1_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Bonzo's Sike", "Cleared"
    };
    private static final String[] FLOOR_2_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Scarf's minions", "Cleared"
    };
    private static final String[] FLOOR_3_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "The Guardians", "The Professor", "Cleared"
    };
    private static final String[] FLOOR_4_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Cleared"
    };
    private static final String[] FLOOR_5_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Cleared"
    };
    private static final String[] FLOOR_6_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Terracottas", "Giants", "Cleared"
    };
    private static final String[] FLOOR_7_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Maxor", "Storm", "Terminals", "Goldor", "Necron", "Cleared"
    };
    private static final String[] MASTER_7_SPLITS = {
        "Blood Open", "Blood Clear", "Portal Entry", "Maxor", "Storm", "Terminals", "Goldor", "Necron", "Cleared"
    };
    private static final Pattern STARTING_IN_ONE_SECOND = Pattern.compile("^Starting in 1 second\\.$");
    private static final Pattern BLOOD_CLEAR_TRIGGER = Pattern.compile(
        "^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$"
    );
    private static final Pattern PORTAL_ENTRY_TRIGGER = Pattern.compile(
        "^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$"
    );
    private static final Pattern FLOOR_1_ENTRY = Pattern.compile(
        "^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$"
    );
    private static final Pattern FLOOR_1_SIKE = Pattern.compile("^\\[BOSS] Bonzo: Oh I'm dead!$");
    private static final Pattern FLOOR_2_ENTRY = Pattern.compile(
        "^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$"
    );
    private static final Pattern FLOOR_2_MINIONS = Pattern.compile(
        "^\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.$"
    );
    private static final Pattern FLOOR_3_ENTRY = Pattern.compile(
        "^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$"
    );
    private static final Pattern FLOOR_3_GUARDIANS = Pattern.compile(
        "^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$"
    );
    private static final Pattern FLOOR_3_PROFESSOR = Pattern.compile(
        "^\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!$"
    );
    private static final Pattern FLOOR_4_ENTRY = Pattern.compile(
        "^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!$"
    );
    private static final Pattern FLOOR_5_ENTRY = Pattern.compile(
        "^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$"
    );
    private static final Pattern FLOOR_6_ENTRY = Pattern.compile(
        "^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!$"
    );
    private static final Pattern FLOOR_6_TERRACOTTAS = Pattern.compile("^\\[BOSS] Sadan: ENOUGH!$");
    private static final Pattern FLOOR_6_GIANTS = Pattern.compile(
        "^\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.$"
    );
    private static final Pattern FLOOR_7_ENTRY = Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
    private static final Pattern FLOOR_7_MAXOR = Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
    private static final Pattern FLOOR_7_STORM = Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");
    private static final Pattern FLOOR_7_TERMINALS = Pattern.compile("^The Core entrance is opening!$");
    private static final Pattern FLOOR_7_GOLDOR = Pattern.compile(
        "^\\[BOSS] Necron: You went further than any human before, congratulations\\.$"
    );
    private static final Pattern FLOOR_7_NECRON = Pattern.compile("^\\[BOSS] Necron: All this, for nothing\\.\\.\\.$");
    private static final Pattern RUN_FINISHED = Pattern.compile(
        "^\\s*\\S Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$"
    );

    private final List<CompletedSplit> completedSplits = new CopyOnWriteArrayList<>();
    private String[] splitNames = DEFAULT_SPLITS;
    private boolean running;
    private long serverElapsedMillis;
    private long lastServerTickRealMillis;
    private long currentSplitStartMillis;
    private long lastAutoMarkMillis;
    private String lastAutoMarkName = "";
    private String currentSplitName = FIRST_SPLIT;

    public void startRun(long nowTick, int floor, boolean masterMode) {
        configureForFloor(floor, masterMode);
        completedSplits.clear();
        running = true;
        serverElapsedMillis = 0L;
        currentSplitStartMillis = 0L;
        lastServerTickRealMillis = System.currentTimeMillis();
        lastAutoMarkMillis = 0L;
        lastAutoMarkName = "";
        currentSplitName = FIRST_SPLIT;
    }

    public void configureForFloor(int floor, boolean masterMode) {
        splitNames = splitNamesFor(floor, masterMode);
        if (!containsSplit(splitNames, currentSplitName)) {
            currentSplitName = FIRST_SPLIT;
        }
    }

    public void stopRun() {
        running = false;
    }

    public void reset() {
        completedSplits.clear();
        running = false;
        serverElapsedMillis = 0L;
        lastServerTickRealMillis = 0L;
        currentSplitStartMillis = 0L;
        lastAutoMarkMillis = 0L;
        lastAutoMarkName = "";
        currentSplitName = FIRST_SPLIT;
    }

    public void mark(String nextSplitName, long nowTick) {
        String normalizedName = normalizeName(nextSplitName);
        if (!running) {
            startRun(nowTick, 7, false);
            currentSplitName = normalizedName;
            return;
        }

        long nowMillis = currentTotalDurationMillis();
        completedSplits.add(new CompletedSplit(
            currentSplitName,
            Math.max(0L, nowMillis - currentSplitStartMillis),
            nowMillis
        ));
        currentSplitName = normalizedName;
        currentSplitStartMillis = nowMillis;
    }

    public boolean markIfCurrent(String currentName, String nextSplitName, long nowTick) {
        if (!running || !currentSplitName.equals(normalizeName(currentName))) {
            return false;
        }
        mark(nextSplitName, nowTick);
        return true;
    }

    public void serverTick(long realNowMillis) {
        if (!running) {
            return;
        }

        serverElapsedMillis += 50L;
        lastServerTickRealMillis = realNowMillis;
    }

    public boolean observeMessage(String message, long nowTick) {
        if (!running || message == null || message.isBlank()) {
            return false;
        }

        String cleanMessage = stripFormatting(message);
        if (STARTING_IN_ONE_SECOND.matcher(cleanMessage).matches()) {
            restartTimer();
            return true;
        }

        if (RUN_FINISHED.matcher(cleanMessage).matches()) {
            finish(nowTick);
            return true;
        }

        String nextSplitName = nextSplitFromMessage(cleanMessage);
        nextSplitName = adaptToActiveSplitList(nextSplitName);
        if (nextSplitName == null || duplicateAutoMark(nextSplitName, currentTotalDurationMillis())) {
            return false;
        }

        mark(nextSplitName, nowTick);
        lastAutoMarkName = nextSplitName;
        lastAutoMarkMillis = currentTotalDurationMillis();
        return true;
    }

    private String adaptToActiveSplitList(String splitName) {
        if (splitName == null || containsSplit(splitNames, splitName)) {
            return splitName;
        }
        if ("Boss Start".equals(splitName)) {
            return splitAfter("Portal Entry");
        }
        return null;
    }

    private String splitAfter(String splitName) {
        for (int index = 0; index < splitNames.length - 1; index++) {
            if (splitNames[index].equals(splitName)) {
                return splitNames[index + 1];
            }
        }
        return null;
    }

    public boolean running() {
        return running;
    }

    public String currentSplitName() {
        return currentSplitName;
    }

    public long currentSplitDurationMillis() {
        return running ? Math.max(0L, currentTotalDurationMillis() - currentSplitStartMillis) : 0L;
    }

    public long currentTotalDurationMillis() {
        if (!running) {
            return serverElapsedMillis;
        }

        long smoothMillis = 0L;
        if (lastServerTickRealMillis > 0L) {
            smoothMillis = Math.max(0L, Math.min(50L, System.currentTimeMillis() - lastServerTickRealMillis));
        }
        return serverElapsedMillis + smoothMillis;
    }

    public List<CompletedSplit> completedSplits() {
        return List.copyOf(completedSplits);
    }

    public static String[] defaultSplitNames() {
        return DEFAULT_SPLITS.clone();
    }

    public String[] splitNames() {
        return splitNames.clone();
    }

    private static String normalizeName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "Split";
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String[] splitSet : ALL_SPLIT_SETS) {
            for (String defaultSplit : splitSet) {
                if (defaultSplit.toLowerCase(Locale.ROOT).equals(lower)) {
                    return defaultSplit;
                }
            }
        }
        return trimmed;
    }

    private static String[] splitNamesFor(int floor, boolean masterMode) {
        if (masterMode && floor == 7) {
            return MASTER_7_SPLITS.clone();
        }
        return switch (floor) {
            case 1 -> FLOOR_1_SPLITS.clone();
            case 2 -> FLOOR_2_SPLITS.clone();
            case 3 -> FLOOR_3_SPLITS.clone();
            case 4 -> FLOOR_4_SPLITS.clone();
            case 5 -> FLOOR_5_SPLITS.clone();
            case 6 -> FLOOR_6_SPLITS.clone();
            case 7 -> FLOOR_7_SPLITS.clone();
            default -> DEFAULT_SPLITS.clone();
        };
    }

    private static boolean containsSplit(String[] splitNames, String splitName) {
        for (String candidate : splitNames) {
            if (candidate.equals(splitName)) {
                return true;
            }
        }
        return false;
    }

    private boolean duplicateAutoMark(String splitName, long nowTick) {
        return splitName.equals(lastAutoMarkName)
            && nowTick - lastAutoMarkMillis < 3000L;
    }

    private void restartTimer() {
        completedSplits.clear();
        serverElapsedMillis = 0L;
        currentSplitStartMillis = 0L;
        lastServerTickRealMillis = System.currentTimeMillis();
        lastAutoMarkMillis = 0L;
        lastAutoMarkName = "";
        currentSplitName = FIRST_SPLIT;
    }

    private void finish(long nowTick) {
        if (!running) {
            return;
        }

        long nowMillis = currentTotalDurationMillis();
        completedSplits.add(new CompletedSplit(
            currentSplitName,
            Math.max(0L, nowMillis - currentSplitStartMillis),
            nowMillis
        ));
        running = false;
    }

    private static String nextSplitFromMessage(String message) {
        if (BLOOD_CLEAR_TRIGGER.matcher(message).matches()) {
            return "Blood Clear";
        }
        if (PORTAL_ENTRY_TRIGGER.matcher(message).matches()) {
            return "Portal Entry";
        }
        if (FLOOR_1_ENTRY.matcher(message).matches()
            || FLOOR_2_ENTRY.matcher(message).matches()
            || FLOOR_3_ENTRY.matcher(message).matches()
            || FLOOR_4_ENTRY.matcher(message).matches()
            || FLOOR_5_ENTRY.matcher(message).matches()
            || FLOOR_6_ENTRY.matcher(message).matches()
            || FLOOR_7_ENTRY.matcher(message).matches()) {
            return "Boss Start";
        }
        if (FLOOR_1_SIKE.matcher(message).matches()
            || FLOOR_2_MINIONS.matcher(message).matches()
            || FLOOR_4_ENTRY.matcher(message).matches()
            || FLOOR_5_ENTRY.matcher(message).matches()
            || FLOOR_6_GIANTS.matcher(message).matches()
            || FLOOR_7_NECRON.matcher(message).matches()) {
            return "Cleared";
        }
        if (FLOOR_3_GUARDIANS.matcher(message).matches()) {
            return "The Professor";
        }
        if (FLOOR_3_PROFESSOR.matcher(message).matches()) {
            return "Cleared";
        }
        if (FLOOR_6_TERRACOTTAS.matcher(message).matches()) {
            return "Giants";
        }
        if (FLOOR_7_MAXOR.matcher(message).matches()) {
            return "Storm";
        }
        if (FLOOR_7_STORM.matcher(message).matches()) {
            return "Terminals";
        }
        if (FLOOR_7_TERMINALS.matcher(message).matches()) {
            return "Goldor";
        }
        if (FLOOR_7_GOLDOR.matcher(message).matches()) {
            return "Necron";
        }
        return null;
    }

    private static String stripFormatting(String message) {
        return message.replaceAll("§.", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public record CompletedSplit(
        String name,
        long splitDurationMillis,
        long totalDurationMillis
    ) {
    }

    private static final String[][] ALL_SPLIT_SETS = {
        DEFAULT_SPLITS,
        FLOOR_1_SPLITS,
        FLOOR_2_SPLITS,
        FLOOR_3_SPLITS,
        FLOOR_4_SPLITS,
        FLOOR_5_SPLITS,
        FLOOR_6_SPLITS,
        FLOOR_7_SPLITS,
        MASTER_7_SPLITS
    };
}
