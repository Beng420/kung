package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DungeonScoreCalculatorTest {
    @Test
    public void appliesDeathAndPuzzlePenalties() {
        assertEquals(0, DungeonScoreCalculator.deathPenalty(0));
        assertEquals(1, DungeonScoreCalculator.deathPenalty(1));
        assertEquals(5, DungeonScoreCalculator.deathPenalty(3));
        assertEquals(85, DungeonScoreCalculator.skillScore(80, 1, 1));
        assertEquals(20, DungeonScoreCalculator.skillScore(0, 10, 10));
    }

    @Test
    public void calculatesSecretRequirementsAndScores() {
        assertEquals(30.0, DungeonScoreCalculator.requiredSecretsPercent(1, false), 0.0);
        assertEquals(85.0, DungeonScoreCalculator.requiredSecretsPercent(6, false), 0.0);
        assertEquals(100.0, DungeonScoreCalculator.requiredSecretsPercent(1, true), 0.0);
        assertEquals(20, DungeonScoreCalculator.secretScoreFromPercent(50.0, 100.0));
        assertEquals(40, DungeonScoreCalculator.secretScoreFromPercent(120.0, 100.0));
        assertEquals(20, DungeonScoreCalculator.secretScoreFromCount(50, 100, 100.0));
    }

    @Test
    public void capsBonusScoreComponents() {
        assertEquals(9, DungeonScoreCalculator.bonusScore(8, true, false, true, true));
        assertEquals(2, DungeonScoreCalculator.bonusScore(0, false, true, false, false));
    }

    @Test
    public void preservesSpeedScoreBoundaries() {
        long grace = DungeonScoreCalculator.speedGraceSeconds(7, false);
        assertEquals(14L * 60L, grace);
        assertEquals(100, DungeonScoreCalculator.speedScore(grace - 1L, grace));
        assertEquals(100, DungeonScoreCalculator.speedScore(grace, grace));
        assertEquals(90, DungeonScoreCalculator.speedScore(grace + grace / 5L, grace));
        assertEquals(0, DungeonScoreCalculator.speedScore(grace * 20L, grace));
        assertEquals(10L * 60L, DungeonScoreCalculator.speedGraceSeconds(6, true));
    }
}
