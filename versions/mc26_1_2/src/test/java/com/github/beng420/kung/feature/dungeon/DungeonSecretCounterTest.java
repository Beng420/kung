package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public final class DungeonSecretCounterTest {
    @Test
    public void unavailablePersonalCountIsDifferentFromAnObservedZero() {
        DungeonPlayerStats stats = player(1);
        assertFalse(stats.hasSecretsFound());
        stats.setApiRunSecretsFound(-1);
        assertFalse(stats.hasSecretsFound());
        stats.setSecretsFound(0);
        assertTrue(stats.hasSecretsFound());
        assertEquals(0, stats.secretsFound());
        stats.resetRunCounters();
        assertFalse(stats.hasSecretsFound());
    }

    @Test
    public void exactTabReplacesInflatedApiAndRejectsLaterLowerPrioritySources() {
        DungeonPlayerStats stats = player(1);
        stats.setApiRunSecretsFound(412);
        assertEquals(412, stats.secretsFound());
        stats.setSecretsFound(8);
        stats.setApiRunSecretsFound(500);
        stats.setSyncedSecretsFound(90, 123L);
        stats.incrementSecrets(12);
        assertEquals(8, stats.secretsFound());
        assertEquals("PERSONAL_TAB", stats.secretsSource());
        stats.setSecretsFound(7); // Hypixel can correct a previous observation.
        assertEquals(7, stats.secretsFound());
    }

    @Test
    public void currentSelfReportOverridesApiAndRejectsOlderReports() {
        DungeonPlayerStats stats = player(1);
        stats.setApiRunSecretsFound(20);
        stats.setSyncedSecretsFound(9, 2_000L);
        stats.setSyncedSecretsFound(22, 1_000L);
        stats.setApiRunSecretsFound(40);
        assertEquals(9, stats.secretsFound());
        stats.setSyncedSecretsFound(8, 3_000L);
        assertEquals(8, stats.secretsFound());
        assertEquals("SELF_REPORT", stats.secretsSource());
        stats.setSecretsFound(0);
        assertEquals(0, stats.secretsFound());
    }

    @Test
    public void invalidSelfReportsDoNotCreateKnowledge() {
        DungeonPlayerStats stats = player(1);
        stats.setSyncedSecretsFound(-1, 1_000L);
        stats.setSyncedSecretsFound(12, 0L);
        assertFalse(stats.hasSecretsFound());
    }

    @Test
    public void identityMergeKeepsLatestExactCountWithoutAddingDuplicateSnapshots() {
        DungeonPlayerStats previousAlias = player(1);
        previousAlias.setSecretsFound(8);
        previousAlias.incrementDeaths(2);
        previousAlias.setRoomClearBounds(1, 4);
        DungeonPlayerStats resolvedIdentity = player(2);
        resolvedIdentity.setSecretsFound(7);
        resolvedIdentity.incrementDeaths(2);
        resolvedIdentity.setRoomClearBounds(2, 5);
        resolvedIdentity.merge(previousAlias);
        resolvedIdentity.merge(previousAlias);
        assertEquals(7, resolvedIdentity.secretsFound());
        assertEquals(2, resolvedIdentity.deaths());
        assertEquals(5, resolvedIdentity.roomsCleared());
        assertEquals(2, resolvedIdentity.soloRoomsCleared());
        previousAlias.setSecretsFound(10);
        resolvedIdentity.merge(previousAlias);
        assertEquals(10, resolvedIdentity.secretsFound());
    }

    @Test
    public void identityMergeHonorsProvenanceAndResetAllowsNewSources() {
        DungeonPlayerStats exact = player(1);
        exact.setSecretsFound(3);
        DungeonPlayerStats fallback = player(2);
        fallback.setApiRunSecretsFound(100);
        exact.merge(fallback);
        assertEquals(3, exact.secretsFound());
        fallback.merge(exact);
        assertEquals(3, fallback.secretsFound());
        fallback.resetRunCounters();
        fallback.setSyncedSecretsFound(1, 1_000L);
        assertEquals(1, fallback.secretsFound());
        assertEquals("SELF_REPORT", fallback.secretsSource());
    }

    @Test
    public void missingOrRegressedLifetimeTotalsCannotBecomeRunSecrets() {
        assertEquals(-1, DungeonApiEnrichment.runSecretDelta(null, 15_000));
        assertEquals(-1, DungeonApiEnrichment.runSecretDelta(-1, 15_000));
        assertEquals(-1, DungeonApiEnrichment.runSecretDelta(15_000, -1));
        assertEquals(-1, DungeonApiEnrichment.runSecretDelta(15_000, 14_999));
        assertEquals(0, DungeonApiEnrichment.runSecretDelta(15_000, 15_000));
        assertEquals(17, DungeonApiEnrichment.runSecretDelta(15_000, 15_017));
    }

    private static DungeonPlayerStats player(long id) {
        return new DungeonPlayerStats(new UUID(0, id), "Player" + id);
    }
}
