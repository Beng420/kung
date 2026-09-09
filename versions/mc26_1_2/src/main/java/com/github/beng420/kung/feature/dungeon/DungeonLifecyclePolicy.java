package com.github.beng420.kung.feature.dungeon;

public final class DungeonLifecyclePolicy {
    private DungeonLifecyclePolicy() {
    }

    public static Decision evaluate(
        boolean instanceActive,
        boolean realRunStarted,
        int missingTicks,
        int missingGraceTicks,
        Evidence evidence
    ) {
        boolean knownNonDungeon = evidence.knownNonDungeon();
        boolean detectedInstanceStart = evidence.detectedInstanceStart() && !knownNonDungeon;
        boolean activeCatacombs = evidence.activeCatacombs() && !knownNonDungeon;
        boolean stickyGridContext = instanceActive && evidence.insideDungeonGrid() && !knownNonDungeon;
        boolean activeRunBossMapContext = instanceActive
            && realRunStarted
            && evidence.showActiveRunWithoutContext()
            && !knownNonDungeon;
        boolean contextPresent = activeCatacombs
            || detectedInstanceStart
            || stickyGridContext
            || activeRunBossMapContext;
        int nextMissingTicks = contextPresent ? 0 : instanceActive ? missingTicks + 1 : missingTicks;
        boolean startInstance = !instanceActive && detectedInstanceStart;
        boolean activeAfterStart = instanceActive || startInstance;
        boolean reportContextLost = activeAfterStart && !contextPresent && realRunStarted;
        boolean endInstance = activeAfterStart
            && (knownNonDungeon || (!contextPresent && nextMissingTicks > missingGraceTicks));
        boolean activeAfterDecision = activeAfterStart && !endInstance;
        boolean stickyDungeonArea = activeAfterDecision
            && !knownNonDungeon
            && nextMissingTicks <= missingGraceTicks;
        boolean visibleArea = !knownNonDungeon
            && (activeCatacombs
                || detectedInstanceStart
                || stickyDungeonArea
                || activeRunBossMapContext);
        return new Decision(
            contextPresent,
            stickyGridContext,
            nextMissingTicks,
            startInstance,
            reportContextLost,
            endInstance,
            visibleArea
        );
    }

    public record Evidence(
        boolean detectedInstanceStart,
        boolean activeCatacombs,
        boolean insideDungeonGrid,
        boolean knownNonDungeon,
        boolean showActiveRunWithoutContext
    ) {
    }

    public record Decision(
        boolean contextPresent,
        boolean stickyGridContext,
        int missingTicks,
        boolean startInstance,
        boolean reportContextLost,
        boolean endInstance,
        boolean visibleArea
    ) {
    }
}
