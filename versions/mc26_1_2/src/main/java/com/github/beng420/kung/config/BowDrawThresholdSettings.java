package com.github.beng420.kung.config;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.config.category.MiscConfig.BowDrawThreshold;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Reuses the live list pattern from Hitboxes without recreating controls while a value changes. */
final class BowDrawThresholdSettings implements Supplier<List<SettingEntry>> {
    private final MiscConfig config;
    private List<BowDrawThreshold> entries = List.of();
    private List<SettingEntry> rows = List.of();

    BowDrawThresholdSettings(MiscConfig config) { this.config = config; }

    @Override
    public List<SettingEntry> get() {
        if (!rows.isEmpty() && entries.equals(config.bowDrawThresholds())) return rows;
        entries = List.copyOf(config.bowDrawThresholds());
        var next = new ArrayList<SettingEntry>();
        next.add(SettingEntry.button("Add Threshold", "+", () -> {
            config.addBowDrawThreshold();
            showList();
        }).withTooltip("Add a marker from 0 to 20 server ticks. Maximum 20 entries."));
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            String name = "Threshold " + (index + 1);
            next.add(SettingEntry.colorRemove(name, entry::color, () -> editColor(name, entry), () -> {
                config.removeBowDrawThreshold(entry);
                showList();
            }).withTooltip("Click the color to change this marker's line; the red - removes it."));
            next.add(SettingEntry.slider("Ticks", entry::ticks, ticks -> config.setBowDrawThreshold(entry, ticks), 0, 20, 1)
                .withTooltip("3 ticks: minimum shot. 20 ticks: full power. Duplicate markers share one line."));
        }
        return rows = List.copyOf(next);
    }

    private void editColor(String name, BowDrawThreshold entry) {
        Minecraft client = Minecraft.getInstance();
        Screen parent = client.screen;
        // The optional native menu delegates the color to the same popup the Kung menu uses.
        if (!(parent instanceof KungConfigScreen)) {
            parent = KungConfigScreen.fromParent(parent, "Bow Draw Indicator");
            client.setScreen(parent);
        }
        client.setScreen(new HitboxEditorScreen(parent, name + " Color", entry.color(),
            color -> config.setBowDrawThresholdColor(entry, color)));
    }

    private static void showList() {
        var client = Minecraft.getInstance();
        if (client != null && !(client.screen instanceof KungConfigScreen)) {
            client.setScreen(KungConfigScreen.fromParent(client.screen, "Bow Draw Indicator"));
        }
    }
}
