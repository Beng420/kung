package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SuperpairsBoardTest {
    @Test
    public void changingGlassPromptsNeverBecomeBonuses() {
        SuperpairsBoard board = fullBoard();
        long seededRevision = board.revision();
        for (String prompt : new String[] { "?", "Click any button!", "Click a second button!", "Next button is instantly rewarded!" }) {
            assertEquals(SuperpairsBoard.Kind.MARKER, board.observe(54, 10, "minecraft:cyan_stained_glass", prompt, 1));
        }
        assertEquals(seededRevision, board.revision());
        assertEquals(28, board.boardSlotCount());
        assertEquals(0, board.bonusSlotCount());
        assertEquals(28, board.summary().unknownFields());
        assertEquals(-1, board.summary().exactTotalPairs());
    }

    @Test
    public void loggedInstantFindRevealsOnlyOneBonusAndBothRewardSlots() {
        SuperpairsBoard board = fullBoard();
        board.observe(54, 11, "pink_dye", "147k Enchanting Exp", 1);
        board.observe(54, 12, "player_head", "Titanic Experience Bottle", 1);
        board.observe(54, 13, "purple_dye", "32k Enchanting Exp", 1);
        board.observe(54, 10, "cyan_stained_glass", "Click a second button!", 1);
        board.observe(54, 10, "cyan_stained_glass", "Next button is instantly rewarded!", 1);
        board.observe(54, 14, "diamond", "Instant Find", 1);
        board.observe(54, 15, "cyan_stained_glass", "Next button is instantly rewarded!", 1);
        board.observe(54, 15, "bone_meal", "133k Enchanting Exp", 1);
        board.observe(54, 28, "bone_meal", "133k Enchanting Exp", 1);
        assertEquals(1, board.bonusSlotCount());
        assertEquals(5, board.rewardSlotCount());
        assertEquals(1, board.summary().knownPairs());
        assertEquals(3, board.summary().singleCards());
        assertEquals(22, board.summary().unknownFields());
        assertEquals(13, board.summary().maximumTotalPairs());
        assertEquals(-1, board.summary().exactTotalPairs());
    }

    @Test
    public void hiddenOrEmptySlotsKeepTheirRewardsAndBoardMembership() {
        SuperpairsBoard board = fullBoard();
        board.observe(54, 11, "pink_dye", "147k Enchanting Exp", 1);
        long revealedRevision = board.revision();
        board.observe(54, 11, "cyan_stained_glass", "Click any button!", 1);
        board.observe(54, 11, "air", "", 0);
        board.observe(54, 12, "gray_stained_glass_pane", " ", 1);
        assertEquals(28, board.boardSlotCount());
        assertEquals(1, board.cards().size());
        assertEquals("147k Enchanting Exp", board.cards().getFirst().label());
        assertEquals(revealedRevision, board.revision());
    }

    @Test
    public void duplicatePacketsDoNotInventMatchingCardsAndCachedSummariesStayStable() {
        SuperpairsBoard board = fullBoard();
        board.observe(54, 11, "pink_dye", "147k Enchanting Exp", 1);
        var cards = board.cards();
        var summary = board.summary();
        long revision = board.revision();
        board.observe(54, 11, "minecraft:pink_dye", "147k Enchanting Exp", 1);
        assertEquals(revision, board.revision());
        assertSame(cards, board.cards());
        assertSame(summary, board.summary());
        assertEquals(0, summary.knownPairs());
        assertEquals(1, summary.singleCards());
    }

    @Test
    public void fourEqualRewardsAreTwoPairsAndFiveLeaveOneSingle() {
        SuperpairsBoard board = fullBoard();
        for (int slot = 10; slot <= 13; slot++) {
            board.observe(54, slot, "pink_dye", "147k Enchanting Exp", 1);
        }
        assertEquals(4, board.cards().getFirst().count());
        assertEquals(2, board.summary().knownPairs());
        assertEquals(0, board.summary().singleCards());
        assertTrue(board.cards().getFirst().complete());
        board.observe(54, 14, "pink_dye", "147k Enchanting Exp", 1);
        assertEquals(5, board.cards().getFirst().count());
        assertEquals(2, board.summary().knownPairs());
        assertEquals(1, board.summary().singleCards());
        assertFalse(board.cards().getFirst().complete());
    }

    @Test
    public void differentRewardAmountsOrEnchantmentsStaySeparate() {
        SuperpairsBoard board = fullBoard();
        board.observe(54, 10, "enchanted_book", "§9Growth VI", 1);
        board.observe(54, 11, "enchanted_book", "Growth VII", 1);
        board.observe(54, 12, "experience_bottle", "Experience Bottle", 1);
        board.observe(54, 13, "experience_bottle", "Experience Bottle", 2);
        board.observe(54, 14, "minecraft:enchanted_book", "Growth  VI", 1);
        assertEquals(4, board.cards().size());
        assertEquals(1, board.summary().knownPairs());
        assertEquals(3, board.summary().singleCards());
    }

    @Test
    public void onlySupportedInteriorSlotsCanContributeCards() {
        SuperpairsBoard board = new SuperpairsBoard();
        for (int slot : new int[] { -1, 0, 4, 9, 17, 18, 26, 27, 35, 36, 44, 45, 49, 53, 54, 64 }) {
            assertEquals(SuperpairsBoard.Kind.IGNORED, board.observe(54, slot, "diamond", "Instant Find", 1));
        }
        assertEquals(SuperpairsBoard.Kind.IGNORED, board.observe(27, 10, "pink_dye", "147k Enchanting Exp", 1));
        assertEquals(0, board.boardSlotCount());
        board.observe(54, 10, "gray_stained_glass_pane", " ", 1);
        board.observe(54, 11, "clock", "Timer: 88s", 64);
        assertEquals(0, board.boardSlotCount());
        board.observe(54, 37, "cyan_stained_glass", "?", 1);
        board.observe(54, 43, "cyan_stained_glass", "?", 1);
        assertEquals(2, board.boardSlotCount());
    }

    @Test
    public void concealedBonusesKeepTotalUncertainAndNewBoardResetClearsEverything() {
        SuperpairsBoard board = new SuperpairsBoard();
        board.observe(54, 10, "cyan_stained_glass", "?", 1);
        board.observe(54, 11, "cyan_stained_glass", "?", 1);
        board.observe(54, 12, "cyan_stained_glass", "?", 1);
        board.observe(54, 13, "cyan_stained_glass", "?", 1);
        board.observe(54, 10, "pink_dye", "147k Enchanting Exp", 1);
        board.observe(54, 11, "pink_dye", "147k Enchanting Exp", 1);
        assertEquals(-1, board.summary().exactTotalPairs());
        assertEquals(2, board.summary().maximumTotalPairs());
        board.observe(54, 12, "diamond", "Instant Find", 1);
        board.observe(54, 13, "diamond", "Extra Clicks", 1);
        assertEquals(1, board.summary().exactTotalPairs());
        assertEquals(1, board.summary().maximumTotalPairs());
        board.reset();
        assertEquals(0, board.boardSlotCount());
        assertEquals(0, board.bonusSlotCount());
        assertTrue(board.cards().isEmpty());
        assertEquals(0, board.summary().knownPairs());
        assertEquals(-1, board.summary().exactTotalPairs());
    }

    private static SuperpairsBoard fullBoard() {
        SuperpairsBoard board = new SuperpairsBoard();
        for (int slot = 0; slot < 54; slot++) {
            if (SuperpairsBoard.isBoardSlot(54, slot)) {
                board.observe(54, slot, "cyan_stained_glass", "?", 1);
            }
        }
        return board;
    }
}
