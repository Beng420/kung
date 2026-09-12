package com.github.beng420.kung.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.beng420.kung.util.CatacombsAverageCalculator.Breakdown;
import com.github.beng420.kung.util.CatacombsAverageCalculator.DungeonClass;
import com.github.beng420.kung.util.CatacombsAverageCalculator.ProfileData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

public final class CatacombsCalculatorTest {
    private static final String UUID = "7b2c5b9705a24f478f2d725b9694bd4d";
    private static final double LEVEL_50_XP = 569_809_640;

    @Test
    public void selectedWatermelonCannotBeOverwrittenByLaterEmptyRaspberry() {
        var result = parse(fixture());
        assertTrue(result.error(), result.success());
        assertEquals(2, result.player().profiles().size());
        assertEquals("Watermelon", result.player().selectedProfile().cuteName());
        assertTrue(result.player().selectedProfile().stats().available());
        assertEquals(10, result.player().selectedProfile().stats().explorerLevel());
        assertFalse(result.player().profiles().get(1).stats().available());
        assertEquals(1_072_528_552.5049516, result.player().selectedProfile().cataXp(), 0.0001);
    }

    @Test
    public void noSelectedFlagFallsBackToDungeonProgressWithoutLastSave() {
        JsonObject root = fixture();
        root.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("selected", false);
        assertEquals("Watermelon", parse(root).player().selectedProfile().cuteName());
    }

    @Test
    public void actualSelectedEmptyProfileIsNotSilentlyReplacedByAnotherProfile() {
        JsonObject root = fixture();
        root.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("selected", false);
        root.getAsJsonArray("profiles").get(1).getAsJsonObject().addProperty("selected", true);
        ProfileData selected = parse(root).player().selectedProfile();
        assertEquals("Raspberry", selected.cuteName());
        assertFalse(selected.stats().available());
    }

    @Test
    public void rejectsFailedResponsesMissingMembersAndEmptyProfileLists() {
        JsonObject failed = fixture();
        failed.addProperty("success", false);
        assertFalse(parse(failed).success());
        assertFalse(HypixelSkyBlockProfileClient.parseProfiles("another-player", "Other",
            new JsonObject(), fixture()).success());
        assertFalse(parse(JsonParser.parseString("{\"success\":true,\"profiles\":[]}").getAsJsonObject()).success());
    }

    @Test
    public void matchesExactAdjectilsSaltyWhaleExampleWithoutMayor() {
        ProfileData profile = parse(fixture()).player().selectedProfile();
        var xp = selectedXp(profile.classPerks(), 1);
        Breakdown result = CatacombsAverageCalculator.calculateBreakdown(profile.classXp(), xp);
        assertEquals(420_000, xp.get(DungeonClass.ARCHER), 0.0001);
        assertEquals(1538, result.total());
        assertEquals(484, result.perClass(DungeonClass.ARCHER));
        assertEquals(514, result.perClass(DungeonClass.BERSERK));
        assertEquals(540, result.perClass(DungeonClass.MAGE));
        assertEquals(0, result.perClass(DungeonClass.HEALER));
        assertEquals(0, result.perClass(DungeonClass.TANK));
        assertCompletes(profile.classXp(), xp, result);
    }

    @Test
    public void derpyReducesRealProfileTo1026RunsAndIncludesPassiveClassXp() {
        ProfileData profile = parse(fixture()).player().selectedProfile();
        var xp = selectedXp(profile.classPerks(), 1.5);
        Breakdown result = CatacombsAverageCalculator.calculateBreakdown(profile.classXp(), xp);
        assertEquals(630_000, xp.get(DungeonClass.ARCHER), 0.0001);
        assertEquals(1026, result.total());
        assertEquals(0, result.perClass(DungeonClass.HEALER));
        assertEquals(0, result.perClass(DungeonClass.TANK));
        assertCompletes(profile.classXp(), xp, result);
    }

    @Test
    public void oldWrongProfileReproducesTheReported2436Runs() {
        var xp = selectedXp(Map.of(), 1.5);
        Breakdown result = CatacombsAverageCalculator.calculateBreakdown(Map.of(), xp);
        assertEquals(2436, result.total());
        assertEquals(488, result.perClass(DungeonClass.ARCHER));
        assertEquals(487, result.perClass(DungeonClass.HEALER));
    }

