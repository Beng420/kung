package com.github.beng420.kung.skyblock;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.junit.Test;

public final class HypixelDungeonFloorMessageTest {
    private static final String ENTRY = "-----------------------------\n[MVP+] Beng114 entered MM The Catacombs, Floor VII!\n-----------------------------";
    private static final HypixelLocation CATACOMBS = new HypixelLocation(HypixelLocation.Kind.CATACOMBS, "The Catacombs");

    @Test public void originalEntrySurvivesDisplayFormattingAndFilteringWithoutAcceptingActionbar() {
        var active = new AtomicBoolean(true);
        var hidden = new AtomicBoolean(false);
        var received = new AtomicInteger();
        var state = new HypixelDungeonFloorState();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !active.get() || !hidden.get());
        HypixelInstanceTracker.registerFloorEntryObserver(message -> {
            if (!active.get()) return;
            received.incrementAndGet();
            state.entryMessage(message, 1_000L);
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
            active.get() ? Component.literal("[16:42:29] " + message.getString()) : message);
        try {
            for (boolean cancel : List.of(false, true)) {
                state.reset();
                hidden.set(cancel);
                int before = received.get();
                assertEquals(!cancel, receive(ENTRY, false));
                assertEquals(before + 1, received.get());
                assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
                state.worldChanged(2_000L);
                state.observe(CATACOMBS, List.of("The Catacombs"), 2_001L);
                assertEquals(new HypixelDungeonFloor(7, true), state.current());
            }
            state.reset();
            int before = received.get();
            receive(ENTRY, true);
            assertEquals(before, received.get());
            state.worldChanged(2_000L);
            state.observe(CATACOMBS, List.of("The Catacombs"), 2_001L);
            assertEquals(HypixelDungeonFloor.UNKNOWN, state.current());
        } finally {
            active.set(false);
        }
    }

    private static boolean receive(String text, boolean overlay) {
        Component message = Component.literal(text);
        boolean allowed = ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(message, overlay);
        if (allowed) {
            message = ClientReceiveMessageEvents.MODIFY_GAME.invoker().modifyReceivedGameMessage(message, overlay);
            ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(message, overlay);
        } else {
            ClientReceiveMessageEvents.GAME_CANCELED.invoker().onReceiveGameMessageCanceled(message, overlay);
        }
        return allowed;
    }
}
