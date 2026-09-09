package com.github.beng420.kung.feature.dungeon;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DungeonAnnouncementsTest {
    @Test
    public void buildsConfiguredDeathPartyMessages() {
        String individual = "Steve died 2 times";
        String combined = individual + " | Total Deaths: 3";

        assertEquals(combined, DungeonAnnouncements.deathPartyMessage(individual, combined, 3, true, true));
        assertEquals("Total Deaths: 3", DungeonAnnouncements.deathPartyMessage(individual, combined, 3, true, false));
        assertEquals(individual, DungeonAnnouncements.deathPartyMessage(individual, combined, 3, false, true));
        assertEquals("", DungeonAnnouncements.deathPartyMessage(individual, combined, 3, false, false));
    }
}
