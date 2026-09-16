package com.github.beng420.kung.compat;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungHudEditorState;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** This entrypoint has no OneConfig/Mod Menu types, including in its signatures. */
public final class KungOneConfigBridge {
    private KungOneConfigBridge() { }

    public static void initialize(DungeonStateTracker tracker) {
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("oneconfig") && !loader.isModLoaded("oneconfigv1")) return;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                KungOneConfig.register();
                KungOneConfigHuds.register(tracker);
            } catch (RuntimeException | LinkageError failure) {
                KungHudEditorState.setExternalEditor(() -> false);
                KungMod.LOGGER.warn("Unable to register OneConfig HUDs; /kung hud remains available.", failure);
            }
        });
    }
}
