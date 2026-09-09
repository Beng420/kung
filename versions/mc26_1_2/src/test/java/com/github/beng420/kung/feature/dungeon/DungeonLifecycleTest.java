package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DungeonLifecycleTest {
    @Test
    public void recognizesFormattedLifecycleMessages() {
        assertTrue(DungeonLifecycleSignals.isRunStart("  \u00a7aStarting   in 1 second.  "));
        assertTrue(DungeonLifecycleSignals.isRunFinished("\u00a7r\u00a7cS Defeated Necron in 05m 42s (NEW RECORD!)"));
        assertFalse(DungeonLifecycleSignals.isRunStart("Starting in 2 seconds."));
        assertFalse(DungeonLifecycleSignals.isRunFinished("Necron was defeated"));
    }

    @Test
    public void startsAnInactiveDetectedInstance() {
        DungeonLifecyclePolicy.Decision decision = evaluate(false, false, 0, evidence(true, true, true, false));

        assertTrue(decision.startInstance());
        assertTrue(decision.contextPresent());
        assertTrue(decision.visibleArea());
        assertEquals(0, decision.missingTicks());
        assertFalse(decision.reportContextLost());
        assertFalse(decision.endInstance());
    }

    @Test
    public void keepsAnActiveRunVisibleDuringTheGraceWindow() {
        DungeonLifecyclePolicy.Decision firstMissingTick = evaluate(true, true, 0, evidence(false, false, false, false));
        DungeonLifecyclePolicy.Decision lastGraceTick = evaluate(true, true, 79, evidence(false, false, false, false));
        DungeonLifecyclePolicy.Decision expired = evaluate(true, true, 80, evidence(false, false, false, false));

        assertTrue(firstMissingTick.reportContextLost());
        assertTrue(firstMissingTick.visibleArea());
        assertFalse(firstMissingTick.endInstance());
        assertEquals(80, lastGraceTick.missingTicks());
        assertTrue(lastGraceTick.visibleArea());
        assertEquals(81, expired.missingTicks());
        assertTrue(expired.endInstance());
        assertFalse(expired.visibleArea());
    }

    @Test
    public void dungeonGridKeepsContextSticky() {
        DungeonLifecyclePolicy.Decision decision = evaluate(true, true, 35, evidence(false, false, true, false));

        assertTrue(decision.stickyGridContext());
        assertTrue(decision.contextPresent());
        assertTrue(decision.visibleArea());
        assertEquals(0, decision.missingTicks());
        assertFalse(decision.reportContextLost());
    }

    @Test
    public void knownNonDungeonBlocksFalseDungeonStart() {
        DungeonLifecyclePolicy.Decision decision = evaluate(false, false, 0, evidence(true, false, true, true));

        assertFalse(decision.startInstance());
        assertFalse(decision.contextPresent());
        assertFalse(decision.visibleArea());
        assertFalse(decision.endInstance());
    }

    @Test
    public void knownNonDungeonEndsActiveDungeonImmediately() {
        DungeonLifecyclePolicy.Decision decision = evaluate(true, true, 0, evidence(true, false, true, true));

        assertFalse(decision.contextPresent());
        assertTrue(decision.endInstance());
        assertFalse(decision.visibleArea());
    }

    @Test
    public void bossMapKeepsActiveRunVisibleWithoutCatacombsContext() {
        DungeonLifecyclePolicy.Decision decision = evaluate(true, true, 35, evidence(false, false, false, false, true));

        assertTrue(decision.contextPresent());
        assertTrue(decision.visibleArea());
        assertEquals(0, decision.missingTicks());
        assertFalse(decision.reportContextLost());
        assertFalse(decision.endInstance());
    }

    @Test
    public void bossMapDoesNotStartInactiveInstance() {
        DungeonLifecyclePolicy.Decision decision = evaluate(false, false, 0, evidence(false, false, false, false, true));

        assertFalse(decision.startInstance());
        assertFalse(decision.contextPresent());
        assertFalse(decision.visibleArea());
    }

    @Test
    public void recognizesKuudraAsKnownNonDungeonInstance() {
        assertTrue(DungeonRunDetector.isKnownNonDungeonInstanceLine("Location: Kuudra's Hollow"));
        assertTrue(DungeonRunDetector.isKnownNonDungeonInstanceLine("Kuudra"));
        assertFalse(DungeonRunDetector.isKnownNonDungeonInstanceLine("The Catacombs"));
    }

    private static DungeonLifecyclePolicy.Decision evaluate(
        boolean active,
        boolean realRunStarted,
        int missingTicks,
        DungeonLifecyclePolicy.Evidence evidence
    ) {
        return DungeonLifecyclePolicy.evaluate(active, realRunStarted, missingTicks, 80, evidence);
    }

    private static DungeonLifecyclePolicy.Evidence evidence(
        boolean detected,
        boolean activeCatacombs,
        boolean insideGrid,
        boolean knownNonDungeon
    ) {
        return evidence(detected, activeCatacombs, insideGrid, knownNonDungeon, false);
    }

    private static DungeonLifecyclePolicy.Evidence evidence(
        boolean detected,
        boolean activeCatacombs,
        boolean insideGrid,
        boolean knownNonDungeon,
        boolean showActiveRunWithoutContext
    ) {
        return new DungeonLifecyclePolicy.Evidence(
            detected,
            activeCatacombs,
            insideGrid,
            knownNonDungeon,
            showActiveRunWithoutContext
        );
    }
}
