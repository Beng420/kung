package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.SplitsConfig;
import com.github.beng420.kung.util.KungDebugRecorder;
import com.github.beng420.kung.util.ServerTpsTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Ordered, server-message driven phases. Wall time and server time remain separate. */
public final class DungeonSplitTracker {
    private static final String FIRST_SPLIT = "Blood Open";
    private static final long VICTORY_CONFIRMATION_WINDOW_MILLIS = 5_000L;
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
    private final Consumer<String> diagnostics;
    private final Supplier<SplitsConfig> config;
    private final Consumer<PhaseMessage> phaseMessages;
    private final Map<String, Long> personalBestCandidates = new HashMap<>();
    private boolean manualRun;
    private long predictedFinishMillis = -1L;
    /** PB sum after the active phase; refreshed only at boundaries or floor changes. */
    private long futurePhaseBestsMillis = -1L;
    private final List<CompletedSplit> completed = new ArrayList<>();
    private List<CompletedSplit> completedView = List.of();
    private String[] splitNames = DEFAULT_SPLITS;
    private int floor = -1;
    private boolean masterMode;
    private boolean running;
    private boolean started;
    private long startedAtMillis;
    private long stoppedElapsedMillis;
    /** Conserved accepted tick time; arrival jitter must not remove fractions of ticks. */
    private long serverElapsedMillis;
    private boolean serverClockAvailable;
    private long lastServerTickAtMillis = Long.MIN_VALUE;
    private long largestPhaseTickGapMillis;
    private long currentSplitStartMillis;
    private long currentSplitStartServerMillis;
    private String currentSplitName = FIRST_SPLIT;
    /** Measured progress in a phase interrupted by the run ending, not a completed phase. */
    private CompletedSplit stoppedCurrentSplit;
    /** Combat has ended, but Total still waits for the server's completion banner. */
    private String awaitingCompletionAfter;
    /** The score freezes clocks before the immediately following victory text can confirm this PB. */
    private PendingVictory pendingVictory;

    public DungeonSplitTracker() {
        this(() -> System.nanoTime() / 1_000_000L,
            event -> KungDebugRecorder.event("dungeon-splits", event + " " + ServerTpsTracker.INSTANCE.diagnostics()),
            () -> KungConfig.get().splits, DungeonSplitMessages::send);
    }

    DungeonSplitTracker(LongSupplier clock) {
        this(clock, ignored -> { });
    }

    DungeonSplitTracker(LongSupplier clock, Consumer<String> diagnostics) {
        this(clock, diagnostics, new SplitsConfig());
    }

    DungeonSplitTracker(LongSupplier clock, Consumer<String> diagnostics, SplitsConfig config) {
        this(clock, diagnostics, config, ignored -> { });
    }

    DungeonSplitTracker(LongSupplier clock, Consumer<String> diagnostics, SplitsConfig config,
                        Consumer<PhaseMessage> phaseMessages) {
        this(clock, diagnostics, () -> config, phaseMessages);
    }

    private DungeonSplitTracker(LongSupplier clock, Consumer<String> diagnostics, Supplier<SplitsConfig> config,
                                Consumer<PhaseMessage> phaseMessages) {
        this.clock = Objects.requireNonNull(clock);
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.config = Objects.requireNonNull(config);
        this.phaseMessages = Objects.requireNonNull(phaseMessages);
    }

    public void startRun(long nowTick, int floor, boolean masterMode) {
        // Starting the timer must not throw away floor metadata already received
        // in this instance's start room. An ended run is never a metadata source.
        boolean suppliedFloor = floor >= 1 && floor <= 7;
        int resolvedFloor = suppliedFloor ? floor : !started && hasKnownFloor() ? this.floor : -1;
        boolean resolvedMaster = suppliedFloor ? masterMode : !started && hasKnownFloor() && this.masterMode;
        boolean preparedTickStream = !started && serverClockAvailable;
        reset();
        serverClockAvailable = preparedTickStream;
        configureKnownFloor(resolvedFloor, resolvedMaster);
        started = true;
        running = true;
        startedAtMillis = clock.getAsLong();
        refreshPrediction();
        diagnostics.accept("run-start floor=" + this.floor + " master=" + this.masterMode + personalBestDiagnostics());
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
        refreshPrediction();
    }

