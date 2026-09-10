package com.github.beng420.kung.feature.dungeon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Ordered, server-message driven phases. Wall time and server time remain separate. */
public final class DungeonSplitTracker {
    private static final String FIRST_SPLIT = "Blood Open";
    private static final String[] DEFAULT_SPLITS = {"Blood Open", "Blood Clear", "Portal Entry"};
    private static final String[][] FLOOR_SPLITS = {
        {},
        {"Bonzo Phase 1", "Bonzo Phase 2"},
        {"Scarf's Minions", "Scarf"},
        {"Guardians", "The Professor", "Final Guardian"},
        {"Thorn"},
        {"Livid"},
        {"Terracottas", "Giants", "Sadan"},
        {"Maxor", "Storm", "Terminals", "Goldor", "Necron"}
    };
    private static final String[] MASTER_7_BOSS_SPLITS = {
        "Maxor", "Storm", "Terminals", "Goldor", "Necron", "Relics", "Wither King", "Dragons"
    };
    private static final Pattern BLOOD_OPEN = Pattern.compile(
        "^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$"
    );

    private final LongSupplier clock;
    private final List<CompletedSplit> completed = new ArrayList<>();
    private List<CompletedSplit> completedView = List.of();
    private String[] splitNames = DEFAULT_SPLITS;
    private int floor = -1;
    private boolean masterMode;
    private boolean running;
    private boolean started;
    private long startedAtMillis;
    private long stoppedElapsedMillis;
    private long serverElapsedMillis;
    private long currentSplitStartMillis;
    private long currentSplitStartServerMillis;
    private String currentSplitName = FIRST_SPLIT;
    /** Measured progress in a phase interrupted by the run ending, not a completed phase. */
    private CompletedSplit stoppedCurrentSplit;
    /** Combat has ended, but Total still waits for the server's completion banner. */
    private String awaitingCompletionAfter;

    public DungeonSplitTracker() {
        this(System::currentTimeMillis);
    }

