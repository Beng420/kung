package com.github.beng420.kung;

import com.github.beng420.kung.feature.FeatureRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KungMod implements ModInitializer {
    public static final String MOD_ID = "kung";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FeatureRegistry.bootstrap();
        LOGGER.info("Kung loaded with {} registered features.", FeatureRegistry.features().size());
    }
}
