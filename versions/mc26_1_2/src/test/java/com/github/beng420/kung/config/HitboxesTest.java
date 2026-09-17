package com.github.beng420.kung.config;

import static org.junit.Assert.*;

import com.github.beng420.kung.config.category.HitboxesConfig;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class HitboxesTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void oldConfigsDefaultOffAndMalformedEntriesDoNotBecomeInvisibleBoxes() {
        assertFalse(read("{}").hitboxes.enabled());
        assertTrue(read("{}").hitboxes.dragonOverallBox());
        assertTrue(read("{}").hitboxes.dragonPartBoxes());
        assertTrue(read("{\"hitboxes\":null}").hitboxes.entities().isEmpty());
        assertTrue(read("{\"hitboxes\":{\"entities\":null}}").hitboxes.entities().isEmpty());
        var config = read("""
            {"hitboxes":{"entities":{"arrow":1193046,"invalid id":45,"minecraft:zombie":null}}}
            """);
        assertEquals(java.util.Map.of("minecraft:arrow", 0xFF123456), config.hitboxes.entities());
    }

    @Test
    public void dragonOverallAndPartBoxesToggleIndependentlyAndPersistWhenReadded() {
        var path = temporary.getRoot().toPath().resolve("dragon-boxes.json");
        var config = new KungConfig(path);
        config.hitboxes.add("minecraft:ender_dragon");
        var settings = new HitboxSettings(config.hitboxes);
        var row = settings.get().get(1);
        assertEquals(List.of("Overall Box", "Body Part Boxes"), row.children().stream().map(SettingEntry::label).toList());
        var overall = row.children().get(0);
        var parts = row.children().get(1);
        overall.toggle().run();
        assertFalse(overall.booleanSupplier().getAsBoolean());
        assertTrue(parts.booleanSupplier().getAsBoolean());
        assertSame(row, settings.get().get(1));
        var restored = new KungConfig(path);
        restored.load();
        assertFalse(restored.hitboxes.dragonOverallBox());
        assertTrue(restored.hitboxes.dragonPartBoxes());
        parts.toggle().run();
        assertFalse(config.hitboxes.dragonOverallBox());
        assertFalse(config.hitboxes.dragonPartBoxes());
        overall.toggle().run();
        restored.load();
        assertTrue(restored.hitboxes.dragonOverallBox());
        assertFalse(restored.hitboxes.dragonPartBoxes());
        config.hitboxes.remove("minecraft:ender_dragon");
        assertEquals(1, settings.get().size());
        config.hitboxes.add("minecraft:ender_dragon");
        assertFalse(settings.get().get(1).children().get(1).booleanSupplier().getAsBoolean());
        assertFalse(config.hitboxes.enabled());
    }

    @Test
    public void addedTypesAndColorsPersistAndTheExistingCatalogUpdatesWithoutReopening() {
        var path = temporary.getRoot().toPath().resolve("kung.json");
        KungConfig config = new KungConfig(path);
        var feature = KungSettings.categories(config, () -> { }).stream().flatMap(category -> category.features().stream())
            .filter(entry -> entry.name().equals("Hitboxes")).findFirst().orElseThrow();
        assertEquals(List.of("Add Hitbox"), feature.settings().stream().map(SettingEntry::label).toList());
        config.hitboxes.add("minecraft:arrow");
        config.hitboxes.setColor("minecraft:arrow", 0x123456);
        config.hitboxes.add("minecraft:arrow");
        config.hitboxes.add("minecraft:ender_dragon");
        assertFalse(feature.enabled());
        assertEquals(List.of("Add Hitbox", "Arrow", "Ender Dragon"), feature.settings().stream().map(SettingEntry::label).toList());
        assertSame(feature.settings().get(1), feature.settings().get(1));
        assertEquals(0xFF123456, feature.settings().get(1).intSupplier().getAsInt());
        feature.toggle().run();

        KungConfig restored = new KungConfig(path);
        restored.load();
        assertTrue(restored.hitboxes.enabled());
        assertEquals(config.hitboxes.entities(), restored.hitboxes.entities());
        feature.settings().get(1).click(150, 100, 52);
        assertEquals(List.of("Add Hitbox", "Ender Dragon"), feature.settings().stream().map(SettingEntry::label).toList());
        restored.load();
        assertFalse(restored.hitboxes.entities().containsKey("minecraft:arrow"));
        config.hitboxes.setEnabled(false);
        assertEquals(1, config.hitboxes.entities().size());
        config.hitboxes.add(null);
        config.hitboxes.add("not an id");
        assertEquals(1, config.hitboxes.entities().size());
    }

    @Test
    public void searchCoversTheWholeRegistryByEnglishNameAndIdWithoutSelectedDuplicates() {
        var all = HitboxSettings.entityIds();
        assertEquals(BuiltInRegistries.ENTITY_TYPE.size(), all.size());
        assertTrue(all.containsAll(List.of("minecraft:arrow", "minecraft:ender_dragon", "minecraft:item", "minecraft:player")));
        assertEquals(List.of("minecraft:ender_dragon"), HitboxSettings.search(all, Set.of(), " ENDER DRAGON "));
        assertEquals(List.of("minecraft:ender_dragon"), HitboxSettings.search(all, Set.of(), "ender_dragon"));
        assertEquals(List.of("minecraft:spectral_arrow"), HitboxSettings.search(all, Set.of("minecraft:arrow"), "arrow"));
        assertEquals(List.of("minecraft:arrow"), HitboxSettings.search(all, Set.of(), "minecraft:arrow"));
        assertTrue(HitboxSettings.search(all, Set.of(), "no such entity xyz").isEmpty());
        assertEquals("Arrow (example)", HitboxSettings.name("example:arrow"));
    }

    @Test
    public void colorSwatchAndRemovalHaveSeparateClickTargets() {
        AtomicInteger edits = new AtomicInteger();
        AtomicInteger removals = new AtomicInteger();
        var entry = SettingEntry.colorRemove("Arrow", () -> HitboxesConfig.DEFAULT_COLOR, edits::incrementAndGet, removals::incrementAndGet);
        entry.click(90, 100, 52);
        assertEquals(0, edits.get());
        entry.click(110, 100, 52);
        assertEquals(1, edits.get());
        assertEquals(0, removals.get());
        entry.click(146, 100, 52);
        assertEquals(1, edits.get());
        assertEquals(1, removals.get());
    }

    @Test
    public void wheelAndHexSupportSaturatedColorsWhiteBlackAndRejectInvalidInput() {
        assertEquals(0xFFFF0000, HitboxEditorScreen.wheelColor(65, 0, 65, 1));
        assertEquals(0xFF00FFFF, HitboxEditorScreen.wheelColor(-65, 0, 65, 1));
        assertEquals(0xFFFFFFFF, HitboxEditorScreen.wheelColor(0, 0, 65, 1));
        assertEquals(0xFF000000, HitboxEditorScreen.wheelColor(65, 0, 65, 0));
        assertEquals(Integer.valueOf(0xFF12ABEF), HitboxEditorScreen.parseHex("#12abEF"));
        assertEquals(Integer.valueOf(0xFF000000), HitboxEditorScreen.parseHex("000000"));
        assertNull(HitboxEditorScreen.parseHex("#12345"));
        assertNull(HitboxEditorScreen.parseHex("#1234567"));
        assertNull(HitboxEditorScreen.parseHex("#xyzxyz"));
    }

    private static KungConfig read(String json) { return KungConfig.read(new StringReader(json)); }
}
