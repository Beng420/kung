package com.github.beng420.kung.config;

import com.github.beng420.kung.config.category.HitboxesConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;

/** Stable row objects until membership changes preserve hover and click ownership. */
final class HitboxSettings implements Supplier<List<SettingEntry>> {
    private final HitboxesConfig config;
    private List<String> ids = List.of();
    private List<SettingEntry> rows = List.of();

    HitboxSettings(HitboxesConfig config) { this.config = config; }

    @Override
    public List<SettingEntry> get() {
        List<String> selected = List.copyOf(config.entities().keySet());
        if (!rows.isEmpty() && selected.equals(ids)) return rows;
        ids = selected;
        List<SettingEntry> next = new ArrayList<>();
        next.add(SettingEntry.button("Add Hitbox", "+", () -> open(null)));
        for (String id : ids) {
            var row = SettingEntry.colorRemove(name(id), () -> config.entities().getOrDefault(id, HitboxesConfig.DEFAULT_COLOR),
                () -> open(id), () -> config.remove(id)).withTooltip(id, "Click the color to edit; click - to remove.");
            if (id.equals("minecraft:ender_dragon")) {
                row = row.withChildren(List.of(
                    SettingEntry.toggle("Overall Box", config::dragonOverallBox,
                        () -> config.setDragonOverallBox(!config.dragonOverallBox()))
                        .withTooltip("Show the dragon's large overall bounding box."),
                    SettingEntry.toggle("Body Part Boxes", config::dragonPartBoxes,
                        () -> config.setDragonPartBoxes(!config.dragonPartBoxes()))
                        .withTooltip("Show the smaller boxes for the head, body, wings and tail.")
                )).withTooltip(id, "Expand for independent overall and body-part boxes.",
                    "Click the color to edit; click - to remove.");
            }
            next.add(row);
        }
        return rows = List.copyOf(next);
    }

    private void open(String id) {
        Minecraft client = Minecraft.getInstance();
        Screen parent = client.screen;
        // The optional native menu delegates these compound controls to the same complete editor.
        if (!(parent instanceof KungConfigScreen)) {
            parent = KungConfigScreen.fromParent(parent, "Hitboxes");
            client.setScreen(parent);
        }
        client.setScreen(new HitboxEditorScreen(parent, config, id));
    }

    static List<String> entityIds() {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString)
            .sorted(Comparator.comparing(HitboxSettings::name).thenComparing(Comparator.naturalOrder())).toList();
    }

    static List<String> search(List<String> all, Set<String> selected, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
        return all.stream().filter(id -> !selected.contains(id))
            .filter(id -> name(id).toLowerCase(Locale.ROOT).contains(needle)
                || id.toLowerCase(Locale.ROOT).replace('_', ' ').contains(needle)).toList();
    }

    static String name(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        return name + (namespace.equals("minecraft") ? "" : " (" + namespace + ")");
    }
}
