package com.github.beng420.kung.feature;

import com.github.beng420.kung.feature.dungeon.DungeonChatFilterFeature;
import com.github.beng420.kung.feature.dungeon.DungeonMapFeature;
import com.github.beng420.kung.feature.dungeon.DungeonSplitsOverlayFeature;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.feature.misc.ChatCommandsFeature;
import com.github.beng420.kung.feature.misc.CustomSoundsFeature;
import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.feature.misc.LobbyHopHelperFeature;
import com.github.beng420.kung.feature.misc.SuperpairsHelperFeature;
import com.github.beng420.kung.feature.slayer.TarantulaHelperFeature;
import com.github.beng420.kung.runtime.AppServices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeatureRegistry {
    private static final List<Feature> FEATURES = new ArrayList<>();
    private static boolean bootstrapped;

    private FeatureRegistry() {
    }

    public static void initializeClient(AppServices services) {
        if (bootstrapped) {
            return;
        }

        DungeonStateTracker tracker = services.dungeonStateTracker();
        register(new DungeonMapFeature(tracker));
        register(new DungeonChatFilterFeature(tracker));
        register(new DungeonSplitsOverlayFeature(tracker));
        register(ChatCommandsFeature.INSTANCE);
        register(CustomSoundsFeature.INSTANCE);
        register(LoadoutsAutoCloseFeature.INSTANCE);
        register(LobbyHopHelperFeature.INSTANCE);
        register(SuperpairsHelperFeature.INSTANCE);
        register(TarantulaHelperFeature.INSTANCE);

        bootstrapped = true;
        FEATURES.forEach(feature -> feature.initialize(services));
    }

    public static List<Feature> features() {
        return Collections.unmodifiableList(FEATURES);
    }

    public static void reset() {
        FEATURES.forEach(Feature::reset);
    }

    public static void shutdown() {
        for (int index = FEATURES.size() - 1; index >= 0; index--) {
            FEATURES.get(index).shutdown();
        }
        FEATURES.clear();
        bootstrapped = false;
    }

    private static void register(Feature feature) {
        FEATURES.add(feature);
    }
}
