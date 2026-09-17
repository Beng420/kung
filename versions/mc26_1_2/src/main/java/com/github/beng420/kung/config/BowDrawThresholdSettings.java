package com.github.beng420.kung.config;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.config.category.MiscConfig.BowDrawThreshold;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

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
        }).withTooltip("Add a marker from 1 to 20 server ticks. Maximum 20 entries."));
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            next.add(SettingEntry.stepperRemove("Threshold " + (index + 1), entry::ticks,
                ticks -> config.setBowDrawThreshold(entry, ticks), 1, 20, 1, () -> {
                    config.removeBowDrawThreshold(entry);
                    showList();
                }).withTooltip("Use - / + to change server ticks; the red - removes this marker.",
                    "3 ticks: minimum shot. 20 ticks: full power. Duplicate markers share one line."));
        }
        return rows = List.copyOf(next);
    }

    private static void showList() {
        var client = Minecraft.getInstance();
        if (client != null && !(client.screen instanceof KungConfigScreen)) {
            client.setScreen(KungConfigScreen.fromParent(client.screen, "Bow Draw Indicator"));
        }
    }
}