    DungeonSplitTracker(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public void startRun(long nowTick, int floor, boolean masterMode) {
        // Starting the timer must not throw away floor metadata already received
        // in this instance's start room. An ended run is never a metadata source.
        boolean suppliedFloor = floor >= 1 && floor <= 7;
        int resolvedFloor = suppliedFloor ? floor : !started && hasKnownFloor() ? this.floor : -1;
        boolean resolvedMaster = suppliedFloor ? masterMode : !started && hasKnownFloor() && this.masterMode;
        reset();
        configureKnownFloor(resolvedFloor, resolvedMaster);
        started = true;
        running = true;
        startedAtMillis = clock.getAsLong();
    }

    public void configureForFloor(int floor, boolean masterMode) {
        if (floor < 1 || floor > 7) return;
        configureKnownFloor(floor, masterMode);
    }

    /** Authoritative metadata distinguishes Entrance (0) from an unknown floor (-1). */
    public void configureKnownFloor(int floor, boolean masterMode) {
        // Reward/sidebar updates belong to the same world but must not rewrite its frozen result.
        if (started && !running) return;
        if (floor < 0 || floor > 7) return;
        if (floor == 0) masterMode = false;
        boolean nextMasterMode = masterMode || (started && this.floor == floor && this.masterMode);
        if (this.floor == floor && this.masterMode == nextMasterMode) return;
        this.floor = floor;
        this.masterMode = nextMasterMode;
        splitNames = namesFor(floor, nextMasterMode);
        // Late M7 metadata must turn Necron's death into the beginning of Relics.
        if (running && floor == 7 && nextMasterMode && "Necron".equals(awaitingCompletionAfter)) {
            currentSplitName = "Relics";
            awaitingCompletionAfter = null;
        }
    }

    /** A transfer/abort freezes the clocks without inventing a completed phase. */
    public void stopRun() {
        if (!running) return;
        stoppedElapsedMillis = currentTotalDurationMillis();
        captureStoppedCurrentSplit(stoppedElapsedMillis);
        running = false;
    }

    public void reset() {
        completed.clear();
        completedView = List.of();
        splitNames = DEFAULT_SPLITS;
        floor = -1;
        masterMode = false;
        started = false;
        running = false;
        startedAtMillis = 0L;
        stoppedElapsedMillis = 0L;
        serverElapsedMillis = 0L;
        currentSplitStartMillis = 0L;
        currentSplitStartServerMillis = 0L;
        currentSplitName = FIRST_SPLIT;
        stoppedCurrentSplit = null;
        awaitingCompletionAfter = null;
    }

    /** Manual debug split; automatic progression below only ever moves forward. */
    public void mark(String nextSplitName, long nowTick) {
        String normalizedName = normalizeName(nextSplitName);
        if (!running) {
            startRun(nowTick, 7, false);
            currentSplitName = normalizedName;
            return;
        }
        if (!currentSplitName.equals(normalizedName)) completeCurrentAndStart(normalizedName, true);
    }

    public boolean markIfCurrent(String currentName, String nextSplitName, long nowTick) {
        if (!hasCurrentSplit() || !currentSplitName.equals(normalizeName(currentName))) return false;
        mark(nextSplitName, nowTick);
        return true;
    }

    public void serverTick(long realNowMillis) {
        if (running) serverElapsedMillis += 50L;
    }

    public boolean observeMessage(String message, long nowTick) {
        if (!running || message == null || message.isBlank()) return false;
        String clean = DungeonLifecycleSignals.clean(message);
        if (DungeonLifecycleSignals.isRunStart(clean)) {
            // The lifecycle owner already starts this timer. A repeated countdown
            // must never erase completed splits, even if delivered much later.
            return false;
        }
        if (DungeonLifecycleSignals.isRunFinished(clean)) {
            finish();
            return true;
        }
        if (clean.startsWith("[BOSS] Wither King:") || clean.startsWith("[BOSS] The Wither King:")) {
            configureForFloor(7, true);
            clean = clean.replace("[BOSS] The Wither King:", "[BOSS] Wither King:");
        }
        if (clean.equals("[BOSS] Necron: All this, for nothing...")) {
            configureForFloor(7, masterMode);
            if (!masterMode) return completeBoss("Necron");
        }
        if (clean.equals("[BOSS] Wither King: Incredible. You did what I couldn't do myself.")) {
            return completeBoss("Dragons");
        }
        if (!hasCurrentSplit()) return false;
        int entryFloor = bossEntryFloor(clean);
        if (entryFloor > 0) configureForFloor(entryFloor, masterMode);
        String next = nextSplitFromMessage(clean);
        if (next == null) return false;
        if (next.equals("Boss Start")) {
            int portal = indexOf("Portal Entry");
            next = portal + 1 < splitNames.length ? splitNames[portal + 1] : null;
        }
        int nextIndex = indexOf(next);
        int currentIndex = indexOf(currentSplitName);
        if (nextIndex <= currentIndex || nextIndex < 0) return false;
        // Missing phase messages leave an unknown boundary. Preserve total time,
        // but do not label the entire elapsed span as an accurate single phase.
        completeCurrentAndStart(next, nextIndex == currentIndex + 1);
        return true;
    }

    public boolean running() { return running; }
    public boolean started() { return started; }
    public boolean hasKnownFloor() { return floor >= 0 && floor <= 7; }
    public boolean hasCurrentSplit() { return running && awaitingCompletionAfter == null; }
    public String currentSplitName() { return currentSplitName; }
    public long currentSplitDurationMillis() {
        return started ? Math.max(0L, currentTotalDurationMillis() - currentSplitStartMillis) : 0L;
    }
    public long currentTotalDurationMillis() {
        return running ? Math.max(0L, clock.getAsLong() - startedAtMillis) : stoppedElapsedMillis;
    }
    public long currentSplitServerDurationMillis() {
        return Math.max(0L, serverElapsedMillis - currentSplitStartServerMillis);
    }
    public long currentTotalServerDurationMillis() { return serverElapsedMillis; }
    public List<CompletedSplit> completedSplits() { return completedView; }
    public CompletedSplit stoppedCurrentSplit() { return stoppedCurrentSplit; }
    public String[] splitNames() { return splitNames.clone(); }

    public static String[] defaultSplitNames() {
        // Editor sample only: a live run always uses its own detected phases.
        return namesFor(7, true);
    }

    private void completeCurrentAndStart(String next, boolean timingKnown) {
        long elapsed = currentTotalDurationMillis();
        if (hasCurrentSplit()) completeCurrent(elapsed, timingKnown);
        awaitingCompletionAfter = null;
        currentSplitName = next;
        currentSplitStartMillis = elapsed;
        currentSplitStartServerMillis = serverElapsedMillis;
    }

    private void completeCurrent(long elapsed, boolean timingKnown) {
        completed.add(new CompletedSplit(
            currentSplitName,
            timingKnown ? Math.max(0L, elapsed - currentSplitStartMillis) : -1L,
            elapsed,
            timingKnown ? Math.max(0L, serverElapsedMillis - currentSplitStartServerMillis) : -1L,
            serverElapsedMillis
        ));
        completedView = List.copyOf(completed);
    }

    private void finish() {
        long elapsed = currentTotalDurationMillis();
        if (hasCurrentSplit()) {
            if (indexOf(currentSplitName) == splitNames.length - 1) completeCurrent(elapsed, true);
            else captureStoppedCurrentSplit(elapsed);
        }
        stoppedElapsedMillis = elapsed;
        running = false;
    }

    private void captureStoppedCurrentSplit(long elapsed) {
        if (!hasCurrentSplit()) return;
        stoppedCurrentSplit = new CompletedSplit(currentSplitName,
            Math.max(0L, elapsed - currentSplitStartMillis), elapsed,
            Math.max(0L, serverElapsedMillis - currentSplitStartServerMillis), serverElapsedMillis);
    }

    private boolean completeBoss(String finalSplitName) {
        if (!hasCurrentSplit()) return false;
        long elapsed = currentTotalDurationMillis();
        completeCurrent(elapsed, currentSplitName.equals(finalSplitName));
        awaitingCompletionAfter = finalSplitName;
        // Keep this boundary so late M7 metadata can resume Relics at Necron's death.
        currentSplitStartMillis = elapsed;
        currentSplitStartServerMillis = serverElapsedMillis;
        return true;
    }

    private int indexOf(String name) {
        for (int index = 0; index < splitNames.length; index++) {
            if (splitNames[index].equals(name)) return index;
        }
        return -1;
    }

    private static String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "Split";
        for (int floor = 1; floor <= 7; floor++) {
            for (String candidate : namesFor(floor, true)) {
                if (candidate.toLowerCase(Locale.ROOT).equals(trimmed.toLowerCase(Locale.ROOT))) return candidate;
            }
        }
        return trimmed;
    }

