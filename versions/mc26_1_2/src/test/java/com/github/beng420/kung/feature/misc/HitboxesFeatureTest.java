package com.github.beng420.kung.feature.misc;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Test;

public final class HitboxesFeatureTest {
    @Test
    public void nametagsReduceToTheirMobNameAndMatchWholeWords() {
        assertEquals("Zealot", HitboxesFeature.mobName("[Lv55] Zealot 13,000/13,000❤"));
        assertEquals("Voidgloom Seraph", HitboxesFeature.mobName("☠ Voidgloom Seraph 30M❤"));
        assertEquals("Skeleton Master", HitboxesFeature.mobName("✯ Skeleton Master 1.2M❤"));
        assertEquals("skyblock:voidgloom_seraph", HitboxesFeature.skyBlockId("☠ Voidgloom Seraph 30M❤"));
        assertNull(HitboxesFeature.skyBlockId("13,000/13,000❤"));
        // Modifiers drop from added names, so one entry covers every variant.
        assertEquals("skyblock:skeleton_master", HitboxesFeature.skyBlockId("✯ Speedy Skeleton Master 1.2M❤"));
        assertEquals("skyblock:zombie_knight", HitboxesFeature.skyBlockId("✯ Healthy Fortified Zombie Knight 900k❤"));
        assertEquals("skyblock:golden_ghoul", HitboxesFeature.skyBlockId("[Lv60] Golden Ghoul 45,000❤"));
        assertEquals("skyblock:healthy", HitboxesFeature.skyBlockId("Healthy"));

        Map<String, Integer> selected = Map.of("skyblock:zealot", 7, "minecraft:zombie", 9);
        assertEquals(Integer.valueOf(7), HitboxesFeature.skyBlockColor(selected, "[Lv55] Zealot 13,000/13,000❤"));
        assertEquals(Integer.valueOf(7), HitboxesFeature.skyBlockColor(selected, "[Lv55] Special Zealot 2,000❤"));
        // Dungeon prefixes: one entry covers every variant of the mob.
        Map<String, Integer> master = Map.of("skyblock:skeleton_master", 3);
        assertEquals(Integer.valueOf(3), HitboxesFeature.skyBlockColor(master, "✯ Healthy Skeleton Master 1.2M❤"));
        assertEquals(Integer.valueOf(3), HitboxesFeature.skyBlockColor(master, "[Lv90] Skeleton Master 20,000❤"));
        assertNull(HitboxesFeature.skyBlockColor(master, "✯ Skeleton Soldier 800k❤"));
        // Whole words only: a vanilla id or a longer word never matches by accident.
        assertNull(HitboxesFeature.skyBlockColor(selected, "[Lv3] Zombie 100/100❤"));
        assertNull(HitboxesFeature.skyBlockColor(Map.of("skyblock:ender", 1), "[Lv42] Enderman 4,500❤"));
    }
}
