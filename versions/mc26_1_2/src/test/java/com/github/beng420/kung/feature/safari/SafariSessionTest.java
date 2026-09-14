package com.github.beng420.kung.feature.safari;

import com.github.beng420.kung.skyblock.HypixelInstanceState;
import com.github.beng420.kung.skyblock.HypixelLocation;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class SafariSessionTest {
    @Test
    public void suppliedCapturesCountSpeciesOnceRegardlessOfShardOrChatRepeatCounts() {
        SafariSession session = active();
        assertTrue(session.observeMessage("[22:41:11] CAPTURE! You caught a Scrappy and gained 2x Scrappy Shard!", "Beng114"));
        assertTrue(session.observeMessage("§8[22:43:55] §aCAPTURE! §fYou caught a Driftling and gained a Driftling Shard!", "Beng114"));
        assertTrue(session.observeMessage("[22:44:27] CAPTURE! You caught a Flitter and gained 2x Flitter Shard!", "Beng114"));
        assertTrue(session.observeMessage("[22:44:32] CAPTURE! You caught a Cavernfish and gained a Cavernfish Shard! (×3)", "Beng114"));
        assertFalse(session.observeMessage("[22:44:55] CAPTURE! You caught a Flitter and gained a Flitter Shard!", "Beng114"));
        assertTrue(session.observeMessage("[22:46:04] CAPTURE! You caught a Chuckwalla and gained a Chuckwalla Shard!", "Beng114"));
        assertEquals(Set.of("Scrappy", "Driftling", "Flitter", "Cavernfish", "Chuckwalla"), session.caught());
        assertEquals(5, SafariOverlayFeature.count(SafariCritters.Region.CAVERN, session.caught()));
        assertEquals(0, SafariOverlayFeature.count(SafariCritters.Region.FOREST, session.caught()));
    }

    @Test
    public void suppliedLootShareIncludesFindingHideyhoAndDeduplicatesPartyCatches() {
        SafariSession session = active();
        assertTrue(session.observeMessage("[22:41:09] LOOT SHARE! You received a Hideyho Shard from Giushki finding the Hideyho!", "Beng114"));
        assertTrue(session.observeMessage("LOOT SHARE! You received 2x Tepid Shard from Giushki catching a Tepid!", "Beng114"));
        assertTrue(session.observeMessage("LOOT SHARE! You received a Mantis Shrimp Shard from Giushki catching a Mantis Shrimp! (×2)", "Beng114"));
        assertTrue(session.observeMessage("LOOT SHARE! You received a Honeybug Shard from Duckboyfi catching a Honeybug!", "Beng114"));
        assertFalse(session.observeMessage("LOOT SHARE! You received 2x Honeybug Shard from Giushki catching a Honeybug!", "Beng114"));
        assertEquals(4, session.caught().size());
    }

    @Test
    public void sparklingAndHideyhoRewardsMapToTheirBaseSpecies() {
        assertEquals("Hideyho", SafariSession.capturedCritter(
            "CAPTURE! You found Hideyho, and as a reward he gave you 2x Hideyho Shard!"));
        assertEquals("Hideyho", SafariSession.capturedCritter(
            "LOOT SHARE! You received a Hideyho Shard from Giushki finding Hideyho!"));
        assertEquals("Bluebird", SafariSession.capturedCritter(
            "LOOT SHARE! You received a Rainbow Feather and 10x Bluebird Shard from Giushki catching a SPARKLING Bluebird!"));
        assertEquals("Flitter", SafariSession.capturedCritter(
            "CAPTURE! You caught a SPARKLING Flitter and gained a Rainbow Feather and 10x Flitter Shard!"));
    }

    @Test
    public void attemptsEscapesFoodFloorDropsAndPlayerQuotesNeverCount() {
        SafariSession session = active();
        for (String line : List.of(
            "FLOOR DROP! You found Shyworm Shard on the ground!",
            "You threw a Critter Capsule at the Chuckwalla! (×3)",
            "The Chuckwalla escaped your Critter Capsule! (×2)",
            "The Chuckwalla is cornered! Catch it!",
            "The Scrappy ate the Lush Lily Pad and came out of its shell!",
            "Party > [MVP+] Beng114: CAPTURE! You caught a Flitter and gained a Flitter Shard!",
            "[MVP+] Player: LOOT SHARE! You received a Tepid Shard from Giushki catching a Tepid!",
            "From Player: SAFARI REWARD SUMMARY",
            "LOOT SHARE! You received a Flitter Shard from Giushki fighting a Flitter!",
            "CAPTURE! You caught a Nonexistent Critter and gained a Nonexistent Critter Shard!",
            "CAPTURE! You caught a Flitter and gained nothing!",
            "x".repeat(1_025))) {
            assertFalse(line, session.observeMessage(line, "Beng114"));
        }
        assertTrue(session.active());
        assertTrue(session.caught().isEmpty());
    }

    @Test
    public void packetWorldChangeAndServerChangeBothInvalidatePreviousUniques() {
        HypixelInstanceState instance = new HypixelInstanceState();
        SafariSession session = new SafariSession();
        instance.beginWorld();
        instance.observe(new HypixelLocation(HypixelLocation.Kind.SKYBLOCK, "Critter Safari"), "mini1a");
        session.select(instance.epoch(), instance.location().name());
        assertTrue(session.observeMessage(catchFlitter(), "Beng114"));
        instance.beginWorld();
        session.select(instance.epoch(), instance.location().name());
        assertFalse(session.active());
        assertTrue(session.caught().isEmpty());
        instance.observe(new HypixelLocation(HypixelLocation.Kind.SKYBLOCK, "Critter Safari"), "mini1a");
        session.select(instance.epoch(), instance.location().name());
        assertTrue(session.observeMessage(catchFlitter(), "Beng114"));
        instance.observe(new HypixelLocation(HypixelLocation.Kind.SKYBLOCK, "Critter Safari"), "mini2b");
        session.select(instance.epoch(), instance.location().name());
        assertFalse(session.active());
        assertTrue(session.caught().isEmpty());
    }

    @Test
    public void missingRowsBiomeChangesAndOtherPlayersJoiningPreserveTheRun() {
        SafariSession session = active();
        session.observeMessage(catchFlitter(), "Beng114");
        for (String location : List.of("", "Cavern Biome", "Forest Biome", "Critter Safari")) {
            session.select(1, location);
            assertTrue(session.active());
            assertEquals(Set.of("Flitter"), session.caught());
        }
        session.observeMessage("[MVP+] Duckboyfi entered Critter Safari!", "Beng114");
        session.observeMessage("[MVP+] Beng114 entered Critter Safari!", "Beng114");
        session.observeMessage("HEAD START! You started with an Orange Gem!", "Beng114");
        assertEquals(Set.of("Flitter"), session.caught());
    }

    @Test
    public void localStartBridgesMissingLocationButOtherPlayersAndEntranceDoNotActivate() {
        SafariSession session = new SafariSession();
        session.select(1, "");
        session.observeMessage("[MVP+] Duckboyfi entered Critter Safari!", "Beng114");
        assertFalse(session.active());
        assertFalse(session.observeMessage(catchFlitter(), "Beng114"));
        session.observeMessage("[22:41:19] [MVP+] Beng114 entered Critter Safari!", "Beng114");
        assertTrue(session.active());
        assertTrue(session.observeMessage(catchFlitter(), "Beng114"));
        for (String outside : List.of("Hub", "Torrhus Canyon", "Critter Safari Entrance", "Dungeon Hub")) {
            session.select(2, outside);
            session.observeMessage("[NPC] Safari Manager: Looks good to me. Have fun out there!", "Beng114");
            assertFalse(outside, session.active());
            assertFalse(session.observeMessage(catchFlitter(), "Beng114"));
        }
    }

    @Test
    public void headStartAndManagerCanActivateBeforeSidebarArrives() {
        for (String message : List.of("HEAD START! You started with a Purple Gem!",
            "[NPC] Safari Manager: Looks good to me. Have fun out there!")) {
            SafariSession session = new SafariSession();
            session.select(1, "");
            session.observeMessage(message, "Beng114");
            assertTrue(session.active());
            assertTrue(session.observeMessage(catchFlitter(), "Beng114"));
            session.select(1, "Critter Safari");
            assertEquals(Set.of("Flitter"), session.caught());
        }
    }

    @Test
    public void summaryHidesHudUntilNextInstanceAndDisconnectClearsEverything() {
        SafariSession session = active();
        session.observeMessage(catchFlitter(), "Beng114");
        session.observeMessage("[22:41:21] SAFARI REWARD SUMMARY", "Beng114");
        assertFalse(session.active());
        session.select(1, "Cavern Biome");
        session.observeMessage("HEAD START! You started with an Orange Gem!", "Beng114");
        assertFalse(session.active());
        assertFalse(session.observeMessage(catchFlitter(), "Beng114"));
        session.select(2, "Critter Safari");
        assertTrue(session.active());
        assertTrue(session.caught().isEmpty());
        assertTrue(session.observeMessage(catchFlitter(), "Beng114"));
        session.reset();
        assertFalse(session.active());
        assertTrue(session.caught().isEmpty());
    }

    @Test
    public void catalogueHasAll37SpeciesInFourDistinctRegionsIncludingMacaw() {
        Set<String> names = new java.util.HashSet<>();
        assertEquals(4, SafariCritters.Region.values().length);
        for (SafariCritters.Region region : SafariCritters.Region.values()) {
            assertEquals(region == SafariCritters.Region.HAUNTED ? 10 : 9, region.critters().size());
            for (String name : region.critters()) assertTrue(name, names.add(name));
        }
        assertEquals(37, names.size());
        assertTrue(SafariCritters.Region.FOREST.critters().contains("Macaw"));
        assertEquals("Mantis Shrimp", SafariCritters.canonical(" SPARKLING Mantis Shrimp "));
    }

    private static SafariSession active() {
        SafariSession session = new SafariSession();
        session.select(1, "Critter Safari");
        return session;
    }

    private static String catchFlitter() {
        return "CAPTURE! You caught a Flitter and gained a Flitter Shard!";
    }
}
