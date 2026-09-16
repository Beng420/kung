package com.github.beng420.kung.compat;

import com.github.beng420.kung.config.KungConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional Mod Menu API entrypoint, also discovered by OneConfig's compatibility bridge. */
public final class KungModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<KungConfigScreen> getModConfigScreenFactory() {
        return KungConfigScreen::fromParent;
    }
}
