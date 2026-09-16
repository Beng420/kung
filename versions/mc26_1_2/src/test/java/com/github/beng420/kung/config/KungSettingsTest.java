package com.github.beng420.kung.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.beng420.kung.config.KungSettings.CategoryEntry;
import com.github.beng420.kung.config.KungSettings.FeatureEntry;
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
        assertEquals(List.of(8, 2, 1, 1, 7, 3),
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

    private static FeatureEntry feature(List<CategoryEntry> categories, String name) {
        return categories.stream().flatMap(category -> category.features().stream())
            .filter(feature -> feature.name().equals(name)).findFirst().orElseThrow();
    }

    private static SettingEntry setting(FeatureEntry feature, String label) {
        return feature.settings().stream().filter(setting -> setting.label().equals(label)).findFirst().orElseThrow();
    }
}
