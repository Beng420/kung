package com.github.beng420.kung.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.beng420.kung.config.KungSettings.CategoryEntry;
import com.github.beng420.kung.config.KungSettings.FeatureEntry;
import com.github.beng420.kung.config.category.DungeonConfig.DragonDebuffScope;
import com.github.beng420.kung.config.category.SplitsConfig.PredictionMode;
import com.github.beng420.kung.config.category.SplitsConfig.TimeFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class KungSettingsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sharesCompleteCatalogWithoutOpeningScreenOrChangingDefaults() {
        Path file = temporary.getRoot().toPath().resolve("kung.json");
        var categories = KungSettings.categories(new KungConfig(file), () -> { });

        assertEquals(List.of("Dungeon", "Garden", "Hunting", "Slayer", "Util", "Debug"),
            categories.stream().map(CategoryEntry::name).toList());
        assertEquals(List.of(10, 2, 1, 1, 9, 3),
            categories.stream().map(category -> category.features().size()).toList());
        for (var category : categories) {
            for (var feature : category.features()) {
                if (!feature.actionOnly() && feature.toggle() != null) {
                    assertFalse(feature.name() + " must remain off by default", feature.enabled());
                }
            }
        }
        assertFalse("Building either menu must not persist or enable anything", Files.exists(file));
        assertEquals(12, feature(categories, "Loadouts Auto Close").settings().stream()
            .filter(setting -> setting.kind() == SettingKind.KEYBIND).count());
        assertFalse(feature(categories, "Crypts").clickable());
    }

    @Test
    public void bowIndicatorSharesHudPlacementAndPersistsWithoutEnablingOtherFeatures() {
        Path file = temporary.getRoot().toPath().resolve("bow.json");
        KungConfig config = new KungConfig(file);
        var entry = KungHudLayout.entries(config, new com.github.beng420.kung.feature.dungeon.DungeonStateTracker())
            .stream().filter(hud -> hud.id().equals("bow_draw_indicator")).findFirst().orElseThrow();
        assertFalse(entry.enabled());
        assertFalse(Files.exists(file));
        feature(KungSettings.categories(config, () -> { }), "Bow Draw Indicator").toggle().run();
        assertTrue(entry.enabled());
        entry.moveX(-30);
        entry.moveY(80);
        entry.scale(1.25F);
        assertEquals(new KungHudLayout.Bounds(-30, 80, 250, 51), entry.bounds());
        KungConfig restored = new KungConfig(file);
        restored.load();
        assertTrue(restored.misc.bowDrawIndicatorEnabled());
        assertEquals(-30, restored.misc.bowDrawIndicatorX());
        assertEquals(80, restored.misc.bowDrawIndicatorY());
        assertEquals(125, restored.misc.bowDrawIndicatorScale());
        assertFalse(restored.misc.superpairsHelperEnabled());
        assertFalse(restored.dungeon.enabled());
        restored.misc.setBowDrawIndicatorScale(999);
        assertEquals(300, restored.misc.bowDrawIndicatorScale());
        restored.misc.setBowDrawIndicatorScale(0);
        assertEquals(25, restored.misc.bowDrawIndicatorScale());
    }

    @Test
    public void separateCatalogsReadAndWriteSameValidatedPersistentSettings() {
        Path file = temporary.getRoot().toPath().resolve("kung.json");
        KungConfig config = new KungConfig(file);
        var standalone = KungSettings.categories(config, () -> { });
        var nativeMenu = KungSettings.categories(config, () -> { });

        feature(nativeMenu, "Feast Progress").toggle().run();
        assertTrue(feature(standalone, "Feast Progress").enabled());
        setting(feature(nativeMenu, "Feast Progress"), "Kernel Timeout (s)").setText("500");
        assertEquals("300", setting(feature(standalone, "Feast Progress"), "Kernel Timeout (s)").textValue());
        config.feast.setKernelTimeoutSeconds(45);
        assertEquals("45", setting(feature(nativeMenu, "Feast Progress"), "Kernel Timeout (s)").textValue());

        FeatureEntry defaults = feature(KungSettings.defaults(), "Feast Progress");
        assertFalse(defaults.enabled());
        assertEquals("60", setting(defaults, "Kernel Timeout (s)").textValue());

        KungConfig restored = new KungConfig(file);
        restored.load();
        assertTrue(restored.feast.enabled());
        assertEquals(45, restored.feast.kernelTimeoutSeconds());
        assertEquals(config.feast.x(), restored.feast.x());
        assertEquals(config.feast.y(), restored.feast.y());
        assertEquals(config.feast.scale(), restored.feast.scale());
    }

    @Test
    public void choicesAndActionsKeepTheirExistingMeaningAcrossMenus() {
        KungConfig config = new KungConfig(temporary.getRoot().toPath().resolve("kung.json"));
        AtomicInteger changelogs = new AtomicInteger();
        var categories = KungSettings.categories(config, changelogs::incrementAndGet);
        FeatureEntry splits = feature(categories, "Splits Overlay");
        SettingEntry format = setting(splits, "Format");
        assertEquals(List.of("Minutes", "Seconds"), format.choices());
        format.intConsumer().accept(1);
        assertEquals(TimeFormat.SECONDS, config.splits.format());
        format.cycleChoice().run();
        assertEquals(TimeFormat.MINUTES, config.splits.format());

        SettingEntry prediction = setting(splits, "Time Prediction").children().getFirst();
        prediction.intConsumer().accept(1);
        assertEquals(PredictionMode.LIVE, config.splits.predictionMode());
        assertEquals("Live", prediction.choiceSupplier().get());

        FeatureEntry updater = categories.getLast().features().getFirst();
        assertTrue(updater.actionOnly());
        setting(updater, "Changelogs").toggle().run();
        assertEquals(1, changelogs.get());
    }

    @Test
    public void soundGroupsKeepFileAndEventBindingsIndependent() {
        Path file = temporary.getRoot().toPath().resolve("kung.json");
        KungConfig config = new KungConfig(file);
        config.misc.setCustomArrowHitSounds("shared.wav, arrow.wav");
        config.misc.setCustomWitherShieldExpireSounds("shared.wav, wither.wav");
        FeatureEntry sounds = feature(KungSettings.categories(config, () -> { }), "Custom Sounds");
        var groups = sounds.settings().stream().filter(setting -> setting.kind() == SettingKind.GROUP).toList();
        assertEquals(List.of("Arrow: shared.wav", "Arrow: arrow.wav", "Wither: shared.wav", "Wither: wither.wav"),
            groups.stream().map(SettingEntry::label).toList());

        for (int index = 0; index < groups.size(); index++) {
            var controls = groups.get(index).children();
            assertEquals(List.of("Volume", "Pitch", "Test"), controls.stream().map(SettingEntry::label).toList());
            controls.get(0).intConsumer().accept(10 + index);
            controls.get(1).intConsumer().accept(100 + index * 5);
        }
        KungConfig restored = new KungConfig(file);
        restored.load();
        assertEquals(10, restored.misc.customArrowHitSoundVolumeTenths("shared.wav"));
        assertEquals(11, restored.misc.customArrowHitSoundVolumeTenths("arrow.wav"));
        assertEquals(12, restored.misc.customWitherShieldExpireSoundVolumeTenths("shared.wav"));
        assertEquals(13, restored.misc.customWitherShieldExpireSoundVolumeTenths("wither.wav"));
        assertEquals(100, restored.misc.customArrowHitSoundPitchHundredths("shared.wav"));
        assertEquals(105, restored.misc.customArrowHitSoundPitchHundredths("arrow.wav"));
        assertEquals(110, restored.misc.customWitherShieldExpireSoundPitchHundredths("shared.wav"));
        assertEquals(115, restored.misc.customWitherShieldExpireSoundPitchHundredths("wither.wav"));
        config.misc.setCustomArrowHitSoundVolumeTenths("shared.wav", 20);
        assertEquals(20, groups.getFirst().children().getFirst().intSupplier().getAsInt());
        assertFalse(config.misc.customSoundsEnabled());
    }

    @Test
    public void dungeonDebuffTogglesPersistIndependentlyOfMapAndEachOther() {
        Path file = temporary.getRoot().toPath().resolve("debuff.json");
        KungConfig config = new KungConfig(file);
        var catalog = KungSettings.categories(config, () -> { });
        feature(catalog, "Ice Spray Highlight").toggle().run();
        assertTrue(config.dungeon.iceSprayHighlightEnabled());
        assertFalse(config.dungeon.dragonDebuffEnabled());
        assertFalse(config.dungeon.enabled());
        feature(catalog, "M7 Dragon Debuff").toggle().run();
        config.dungeon.setDragonDebuffX(-30);
        config.dungeon.setDragonDebuffY(150);
        config.dungeon.setDragonDebuffScale(125);
        KungConfig restored = new KungConfig(file);
        restored.load();
        assertTrue(restored.dungeon.iceSprayHighlightEnabled());
        assertTrue(restored.dungeon.dragonDebuffEnabled());
        assertFalse(restored.dungeon.enabled());
        assertEquals(-30, restored.dungeon.dragonDebuffX());
        assertEquals(150, restored.dungeon.dragonDebuffY());
        assertEquals(125, restored.dungeon.dragonDebuffScale());
        var bounds = com.github.beng420.kung.feature.dungeon.DragonDebuffHud.overlayBounds(restored.dungeon);
        assertEquals(-30, bounds.x());
        assertEquals(150, bounds.y());
        assertEquals(Math.round(410 * 1.25F), bounds.width());
        restored.dungeon.setDragonDebuffScale(999);
        assertEquals(300, restored.dungeon.dragonDebuffScale());
    }

    @Test
    public void spraySizeAndDragonScopeShareValidatedSettingsAndPersist() {
        Path file = temporary.getRoot().toPath().resolve("debuff-options.json");
        KungConfig config = new KungConfig(file);
        var catalog = KungSettings.categories(config, () -> { });
        var size = setting(feature(catalog, "Ice Spray Highlight"), "Box Size (%)");
        var scope = setting(feature(catalog, "M7 Dragon Debuff"), "Track");
        assertEquals(100, size.intSupplier().getAsInt());
        assertEquals(List.of("All Dragons", "Nearest Statue"), scope.choices());
        size.intConsumer().accept(150);
        scope.intConsumer().accept(1);
        KungConfig restored = new KungConfig(file);
        restored.load();
        assertEquals(150, restored.dungeon.iceSprayBoxSize());
        assertEquals(DragonDebuffScope.NEAREST_STATUE, restored.dungeon.dragonDebuffScope());
        assertFalse(restored.dungeon.iceSprayHighlightEnabled());
        assertFalse(restored.dungeon.dragonDebuffEnabled());
        assertEquals(Math.round(30 * .85F), com.github.beng420.kung.feature.dungeon.DragonDebuffHud.overlayBounds(restored.dungeon).height());
        size.intConsumer().accept(900);
        assertEquals(200, config.dungeon.iceSprayBoxSize());
        size.intConsumer().accept(-1);
        assertEquals(100, config.dungeon.iceSprayBoxSize());
        var invalid = KungConfig.read(new java.io.StringReader("""
            {"dungeonMap":{"iceSprayBoxSize":999,"dragonDebuffScope":"unknown"}}
            """));
        assertEquals(200, invalid.dungeon.iceSprayBoxSize());
        assertEquals(DragonDebuffScope.ALL_DRAGONS, invalid.dungeon.dragonDebuffScope());
    }

    private static FeatureEntry feature(List<CategoryEntry> categories, String name) {
        return categories.stream().flatMap(category -> category.features().stream())
            .filter(feature -> feature.name().equals(name)).findFirst().orElseThrow();
    }

    private static SettingEntry setting(FeatureEntry feature, String label) {
        return feature.settings().stream().filter(setting -> setting.label().equals(label)).findFirst().orElseThrow();
    }
}
