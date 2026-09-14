package com.github.beng420.kung.feature.garden;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeastMessageRoutingTest {
    @Test
    public void countsBeforeChatModificationAndCancellationWithoutCountingActionBarsOrTwice() {
        var active = new AtomicBoolean(true);
        var hidden = new AtomicBoolean(false);
        var session = new FeastSession();
        session.select(new FeastContext.Event(FeastProgress.Kind.GRAND, "grand:513"), true);
        session.observeMenu(new Object(), new FeastProgress.Snapshot(FeastProgress.Kind.GRAND, 27,
            List.of(5, 25, 75, 150, 250, 350, 450, 550, 750)));
        session.closeMenu();
        var kernels = new FeastKernels();
        kernels.observeMenu(new Object(), 1_234L);
        var observed = new ArrayList<String>();
        // Register a canceling mod first: Feast must still receive the message.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !active.get() || !hidden.get());
        FeastOverlayFeature.registerMessageObserver(text -> {
            if (active.get()) {
                observed.add(text);
                session.donate(text);
                kernels.observeMessage(text);
            }
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
            active.get() ? Component.literal("Reformatted chat output") : message);
        String seasoning = "RARE CROP! Seasoning (+80\uE02B) (automatically donated)";
        try {
            assertTrue(receive(seasoning, false));
            assertEquals(List.of(seasoning), observed);
            assertEquals(28, session.snapshot().donations());

            // Identical text in a second server event represents another drop, not a duplicate.
            assertTrue(receive(seasoning, false));
            assertEquals(29, session.snapshot().donations());
            hidden.set(true);
            assertFalse(receive(seasoning, false));
            assertEquals(30, session.snapshot().donations());
            assertEquals(3, observed.size());
            assertEquals(Long.valueOf(1_234), kernels.balance());

            assertFalse(receive(seasoning, true));
            assertEquals(30, session.snapshot().donations());
            assertEquals(3, observed.size());
            assertFalse(receive("[NPC] Feast Chef Ted: Thanks for the donation! I've added a Kernel to your purse.", false));
            assertEquals(Long.valueOf(1_235), kernels.balance());
            assertEquals(30, session.snapshot().donations());
        } finally {
            active.set(false);
        }
    }

    // Exercise Fabric's complete delivery sequence, including its post-filter events.
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