    @Test
    public void handlesCompletedClassesAndCapsAuraLikeAdjectils() {
        EnumMap<DungeonClass, Double> completed = new EnumMap<>(DungeonClass.class);
        for (DungeonClass dungeonClass : DungeonClass.values()) completed.put(dungeonClass, LEVEL_50_XP);
        assertEquals(0, CatacombsAverageCalculator.calculateBreakdown(completed, Map.of()).total());
        assertEquals(Long.MAX_VALUE, CatacombsAverageCalculator.calculateBreakdown(Map.of(), Map.of()).total());
        assertEquals(selectedXp(Map.of(), 1.5), selectedXp(Map.of(), 1.59));
    }

    @Test
    public void explorerMatchesAdjectilsCatacombsDefaultsAcrossMayorBonuses() {
        assertEquals(534_000, CatacombsAverageCalculator.catacombsXpPerRun(300_000, true, 0.02, 10, 0, 1));
        assertEquals(759_000, CatacombsAverageCalculator.catacombsXpPerRun(300_000, true, 0.02, 10, 0, 1.5));
        assertEquals(786_000, CatacombsAverageCalculator.catacombsXpPerRun(300_000, true, 0.02, 10, 0, 1.59));
    }

    @Test
    public void explorerIsIndependentOfExpertRingAndAddedOnlyOnce() {
        for (boolean ring : new boolean[] {false, true}) {
            long without = CatacombsAverageCalculator.catacombsXpPerRun(300_000, ring, 0.02, 0, 0, 1);
            long levelFive = CatacombsAverageCalculator.catacombsXpPerRun(300_000, ring, 0.02, 5, 0, 1);
            long levelTen = CatacombsAverageCalculator.catacombsXpPerRun(300_000, ring, 0.02, 10, 0, 1);
            assertEquals(15_000, levelFive - without);
            assertEquals(30_000, levelTen - without);
        }
    }

    @Test
    public void globalCatacombsBonusMultipliesExplorerAndLowerFloorsUseTheirOwnBaseXp() {
        assertEquals(640_800, CatacombsAverageCalculator.catacombsXpPerRun(300_000, true, 0.02, 10, 0.20, 1));
        assertEquals(330, CatacombsAverageCalculator.catacombsXpPerRun(110, true, 0.02, 10, 0, 1));
        assertEquals(11_664, CatacombsAverageCalculator.catacombsXpPerRun(4880, true, 0.02, 10, 0, 1));
    }

    @Test
    public void explorerConvertsCumulativeEpicShardsToLevelsInsteadOfClampingStacks() {
        JsonObject member = new JsonObject();
        JsonObject attributes = new JsonObject();
        JsonObject stacks = new JsonObject();
        attributes.add("stacks", stacks);
        member.add("attributes", attributes);
        int[] shards = {0, 1, 2, 3, 4, 8, 9, 24, 25, 31, 32, 64};
        int[] levels = {0, 1, 2, 2, 3, 4, 5, 8, 9, 9, 10, 10};
        for (int i = 0; i < shards.length; i++) {
            stacks.addProperty("catacombs_explorer", shards[i]);
            assertEquals("shards=" + shards[i], levels[i], HypixelSkyBlockProfileClient.explorerAttributeLevel(member));
        }
    }

    @Test
    public void absentAttributeApiDataDiffersFromKnownZeroExplorer() {
        assertEquals(-1, HypixelSkyBlockProfileClient.explorerAttributeLevel(new JsonObject()));
        assertEquals(0, HypixelSkyBlockProfileClient.explorerAttributeLevel(
            JsonParser.parseString("{\"attributes\":{\"stacks\":{}}}").getAsJsonObject()));
    }

    private static void assertCompletes(Map<DungeonClass, Double> current, Map<DungeonClass, Double> xp, Breakdown result) {
        assertEquals(result.total(), result.perClass().values().stream().mapToLong(Long::longValue).sum());
        for (DungeonClass dungeonClass : DungeonClass.values()) {
            double selected = result.perClass(dungeonClass);
            double passive = (result.total() - selected) / 4.0;
            assertTrue(dungeonClass.label(), current.getOrDefault(dungeonClass, 0.0)
                + xp.get(dungeonClass) * (selected + passive) >= LEVEL_50_XP);
        }
    }

    private static EnumMap<DungeonClass, Double> selectedXp(Map<DungeonClass, Integer> perks, double mayor) {
        return CatacombsAverageCalculator.classXpPerRun(perks, 300_000, 0.02, 0.06, 0.20, 0, mayor);
    }

    private static HypixelSkyBlockProfileClient.ProfileResult parse(JsonObject root) {
        return HypixelSkyBlockProfileClient.parseProfiles(UUID, "Salty_Whale", new JsonObject(), root);
    }

    private static JsonObject fixture() {
        try (var stream = CatacombsCalculatorTest.class.getResourceAsStream("/catacombs/salty-whale-profiles.json");
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
