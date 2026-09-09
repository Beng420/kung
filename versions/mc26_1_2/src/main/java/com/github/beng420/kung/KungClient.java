package com.github.beng420.kung;

import com.github.beng420.kung.command.KungCommands;
import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.feature.FeatureRegistry;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.runtime.AppServices;
import com.github.beng420.kung.runtime.ServiceRegistry;
import com.github.beng420.kung.update.KungUpdater;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class KungClient implements ClientModInitializer {
    private final DungeonStateTracker dungeonStateTracker = new DungeonStateTracker();
    private final AppServices services = AppServices.create(KungConfig.INSTANCE, dungeonStateTracker);

    @Override
    public void onInitializeClient() {
        KungConfig.INSTANCE.load();
        ServiceRegistry.initializeClient(services);
        FeatureRegistry.initializeClient(services);
        KungCommands.register(services);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            FeatureRegistry.shutdown();
            ServiceRegistry.shutdown();
        });
        KungUpdater.INSTANCE.installPendingUpdateIfReady();
        KungUpdater.INSTANCE.checkForUpdatesAsync();
        KungMod.LOGGER.info("Kung client entrypoint ready.");
    }
}