    /** A transfer/abort freezes the clocks without inventing a completed phase. */
    public void stopRun() {
        if (!running) return;
        stoppedElapsedMillis = currentTotalDurationMillis();
        captureStoppedCurrentSplit(stoppedElapsedMillis, "stopRun");
        running = false;
        predictedFinishMillis = -1L;
        savePersonalBests();
        traceStopped("run-stop", "stopRun");
    }

    public void reset() {
        savePersonalBests();
        manualRun = false;
        predictedFinishMillis = -1L;
        futurePhaseBestsMillis = -1L;
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
        serverClockAvailable = false;
        lastServerTickAtMillis = Long.MIN_VALUE;
        largestPhaseTickGapMillis = 0L;
        currentSplitStartMillis = 0L;
        currentSplitStartServerMillis = 0L;
        currentSplitName = FIRST_SPLIT;
        stoppedCurrentSplit = null;
        awaitingCompletionAfter = null;
        pendingVictory = null;
    }

    /** Manual debug split; automatic progression below only ever moves forward. */
    public void mark(String nextSplitName, long nowTick) {
        String normalizedName = normalizeName(nextSplitName);
        if (!running) {
            startRun(nowTick, 7, false);
            manualRun = true;
            predictedFinishMillis = -1L;
            currentSplitName = normalizedName;
            return;
        }
        manualRun = true;
        predictedFinishMillis = -1L;
        if (!currentSplitName.equals(normalizedName)) completeCurrentAndStart(normalizedName, true, "manual:" + normalizedName);
    }

    public boolean markIfCurrent(String currentName, String nextSplitName, long nowTick) {
        if (!hasCurrentSplit() || !currentSplitName.equals(normalizeName(currentName))) return false;
        mark(nextSplitName, nowTick);
        return true;
    }

    public void serverTick(long realNowMillis) {
        // Start-room evidence distinguishes a stall immediately after countdown
        // from a connection on which no usable tick stream has ever been seen.
        if (!started) {
            serverClockAvailable = true;
            return;
        }
        if (!running) return;
        serverClockAvailable = true;
        // Source filtering establishes progress; wall time only measures receipt.
        // Even a burst spanning chat boundaries must preserve every accepted tick.
        serverElapsedMillis += 50L;
        long gapStart = lastServerTickAtMillis != Long.MIN_VALUE ? lastServerTickAtMillis : startedAtMillis;
        largestPhaseTickGapMillis = Math.max(largestPhaseTickGapMillis, realNowMillis - gapStart);
        lastServerTickAtMillis = realNowMillis;
    }

