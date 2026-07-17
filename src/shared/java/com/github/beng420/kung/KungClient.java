package com.github.beng420.kung;

import net.fabricmc.api.ClientModInitializer;

public final class KungClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KungMod.LOGGER.info("Kung client entrypoint ready.");
    }
}
