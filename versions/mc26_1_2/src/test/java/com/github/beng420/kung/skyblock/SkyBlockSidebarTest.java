package com.github.beng420.kung.skyblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.junit.Test;

public final class SkyBlockSidebarTest {
    @Test
    public void syntheticOwnersDoNotSplitLocationOrServerText() {
        Fixture fixture = new Fixture();
        fixture.teamLine("team_date", "🎁", 15, "§709/10/26 mini", "§724BS");
        fixture.teamLine("team_location", "⚽", 10, " §7⏣ §cThe Cata", "§ccombs (M7)");

        List<String> lines = SkyBlockSidebar.lines(fixture.scoreboard, Set.of("⚽", "🎁"));

        assertEquals(List.of("SKYBLOCK", "09/10/26 mini24BS", "⏣ The Catacombs (M7)"),
            lines.stream().map(HypixelLocation::clean).toList());
        assertEquals(HypixelLocation.Kind.CATACOMBS, HypixelLocation.parse(lines).kind());
        assertEquals("mini24bs", HypixelLocation.serverId(lines));
        assertFalse(lines.stream().anyMatch(line -> line.contains("⚽") || line.contains("🎁")));
    }

    @Test
    public void ownersNotUpdatedInThisWorldCannotOverrideFreshDungeonEvidence() {
        Fixture fixture = new Fixture();
        fixture.teamLine("old_area", "🍫", 14, "§7⏣ §bDungeon ", "§bHub");
        fixture.teamLine("old_server", "🎁", 15, "§709/10/26 mini", "§7149C");
        fixture.teamLine("new_area", "⚽", 9, "§7⏣ §cThe Cata", "§ccombs (E)");
        fixture.teamLine("new_server", "🎉", 12, "§709/10/26 mini", "§7112H");

        // The old rows really are present; only current-world packet ownership makes them ineligible.
        assertEquals(HypixelLocation.Kind.DUNGEON_HUB,
            HypixelLocation.parse(SkyBlockSidebar.lines(fixture.scoreboard, null)).kind());
        List<String> fresh = SkyBlockSidebar.lines(fixture.scoreboard, Set.of("⚽", "🎉"));
        assertEquals(HypixelLocation.Kind.CATACOMBS, HypixelLocation.parse(fresh).kind());
        assertEquals("mini112h", HypixelLocation.serverId(fresh));
        assertEquals(3, fresh.size());

        List<String> unconfirmed = SkyBlockSidebar.lines(fixture.scoreboard, Set.of());
        assertEquals(List.of("SKYBLOCK"), unconfirmed);
        assertEquals(HypixelLocation.UNKNOWN, HypixelLocation.parse(unconfirmed));
        assertEquals("", HypixelLocation.serverId(unconfirmed));
    }

    @Test
    public void directDisplayNamesWorkWithoutTeamsAndHiddenScoresStayHidden() {
        Fixture fixture = new Fixture();
        fixture.scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("location"), fixture.objective)
            .display(Component.literal("⏣ Kuudra's Hollow (T5)"));
        fixture.teamLine("hidden", "#not-a-sidebar-line", 20, "⏣ Dungeon ", "Hub");

        List<String> lines = SkyBlockSidebar.lines(fixture.scoreboard, null);
        assertEquals(List.of("SKYBLOCK", "⏣ Kuudra's Hollow (T5)"), lines);
        assertEquals(HypixelLocation.Kind.KUUDRA, HypixelLocation.parse(lines).kind());
    }

    @Test
    public void freshScoreValueCannotReviveTeamTextFromBeforeTheTransfer() {
        Fixture fixture = new Fixture();
        fixture.teamLine("reused", "old-owner", 10, "⏣ Dungeon ", "Hub");
        // Respawn may preserve the vanilla scoreboard. A new numeric score is not a new location field.
        List<String> beforeTeamPacket = SkyBlockSidebar.lines(fixture.scoreboard, Set.of("old-owner"), Set.of());
        assertEquals(HypixelLocation.UNKNOWN, HypixelLocation.parse(beforeTeamPacket));
        PlayerTeam team = fixture.scoreboard.getPlayerTeam("reused");
        team.setPlayerPrefix(Component.literal("⏣ The Cata"));
        team.setPlayerSuffix(Component.literal("combs (M7)"));
        List<String> afterTeamPacket = SkyBlockSidebar.lines(fixture.scoreboard, Set.of("old-owner"), Set.of("reused"));
        assertEquals(HypixelLocation.Kind.CATACOMBS, HypixelLocation.parse(afterTeamPacket).kind());
    }

    @Test
    public void bossSidebarRewriteDoesNotCreateAnInstanceBetweenItsPackets() {
        Fixture fixture = new Fixture();
        fixture.teamLine("date", "date-owner", 15, "09/10/26 m61", "e");
        fixture.teamLine("location", "location-owner", 10, "The Catacombs ", "(F1)");
        SkyBlockSidebar.Pending pending = new SkyBlockSidebar.Pending();
        pending.team("date", Set.of("date-owner"), true);
        pending.team("location", Set.of("location-owner"), true);
        HypixelInstanceState state = new HypixelInstanceState();
        List<String> initial = pending.poll(fixture.scoreboard);
        state.observe(HypixelLocation.parse(initial), HypixelLocation.serverId(initial));
        long epoch = state.epoch();

        // Exact trace regression: m61e temporarily reads as m61 during the boss HUD rewrite.
        PlayerTeam date = fixture.scoreboard.getPlayerTeam("date");
        date.setPlayerSuffix(Component.empty());
        pending.team("date", Set.of("date-owner"), true);
        date.setPlayerSuffix(Component.literal("e"));
        pending.team("date", Set.of("date-owner"), true);
        List<String> completedBatch = pending.poll(fixture.scoreboard);
        state.observe(HypixelLocation.parse(completedBatch), HypixelLocation.serverId(completedBatch));
        assertTrue(state.catacombs());
        assertEquals(epoch, state.epoch());
        assertEquals("m61e", state.serverId());
        assertNull(pending.poll(fixture.scoreboard));

        // A complete new server row still invalidates the old instance on the next batch.
        date.setPlayerPrefix(Component.literal("09/10/26 m183"));
        date.setPlayerSuffix(Component.literal("q"));
        pending.team("date", Set.of("date-owner"), true);
        List<String> transfer = pending.poll(fixture.scoreboard);
        state.observe(HypixelLocation.parse(transfer), HypixelLocation.serverId(transfer));
        assertFalse(state.catacombs());
        assertEquals(epoch + 1, state.epoch());
        pending.reset();
        assertNull(pending.poll(fixture.scoreboard));
    }

    private static final class Fixture {
        final Scoreboard scoreboard = new Scoreboard();
        final Objective objective = scoreboard.addObjective("SBScoreboard", ObjectiveCriteria.DUMMY,
            Component.literal("SKYBLOCK"), ObjectiveCriteria.RenderType.INTEGER, false, null);

        Fixture() {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        }

        void teamLine(String teamName, String owner, int score, String prefix, String suffix) {
            PlayerTeam team = scoreboard.addPlayerTeam(teamName);
            team.setPlayerPrefix(Component.literal(prefix));
            team.setPlayerSuffix(Component.literal(suffix));
            scoreboard.addPlayerToTeam(owner, team);
            scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner), objective).set(score);
        }
    }
}
