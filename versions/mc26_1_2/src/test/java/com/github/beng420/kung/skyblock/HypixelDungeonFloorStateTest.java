package com.github.beng420.kung.skyblock;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public final class HypixelDungeonFloorStateTest {
    private static final HypixelLocation CATACOMBS = new HypixelLocation(HypixelLocation.Kind.CATACOMBS, "The Catacombs");
    private static final HypixelLocation HUB = new HypixelLocation(HypixelLocation.Kind.DUNGEON_HUB, "Dungeon Hub");
    private static final HypixelLocation KUUDRA = new HypixelLocation(HypixelLocation.Kind.KUUDRA, "Kuudra's Hollow");
    private static final String FLOOR_ONE_ENTRY = "----------------------------- [MVP+] Beng114 entered The Catacombs, Floor I! -----------------------------";

    @Test public void entryHintRequiresBothALaterTransferAndConfirmedCatacombs() {
        HypixelDungeonFloorState state = new HypixelDungeonFloorState();
        state.entryMessage(FLOOR_ONE_ENTRY, 1_000L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_001L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        state.worldChanged(1_002L);
        state.observe(HypixelLocation.UNKNOWN, List.of("The Catacombs (F1)"), 1_003L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_004L);
        assertEquals(new HypixelDungeonFloor(1, false), state.current());
    }

    @Test public void lobbyUpdatesAndMultipleWorldHooksCannotConsumeTheNextTransferHintEarly() {
        HypixelDungeonFloorState state = new HypixelDungeonFloorState();
        state.entryMessage(FLOOR_ONE_ENTRY, 1_000L);
        state.observe(HUB, List.of("Dungeon Hub"), 1_001L);
        state.worldChanged(1_002L);
        state.worldChanged(1_003L);
        state.observe(HypixelLocation.UNKNOWN, List.of(), 1_004L);
        state.worldChanged(1_005L);
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_006L);
        assertEquals(new HypixelDungeonFloor(1, false), state.current());
    }

    @Test public void visibleDestinationFloorOverridesTheEntryHintIncludingEntrance() {
        HypixelDungeonFloorState state = new HypixelDungeonFloorState();
        state.entryMessage(FLOOR_ONE_ENTRY, 1_000L);
        state.worldChanged(1_001L);
        state.observe(CATACOMBS, List.of("The Catacombs (M7)"), 1_002L);
        assertEquals(new HypixelDungeonFloor(7, true), state.current());
        state.observe(CATACOMBS, List.of("The Catacombs (E)"), 1_003L);
        assertEquals(new HypixelDungeonFloor(0, false), state.current());
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_004L);
        assertEquals(new HypixelDungeonFloor(0, false), state.current());
    }

    @Test public void newEntryBannerDoesNotOverwriteTheCurrentRun() {
        HypixelDungeonFloorState state = new HypixelDungeonFloorState();
        state.observe(CATACOMBS, List.of("The Catacombs (M7)"), 1_000L);
        state.entryMessage(FLOOR_ONE_ENTRY, 1_001L);
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_002L);
        assertEquals(new HypixelDungeonFloor(7, true), state.current());
        state.worldChanged(1_003L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_004L);
        assertEquals(new HypixelDungeonFloor(1, false), state.current());
    }

    @Test public void unrelatedDestinationCancelsTheHintAndTheCurrentFloor() {
        for (HypixelLocation destination : List.of(HUB, KUUDRA)) {
            HypixelDungeonFloorState state = new HypixelDungeonFloorState();
            state.observe(CATACOMBS, List.of("The Catacombs (M7)"), 1_000L);
            state.entryMessage(FLOOR_ONE_ENTRY, 1_001L);
            state.worldChanged(1_002L);
            state.observe(destination, List.of(destination.name()), 1_003L);
            assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
            state.worldChanged(1_004L);
            state.observe(CATACOMBS, List.of("The Catacombs"), 1_005L);
            assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        }
    }

    @Test public void disconnectAndExpiredHintsCannotSeedALaterInstance() {
        HypixelDungeonFloorState disconnected = new HypixelDungeonFloorState();
        disconnected.entryMessage(FLOOR_ONE_ENTRY, 1_000L);
        disconnected.worldChanged(1_001L);
        disconnected.reset();
        disconnected.worldChanged(1_002L);
        disconnected.observe(CATACOMBS, List.of("The Catacombs"), 1_003L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, disconnected.current());

        HypixelDungeonFloorState expired = new HypixelDungeonFloorState();
        expired.entryMessage(FLOOR_ONE_ENTRY, 1_000L);
        expired.worldChanged(1_001L);
        expired.observe(CATACOMBS, List.of("The Catacombs"), 31_001L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, expired.current());

        expired.entryMessage(FLOOR_ONE_ENTRY, 40_000L);
        expired.worldChanged(70_001L);
        expired.observe(CATACOMBS, List.of("The Catacombs"), 70_002L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, expired.current());
    }

    @Test public void consumedHintAndObservedFloorBelongToExactlyOneInstance() {
        HypixelDungeonFloorState state = new HypixelDungeonFloorState();
        state.entryMessage(FLOOR_ONE_ENTRY, 1_000L);
        state.worldChanged(1_001L);
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_002L);
        assertEquals(new HypixelDungeonFloor(1, false), state.current());
        state.worldChanged(1_003L);
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_004L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        state.observe(CATACOMBS, List.of("The Catacombs (M7)"), 1_005L);
        state.worldChanged(1_006L);
        state.observe(CATACOMBS, List.of("The Catacombs"), 1_007L);
        assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
    }
}
