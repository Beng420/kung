package com.github.beng420.kung.runtime;

import com.github.beng420.kung.feature.dungeon.BloodRushHelperFeature;
import com.github.beng420.kung.feature.screen.ScreenTracker;
import com.github.beng420.kung.skyblock.HypixelGuildTracker;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.skyblock.HypixelPartyTracker;
import com.github.beng420.kung.skyblock.SkyBlockMayorTracker;
import com.github.beng420.kung.util.CatacombsRecentXpTracker;
import java.util.List;

public final class ServiceRegistry {
    private static final List<ClientService> SERVICES = List.of(BloodRushHelperFeature.INSTANCE);
    private static boolean initialized;

    private ServiceRegistry() {
    }

    public static void initializeClient(AppServices services) {
        if (initialized) {
            return;
        }

        HypixelInstanceTracker.initializeClient();
        HypixelPartyTracker.initializeClient();
        HypixelGuildTracker.initializeClient();
        SkyBlockMayorTracker.initializeClient();
        CatacombsRecentXpTracker.initializeClient();
        services.dungeonStateTracker().initializeClient();
        ScreenTracker.initializeClient();
        SERVICES.forEach(service -> service.initialize(services));
        initialized = true;
    }

    public static List<ClientService> services() {
        return SERVICES;
    }

    public static void reset() {
        SERVICES.forEach(ClientService::reset);
    }

    public static void shutdown() {
        for (int index = SERVICES.size() - 1; index >= 0; index--) {
            SERVICES.get(index).shutdown();
        }
        initialized = false;
    }
}
