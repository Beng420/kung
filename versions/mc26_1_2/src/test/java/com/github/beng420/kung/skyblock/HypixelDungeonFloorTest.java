package com.github.beng420.kung.skyblock;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public final class HypixelDungeonFloorTest {
    @Test public void readsCurrentScoreboardAndTabFloorFieldsIncludingEntranceAndMasterMode() {
        assertEquals(new HypixelDungeonFloor(1, false), HypixelDungeonFloor.fromLines(List.of("SKYBLOCK", "§cThe Catacombs (F1)", "Time Elapsed: 0m 0s")));
        assertEquals(new HypixelDungeonFloor(7, true), HypixelDungeonFloor.fromLine("Dungeon: The Catacombs (M7)"));
        assertEquals(new HypixelDungeonFloor(0, false), HypixelDungeonFloor.fromLine("⏣ The Catacombs (E)"));
        assertEquals(new HypixelDungeonFloor(1, false), HypixelDungeonFloor.fromLine("The Catacombs - Floor I"));
        assertEquals(new HypixelDungeonFloor(7, true), HypixelDungeonFloor.fromLine("Master Mode The Catacombs - Floor VII"));
        assertEquals(new HypixelDungeonFloor(6, false), HypixelDungeonFloor.fromLine("Location: The Catacombs - Floor VI"));
        assertTrue(HypixelDungeonFloor.fromLine("The Catacombs - Entrance").known());
        assertFalse(HypixelDungeonFloor.fromLine("The Catacombs").known());
    }

    @Test public void parsesTheActualServerEntryBannerBeforeTheStartRoomSidebarHasAFloor() {
        assertEquals(new HypixelDungeonFloor(1, false), HypixelDungeonFloor.fromEntryMessage(
            "----------------------------- [MVP+] Beng114 entered The Catacombs, Floor I! -----------------------------"));
        assertEquals(new HypixelDungeonFloor(7, true), HypixelDungeonFloor.fromEntryMessage(
            "§r§e----------------------------- §b[MVP++] Exlusiv §eentered MM The Catacombs, Floor VII! -----------------------------"));
        assertEquals(new HypixelDungeonFloor(0, false), HypixelDungeonFloor.fromEntryMessage(
            "[VIP] Beng114 entered The Catacombs, Entrance!"));
        assertEquals(new HypixelDungeonFloor(6, true), HypixelDungeonFloor.fromEntryMessage(
            "Beng114 entered Master Mode The Catacombs, Floor VI!"));
    }

    @Test public void rejectsPlayerMessagesEquipmentMentionsAndUnrelatedDungeonAreas() {
        for (String message : List.of(
            "Party > [MVP+] Beng114: entered The Catacombs, Floor I!",
            "[MVP+] Beng114: Exlusiv entered MM The Catacombs, Floor VII!",
            "Your friend Beng114 entered The Catacombs, Floor I!",
            "Equipped: The Catacombs (M7)",
            "Dungeon: Kuudra's Hollow (T5)",
            "Dungeon Hub",
            "The Catacombs - Floor VIII",
            "A player entered The Catacombs, Floor I! later"
        )) {
            assertFalse(message, HypixelDungeonFloor.fromEntryMessage(message).known());
            assertFalse(message, HypixelDungeonFloor.fromLine(message).known());
        }
    }

    @Test public void conflictingPacketRowsCannotChooseAnArbitraryFloor() {
        assertEquals(HypixelDungeonFloor.UNKNOWN, HypixelDungeonFloor.fromLines(List.of("The Catacombs (F1)", "The Catacombs (M7)")));
        assertEquals(new HypixelDungeonFloor(1, false), HypixelDungeonFloor.fromLines(List.of("The Catacombs (F1)", "Dungeon: The Catacombs - Floor I")));
        assertEquals(HypixelDungeonFloor.UNKNOWN, HypixelDungeonFloor.fromLines(List.of("The Catacombs", "Dungeon Hub")));
    }
}
