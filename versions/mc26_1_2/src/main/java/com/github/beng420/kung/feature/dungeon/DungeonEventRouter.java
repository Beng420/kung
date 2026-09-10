package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.world.entity.Entity;

public final class DungeonEventRouter {
    private static DungeonEventRouter activeRouter;

    private final DungeonStateTracker tracker;

    public DungeonEventRouter(DungeonStateTracker tracker) {
        this.tracker = java.util.Objects.requireNonNull(tracker, "tracker");
    }

    public void register() {
        activeRouter = this;
        ClientTickEvents.END_CLIENT_TICK.register(tracker::tick);
        DungeonServerTickEvents.register(tracker::serverTick);
        ClientReceiveMessageEvents.GAME.register(tracker::observeGameMessage);
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            tracker.observeChatMessage(message));
    }

    public static void observeEntityDeath(Entity entity) {
        DungeonEventRouter router = activeRouter;
        if (router != null && entity != null) {
            router.tracker.observeEntityDeath(entity);
        }
    }

    public static void observeInstanceChanged() {
        DungeonEventRouter router = activeRouter;
        if (router != null) {
            try {
                router.tracker.synchronizeInstance(net.minecraft.client.Minecraft.getInstance());
            } catch (RuntimeException | LinkageError exception) {
                KungMod.LOGGER.warn("Failed to synchronize dungeon instance; retrying on the next client tick.", exception);
            }
        }
    }
}
