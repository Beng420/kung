package com.github.beng420.kung.skyblock;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkyBlockFeastMayorTest {
    @Test
    public void recognizesTheElectedMayorPerkAndTermWithBoundedFreshness() {
        var status = parse("""
            {"name":"Finnegan", "perks":[{"name":"Grand Feast"}], "election":{"year":513}}
            """);
        assertTrue(status.active(1_000));
        assertEquals(513, status.electionYear());
        assertFalse(status.active(121_000));
    }

    @Test
    public void recognizesOnlyTheSelectedMinisterPerk() {
        assertTrue(parse("""
            {"name":"Aatrox", "minister":{"name":"Finnegan", "perk":{"name":"Grand Feast"}},
             "election":{"year":514}}
            """).active(1_000));
        assertFalse(parse("""
            {"name":"Aatrox", "minister":{"name":"Finnegan", "perk":{"name":"Blooming Business"}},
             "election":{"year":514}}
            """).active(1_000));
    }

    @Test
    public void candidatePerksAndFinneganNameAloneDoNotActivateGrandFeast() {
        assertFalse(parse("""
            {"name":"Finnegan", "perks":[{"name":"GOATed"}],
             "election":{"year":513,"candidates":[{"name":"Finnegan","perks":[{"name":"Grand Feast"}]}]}}
            """).active(1_000));
        assertFalse(parse("""
            {"name":"Jerry", "perks":[{"name":"Perkpocalypse"}], "election":{"year":513}}
            """).active(1_000));
    }

    @Test
    public void missingOrMalformedTermCannotReuseAnOldEvent() {
        for (String value : new String[] {"{}", "{\"perks\":[{\"name\":\"Grand Feast\"}]}",
            "{\"perks\":[{\"name\":\"Grand Feast\"}],\"election\":{\"year\":\"oops\"}}"}) {
            assertFalse(parse(value).active(1_000));
        }
        assertFalse(SkyBlockMayorTracker.parseFeastStatus(null, 1_000).active(1_000));
    }

    private static SkyBlockMayorTracker.FeastStatus parse(String json) {
        return SkyBlockMayorTracker.parseFeastStatus(JsonParser.parseString(json).getAsJsonObject(), 1_000);
    }
}