    private static String[] namesFor(int floor, boolean masterMode) {
        String[] boss = floor == 7 && masterMode ? MASTER_7_BOSS_SPLITS : FLOOR_SPLITS[Math.clamp(floor, 0, 7)];
        String[] names = Arrays.copyOf(DEFAULT_SPLITS, DEFAULT_SPLITS.length + boss.length);
        System.arraycopy(boss, 0, names, DEFAULT_SPLITS.length, boss.length);
        return names;
    }

    private String nextSplitFromMessage(String message) {
        if (BLOOD_OPEN.matcher(message).matches()) return "Blood Clear";
        return switch (message) {
            case "[BOSS] The Watcher: You have proven yourself. You may pass." -> "Portal Entry";
            case "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.",
                "[BOSS] Scarf: This is where the journey ends for you, Adventurers.",
                "[BOSS] The Professor: I was burdened with terrible news recently...",
                "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!",
                "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.",
                "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!",
                "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> "Boss Start";
            case "[BOSS] Bonzo: Oh I'm dead!" -> "Bonzo Phase 2";
            case "[BOSS] Scarf: Did you forget? I was taught by the best! Let's dance." -> "Scarf";
            case "[BOSS] The Professor: Oh? You found my Guardians' one weakness?" -> "The Professor";
            case "[BOSS] The Professor: What?! My Guardian power is unbeatable!" -> "Final Guardian";
            case "[BOSS] Sadan: ENOUGH!" -> "Giants";
            case "[BOSS] Sadan: You did it. I understand now, you have earned my respect." -> "Sadan";
            case "[BOSS] Storm: Pathetic Maxor, just like expected." -> "Storm";
            case "[BOSS] Goldor: Who dares trespass into my domain?" -> "Terminals";
            case "The Core entrance is opening!" -> "Goldor";
            case "[BOSS] Necron: You went further than any human before, congratulations." -> "Necron";
            case "[BOSS] Necron: All this, for nothing..." -> "Relics";
            case "[BOSS] Wither King: You... again?",
                "[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you." -> "Wither King";
            case "[BOSS] Wither King: We will decide it all, here, now." -> "Dragons";
            default -> null;
        };
    }

    private static int bossEntryFloor(String message) {
        return switch (message) {
            case "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable." -> 1;
            case "[BOSS] Scarf: This is where the journey ends for you, Adventurers." -> 2;
            case "[BOSS] The Professor: I was burdened with terrible news recently..." -> 3;
            case "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!" -> 4;
            case "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows." -> 5;
            case "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!" -> 6;
            case "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> 7;
            default -> 0;
        };
    }

    public record CompletedSplit(
        String name,
        long splitDurationMillis,
        long totalDurationMillis,
        long serverSplitDurationMillis,
        long serverTotalDurationMillis
    ) { }
}
