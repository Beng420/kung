package com.github.beng420.kung.feature;

import com.github.beng420.kung.feature.dungeon.DungeonMapFeature;
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
