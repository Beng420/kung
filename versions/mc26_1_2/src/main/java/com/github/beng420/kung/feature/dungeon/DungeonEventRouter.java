package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
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
        registerMessageObservers(tracker::observeGameMessage, tracker::observeChatMessage);
    }

    static void registerMessageObservers(BiConsumer<Component, Boolean> game, Consumer<Component> chat) {
        // Observe original messages even when another listener hides or rewrites their display.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            game.accept(message, overlay);
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            chat.accept(message);
            return true;
        });
    }

    public static void observeEntityDeath(Entity entity) {
        DungeonEventRouter router = activeRouter;
        if (router != null && entity != null) {
            router.tracker.observeEntityDeath(entity);
        }
    }

    public static void observeSkyblockerBonus(DungeonBonusContribution bonus) {
        DungeonEventRouter router = activeRouter;
        if (router == null) return;
        try {
            router.tracker.observeSkyblockerBonus(bonus);
        } catch (RuntimeException | LinkageError exception) {
            KungMod.LOGGER.warn("Failed to process Skyblocker dungeon bonus.", exception);
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