    public boolean observeMessage(String message, long nowTick) {
        if (message == null || message.isBlank()) return false;
        String clean = DungeonLifecycleSignals.clean(message);
        if (DungeonLifecycleSignals.isRunStart(clean)
            || clean.equals("[NPC] Mort: Here, I found this map when I first entered the dungeon.")) {
            diagnostics.accept("start-candidate message=\"" + clean + "\" running=" + running
                + " wallMs=" + currentTotalDurationMillis() + " totalTicks=" + serverElapsedMillis / 50L);
        }
        if (!running) return confirmPendingVictory(clean);
        if (DungeonLifecycleSignals.isRunStart(clean)) {
            // The lifecycle owner already starts this timer. A repeated countdown
            // must never erase completed splits, even if delivered much later.
            return false;
        }
        if (DungeonLifecycleSignals.isRunFinished(clean)) {
            finish(clean);
            return true;
        }
        if (clean.startsWith("[BOSS] Wither King:") || clean.startsWith("[BOSS] The Wither King:")) {
            configureForFloor(7, true);
            clean = clean.replace("[BOSS] The Wither King:", "[BOSS] Wither King:");
        }
        if (clean.equals("[BOSS] Necron: All this, for nothing...")) {
            configureForFloor(7, masterMode);
            if (!masterMode) return completeBoss("Necron", clean);
        }
        if (clean.equals("[BOSS] Wither King: Incredible. You did what I couldn't do myself.")) {
            return completeBoss("Dragons", clean);
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
        completeCurrentAndStart(next, nextIndex == currentIndex + 1, clean);
        return true;
    }

    public boolean running() { return running; }
    public boolean started() { return started; }
    /** Boss progress remains evidence after completion; this does not establish instance membership. */
    public boolean hasEnteredBoss() { return started && indexOf(currentSplitName) >= DEFAULT_SPLITS.length; }
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
        return splitServerDurationMillis();
    }
    /** No observed tick stream is an unavailable measurement, never a zero-TPS run. */
    public long currentTotalServerDurationMillis() { return totalServerDurationMillis(); }
    /** Sample both clocks at one instant so a frame cannot combine different wall-time bounds. */
    public Timings currentTimings() {
        long elapsed = currentTotalDurationMillis();
        return new Timings(Math.max(0L, elapsed - currentSplitStartMillis), elapsed,
            splitServerDurationMillis(), totalServerDurationMillis());
    }
    public List<CompletedSplit> completedSplits() { return completedView; }
    public CompletedSplit stoppedCurrentSplit() { return stoppedCurrentSplit; }
    public String[] splitNames() { return splitNames.clone(); }
    public long predictedFinishMillis() {
        return predictedFinishMillis(currentTotalDurationMillis());
    }

    /** The HUD passes its Total snapshot so live prediction uses the same wall-clock sample. */
    public long predictedFinishMillis(long elapsedMillis) {
        if (!running || config.get().predictionMode() == SplitsConfig.PredictionMode.PHASE_END) {
            return predictedFinishMillis;
        }
        if (!hasKnownFloor() || manualRun || futurePhaseBestsMillis < 0L
            || elapsedMillis < 0L || elapsedMillis > Long.MAX_VALUE - futurePhaseBestsMillis) return -1L;
        return elapsedMillis + futurePhaseBestsMillis;
    }

    public static String[] defaultSplitNames() {
        // Editor sample only: a live run always uses its own detected phases.
        return namesFor(7, true);
    }

    private void completeCurrentAndStart(String next, boolean timingKnown, String boundary) {
        long elapsed = currentTotalDurationMillis();
        if (hasCurrentSplit()) completeCurrent(elapsed, timingKnown, boundary, true);
        awaitingCompletionAfter = null;
        currentSplitName = next;
        startClockSegment(elapsed);
        refreshPrediction();
    }

    private void refreshPrediction() {
        predictedFinishMillis = -1L;
        futurePhaseBestsMillis = -1L;
        if (!running || !hasKnownFloor() || manualRun) return;
        int next = awaitingCompletionAfter == null ? indexOf(currentSplitName) : splitNames.length;
        if (next < 0) return;
        SplitsConfig settings = config.get();
        long future = 0L;
        for (int index = next + 1; index < splitNames.length; index++) {
            long best = settings.personalBestMillis(floor, masterMode, splitNames[index]);
            if (best < 0L || future > Long.MAX_VALUE - best) return;
            future += best;
        }
        futurePhaseBestsMillis = future;
        // Phase End includes the active phase's full PB. Live uses its elapsed time instead.
        long activeBest = next < splitNames.length
            ? settings.personalBestMillis(floor, masterMode, splitNames[next]) : 0L;
        if (activeBest < 0L || future > Long.MAX_VALUE - activeBest) return;
        long remaining = future + activeBest;
        // This boundary includes skipped phases; never add their PBs a second time.
        if (currentSplitStartMillis <= Long.MAX_VALUE - remaining) {
            predictedFinishMillis = currentSplitStartMillis + remaining;
        }
    }

