package com.github.beng420.kung;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KungMod implements ModInitializer {
    public static final String MOD_ID = "kung";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Kung loaded.");
    }
}
