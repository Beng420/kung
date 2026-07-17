package com.github.beng420.kung.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeatureRegistry {
    private static final List<Feature> FEATURES = new ArrayList<>();
    private static boolean bootstrapped;

    private FeatureRegistry() {
    }

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }

        register(new Feature(
            "skyblock-context",
            "SkyBlock Context",
            true,
            "Detects whether the player is in a Hypixel SkyBlock related context."
        ));
        register(new Feature(
            "debug-overlay",
            "Debug Overlay",
            false,
            "Shows development-only state while building new client-side helpers."
        ));
        register(DungeonMapFeature.definition());

        bootstrapped = true;
    }

    public static List<Feature> features() {
        return Collections.unmodifiableList(FEATURES);
    }

    private static void register(Feature feature) {
        FEATURES.add(feature);
    }
}
