package com.github.beng420.kung.compat;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.KungConfigScreen;
import com.github.beng420.kung.config.KungSettings;
import com.github.beng420.kung.runtime.KungDeveloperAccess;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Property;

/** Kept behind KungModMenu's optional-mod check so standalone Kung never links OneConfig classes. */
final class KungOneConfig {
    private static KungOneConfigTree bindings;
    private static final List<Property<?>> hudControls = new ArrayList<>();
    private static String dynamicSettings;
    private static int ticks;

    private KungOneConfig() { }

    static List<Property<?>> hudProperties(String feature) {
        var properties = bindings.tree().map.values().stream()
            .filter(node -> node instanceof Property<?>
                && feature.equals(node.getMetadata("subcategory")) && !node.getID().endsWith("__enabled"))
            .<Property<?>>map(node -> (Property<?>) node).toList();
        hudControls.addAll(properties);
        return properties;
    }

    static void register() {
        if (bindings != null) return;
        rebuild();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks < 20) return;
            ticks = 0;
            bindings.refresh();
            // HUD panels retain their controls when an audio-list edit replaces the main settings tree.
            for (var property : hudControls) property.revaluateDisplay();
            // Rebuild changing row lists outside the native editor to preserve active text input.
            boolean oneConfigOpen = client.screen != null
                && client.screen.getClass().getName().startsWith("org.polyfrost.oneconfig.");
            if (!oneConfigOpen && !dynamicSettings.equals(dynamicSettings())) rebuild();
        });
    }

    private static void rebuild() {
        var next = new KungOneConfigTree(KungSettings.categories(() -> {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(KungConfigScreen.changelog(client.screen));
        }), KungConfig.get()::save, () -> !ConfigManager.isRebindingProfiles());
        next.applyDefaults(new KungOneConfigTree(KungSettings.defaults(), () -> { }, () -> false));
        // Replace, rather than merge, so removed file-specific controls disappear and native search refreshes.
        if (bindings != null) ConfigManager.active().unregister("kung");
        ConfigManager.active().register(next.tree());
        bindings = next;
        dynamicSettings = dynamicSettings();
    }

    private static String dynamicSettings() {
        var config = KungConfig.get().misc;
        return config.customArrowHitSounds() + "\n" + config.customWitherShieldExpireSounds()
            + "\n" + KungConfig.get().hitboxes.entities().keySet() + "\n" + config.bowDrawThresholds()
            + "\n" + KungDeveloperAccess.allowed();
    }
}
