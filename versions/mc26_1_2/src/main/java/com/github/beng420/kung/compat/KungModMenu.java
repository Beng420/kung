package com.github.beng420.kung.compat;

import com.github.beng420.kung.config.KungConfigScreen;
import com.github.beng420.kung.KungMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Optional Mod Menu API entrypoint, also discovered by OneConfig's compatibility bridge. */
public final class KungModMenu implements ModMenuApi {
    private static boolean integrationFailed;

    @Override
    public ConfigScreenFactory<KungConfigScreen> getModConfigScreenFactory() {
        return parent -> {
            if (!integrationFailed && (FabricLoader.getInstance().isModLoaded("oneconfig")
                || FabricLoader.getInstance().isModLoaded("oneconfigv1"))) {
                try {
                    KungOneConfig.register();
                } catch (RuntimeException | LinkageError failure) {
                    integrationFailed = true;
                    KungMod.LOGGER.warn("Could not register native OneConfig settings; using Kung's settings screen.", failure);
                }
            }
            return KungConfigScreen.fromParent(parent);
        };
    }
}
