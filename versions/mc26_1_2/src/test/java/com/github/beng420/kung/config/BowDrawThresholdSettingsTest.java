package com.github.beng420.kung.config;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.MiscConfig;
import java.io.StringReader;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BowDrawThresholdSettingsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void defaultsNormalizationAndEmptyListsPreserveUserChoices() {
        assertEquals(List.of(3, 20), ticks(KungConfig.read(new StringReader("{}")).misc));
        assertEquals(List.of(3, 20), ticks(KungConfig.read(new StringReader("{\"misc\":{\"bowDrawThresholds\":null}}")).misc));
        assertEquals(List.of(), ticks(KungConfig.read(new StringReader("{\"misc\":{\"bowDrawThresholds\":[]}}")).misc));
        var config = KungConfig.read(new StringReader("""
            {"misc":{"bowDrawThresholds":[null, {"ticks":-4}, {"ticks":99}, {"ticks":7}]}}
            """));
        assertEquals(List.of(1, 20, 7), ticks(config.misc));
        assertFalse(config.misc.bowDrawIndicatorEnabled());
        for (int index = 0; index < 30; index++) config.misc.addBowDrawThreshold();
        assertEquals(20, config.misc.bowDrawThresholds().size());
    }

    @Test
    public void listControlsEditAndRemoveTheOwnedRowAndPersistWithoutReopening() {
        var path = temporary.getRoot().toPath().resolve("bow-thresholds.json");
        var config = new KungConfig(path);
        var settings = new BowDrawThresholdSettings(config.misc);
        var first = settings.get().get(1);
        var second = settings.get().get(2);
        assertEquals(SettingKind.STEPPER_REMOVE, first.kind());
        first.click(160, 100, 82); // Increase, distinct from the red remove control.
        assertEquals(List.of(4, 20), ticks(config.misc));
        assertSame(first, settings.get().get(1));
        first.click(90, 100, 82); // Clicking the label cannot change a number.
        assertEquals(List.of(4, 20), ticks(config.misc));
        first.click(175, 100, 82);
        assertEquals(List.of(20), ticks(config.misc));
        second.intConsumer().accept(12); // The old control still owns the same entry after a removal.
        first.intConsumer().accept(8); // Removed native/HUD controls cannot change another entry.
        assertEquals(List.of(12), ticks(config.misc));
        settings.get().getFirst().toggle().run();
        assertEquals(List.of(12, 10), ticks(config.misc));
        var restored = new KungConfig(path);
        restored.load();
        assertEquals(List.of(12, 10), ticks(restored.misc));
        for (var entry : List.copyOf(config.misc.bowDrawThresholds())) config.misc.removeBowDrawThreshold(entry);
        assertEquals(1, settings.get().size());
        restored.load();
        assertTrue(restored.misc.bowDrawThresholds().isEmpty());
    }

    @Test
    public void editorsClampTicksAndAddedRowsUseAnUnusedValue() {
        var config = new MiscConfig();
        var first = config.bowDrawThresholds().getFirst();
        config.setBowDrawThreshold(first, -20);
        assertEquals(1, first.ticks());
        config.setBowDrawThreshold(first, 99);
        assertEquals(20, first.ticks());
        config.addBowDrawThreshold();
        assertEquals(10, config.bowDrawThresholds().getLast().ticks());
        config.addBowDrawThreshold();
        assertEquals(1, config.bowDrawThresholds().getLast().ticks());
    }

    private static List<Integer> ticks(MiscConfig config) {
        return config.bowDrawThresholds().stream().map(MiscConfig.BowDrawThreshold::ticks).toList();
    }
}
