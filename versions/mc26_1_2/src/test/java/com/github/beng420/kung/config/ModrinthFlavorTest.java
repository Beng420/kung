package com.github.beng420.kung.config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.github.beng420.kung.KungBuild;
import com.github.beng420.kung.config.KungSettings.FeatureEntry;
import com.github.beng420.kung.config.category.DungeonConfig.PillarMaterial;
import java.io.StringReader;
import org.junit.Test;

/** Runs in both flavors; with -Pmodrinth it checks what the Modrinth jar leaves out. */
public final class ModrinthFlavorTest {
    @Test
    public void modrinthLeavesOutBloodRushMimicEspAndGlassPillars() {
        var config = KungConfig.read(new StringReader("{\"dungeonMap\":{\"pillarMaterial\":\"GLASS\",\"mimicEspEnabled\":true}}"));
        var catalog = KungSettings.categories(config, () -> { }, true);
        var features = catalog.stream().flatMap(category -> category.features().stream()).toList();
        assertEquals(!KungBuild.MODRINTH, features.stream().anyMatch(feature -> feature.name().equals("Blood rush helper")));

        FeatureEntry map = features.stream().filter(feature -> feature.name().equals("Dungeon Map")).findFirst().orElseThrow();
        var mapSettings = map.settings().stream().map(SettingEntry::label).toList();
        assertEquals(!KungBuild.MODRINTH, mapSettings.contains("Mimic ESP"));
        // Entered rooms only: nothing unopened left to dim.
        assertEquals(!KungBuild.MODRINTH, mapSettings.contains("Unopened Alpha"));

        // No GitHub update check in the Modrinth jar, so no row may wait for one.
        FeatureEntry updates = catalog.stream().filter(category -> category.name().equals("Debug"))
            .findFirst().orElseThrow().features().getFirst();
        assertEquals(KungBuild.MODRINTH, updates.name().equals("Updates: Modrinth App"));
        assertEquals(!KungBuild.MODRINTH, updates.settings().stream()
            .anyMatch(setting -> setting.label().startsWith("Latest")));

        assertArrayEquals(KungBuild.MODRINTH
                ? new PillarMaterial[] {PillarMaterial.WOOL, PillarMaterial.TERRACOTTA}
                : PillarMaterial.values(),
            PillarMaterial.choices());
        // A config copied over from the full build keeps no Glass.
        assertEquals(KungBuild.MODRINTH ? PillarMaterial.WOOL : PillarMaterial.GLASS, config.dungeon.pillarMaterial());
    }
}