    private void savePersonalBests() {
        // Late metadata can still upgrade F7 to M7. Commit with the final floor on
        // finish/exit, rather than permanently putting early splits in the wrong bucket.
        if (!manualRun && hasKnownFloor() && !personalBestCandidates.isEmpty()) {
            Map<String, Long> validPhases = new HashMap<>();
            for (String name : splitNames) {
                Long duration = personalBestCandidates.get(name);
                if (duration != null) validPhases.put(name, duration);
            }
            config.get().recordPersonalBests(floor, masterMode, validPhases);
        }
        personalBestCandidates.clear();
    }

    private long splitServerDurationMillis() {
        if (!serverClockAvailable) return -1L;
        return serverElapsedMillis - currentSplitStartServerMillis;
    }

    private long totalServerDurationMillis() {
        return serverClockAvailable ? serverElapsedMillis : -1L;
    }

    private void startClockSegment(long elapsed) {
        currentSplitStartMillis = elapsed;
        currentSplitStartServerMillis = serverElapsedMillis;
        largestPhaseTickGapMillis = 0L;
    }

    private void completeCurrent(long elapsed, boolean timingKnown, String boundary, boolean confirmed) {
        completed.add(new CompletedSplit(
            currentSplitName,
            timingKnown ? Math.max(0L, elapsed - currentSplitStartMillis) : -1L,
            elapsed,
            timingKnown ? splitServerDurationMillis() : -1L,
            totalServerDurationMillis()
        ));
        completedView = List.copyOf(completed);
        CompletedSplit split = completed.getLast();
        long duration = split.splitDurationMillis();
        PhaseMessage notice = phaseMessage(split);
        if (!manualRun && config.get().enabled() && duration > 0L) {
            personalBestCandidates.merge(currentSplitName, duration, Math::min);
            if (confirmed) phaseMessages.accept(notice);
        }
        tracePhase("phase-end", split, boundary);
    }

    private PhaseMessage phaseMessage(CompletedSplit split) {
        SplitsConfig settings = config.get();
        long previous = settings.personalBestMillis(floor, masterMode, split.name());
        Long candidate = personalBestCandidates.get(split.name());
        if (candidate != null && (previous < 0L || candidate < previous)) previous = candidate;
        boolean personalBest = hasKnownFloor() && split.splitDurationMillis() > 0L
            && (previous < 0L || split.splitDurationMillis() < previous);
        return new PhaseMessage(floor, masterMode, split.name(), split.splitDurationMillis(),
            previous, personalBest, settings.format());
    }

    private void finish(String boundary) {
        long elapsed = currentTotalDurationMillis();
        // A score banner also appears on a wipe, even inside the final phase.
        boolean finalPhaseConfirmed = awaitingCompletionAfter != null || !boundary.startsWith("Team Score:");
        if (hasCurrentSplit()) {
            if (indexOf(currentSplitName) == splitNames.length - 1) completeCurrent(elapsed, true, boundary, finalPhaseConfirmed);
            else captureStoppedCurrentSplit(elapsed, boundary);
            if (!finalPhaseConfirmed) {
                boolean bestEligible = personalBestCandidates.remove(currentSplitName) != null;
                if (!manualRun && hasKnownFloor() && stoppedCurrentSplit == null) {
                    pendingVictory = new PendingVictory(completed.getLast(), bestEligible, clock.getAsLong());
                }
            }
        }
        stoppedElapsedMillis = elapsed;
        running = false;
        predictedFinishMillis = finalPhaseConfirmed && !manualRun && hasKnownFloor() && stoppedCurrentSplit == null
            && !completed.isEmpty() && completed.getLast().name().equals(splitNames[splitNames.length - 1])
            ? elapsed : -1L;
        savePersonalBests();
        traceStopped("run-finished", boundary);
    }

    private boolean confirmPendingVictory(String message) {
        PendingVictory pending = pendingVictory;
        if (pending == null) return false;
        if (clock.getAsLong() - pending.scoreAtMillis() > VICTORY_CONFIRMATION_WINDOW_MILLIS) {
            pendingVictory = null;
            return false;
        }
        if (!DungeonLifecycleSignals.isVictoryForFloor(message, floor, masterMode)) return false;
        pendingVictory = null;
        if (pending.bestEligible()) {
            PhaseMessage notice = phaseMessage(pending.split());
            config.get().recordPersonalBests(floor, masterMode,
                Map.of(pending.split().name(), pending.split().splitDurationMillis()));
            phaseMessages.accept(notice);
        }
        // Confirm the existing score-boundary sample; never append a split or resume either clock.
        predictedFinishMillis = stoppedElapsedMillis;
        traceStopped("run-victory-confirmed", message);
        return true;
    }

    private void captureStoppedCurrentSplit(long elapsed, String boundary) {
        if (!hasCurrentSplit()) return;
        stoppedCurrentSplit = new CompletedSplit(currentSplitName,
            Math.max(0L, elapsed - currentSplitStartMillis), elapsed,
            splitServerDurationMillis(), totalServerDurationMillis());
        tracePhase("phase-stop", stoppedCurrentSplit, boundary);
    }

    private void tracePhase(String event, CompletedSplit split, String boundary) {
        diagnostics.accept(event + " name=" + split.name() + " wallMs=" + split.splitDurationMillis()
            + " serverMs=" + split.serverSplitDurationMillis()
            + " totalWallMs=" + split.totalDurationMillis() + " totalServerMs=" + split.serverTotalDurationMillis()
            + " phaseTicks=" + (serverElapsedMillis - currentSplitStartServerMillis) / 50L
            + " maxAppliedGapMs=" + largestPhaseTickGapMillis(split.totalDurationMillis())
            + " phaseStartTicks=" + currentSplitStartServerMillis / 50L
            + " totalTicks=" + serverElapsedMillis / 50L + " boundary=\"" + boundary + "\"");
    }

    private long largestPhaseTickGapMillis(long elapsed) {
        long gapStart = lastServerTickAtMillis != Long.MIN_VALUE ? lastServerTickAtMillis : startedAtMillis;
        return Math.max(largestPhaseTickGapMillis, startedAtMillis + elapsed - gapStart);
    }

    private void traceStopped(String event, String boundary) {
        diagnostics.accept(event + " totalWallMs=" + stoppedElapsedMillis
            + " totalServerMs=" + totalServerDurationMillis()
            + " totalTicks=" + serverElapsedMillis / 50L + " boundary=\"" + boundary + "\"" + personalBestDiagnostics());
    }

    private String personalBestDiagnostics() {
        SplitsConfig settings = config.get();
        List<String> missing = new ArrayList<>();
        for (String name : splitNames) {
            if (settings.personalBestMillis(floor, masterMode, name) < 0L) missing.add(name);
        }
        return " pbFloor=" + (hasKnownFloor() ? floor == 0 ? "Entrance" : (masterMode ? "M" : "F") + floor : "unknown")
            + " pbTracking=" + settings.enabled() + " predictionMode=" + settings.predictionMode()
            + " pbKnown=" + (splitNames.length - missing.size()) + "/" + splitNames.length
            + " pbMissing=\"" + String.join("|", missing) + "\"";
    }

    private boolean completeBoss(String finalSplitName, String boundary) {
        if (!hasCurrentSplit()) return false;
        long elapsed = currentTotalDurationMillis();
        completeCurrent(elapsed, currentSplitName.equals(finalSplitName), boundary, true);
        awaitingCompletionAfter = finalSplitName;
        // Keep this boundary so late M7 metadata can resume Relics at Necron's death.
        startClockSegment(elapsed);
        refreshPrediction();
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

    public record Timings(long splitMillis, long totalMillis, long serverSplitMillis, long serverTotalMillis) { }

    public record PhaseMessage(int floor, boolean masterMode, String phase, long durationMillis,
                               long previousBestMillis, boolean personalBest, SplitsConfig.TimeFormat format) { }

    private record PendingVictory(CompletedSplit split, boolean bestEligible, long scoreAtMillis) { }

    public record CompletedSplit(
        String name,
        long splitDurationMillis,
        long totalDurationMillis,
        long serverSplitDurationMillis,
        long serverTotalDurationMillis
    ) { }
}
