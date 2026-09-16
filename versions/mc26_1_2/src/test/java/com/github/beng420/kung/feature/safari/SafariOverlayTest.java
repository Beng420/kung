package com.github.beng420.kung.feature.safari;

import com.github.beng420.kung.config.category.SafariConfig;
import com.github.beng420.kung.skyblock.HypixelLocation;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.Test;
import static org.junit.Assert.*;

public class SafariOverlayTest {
    @Test
    public void missingIsRedAndCaughtIsGrayWithStrikethrough() {
        Component missing = SafariOverlayFeature.critterText("Flitter", false);
        Component caught = SafariOverlayFeature.critterText("Flitter", true);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), missing.getStyle().getColor());
        assertFalse(missing.getStyle().isStrikethrough());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), caught.getStyle().getColor());
        assertTrue(caught.getStyle().isStrikethrough());
        assertEquals(missing.getString(), caught.getString());
    }

    @Test
    public void hudEditorBoundsUseSavedPositionAndScale() {
        SafariConfig config = new SafariConfig();
        var initial = SafariOverlayFeature.overlayBounds(config);
        config.setX(71);
        config.setY(-3);
        config.setScale(150);
        var doubled = SafariOverlayFeature.overlayBounds(config);
        assertEquals(71, doubled.x());
        assertEquals(-3, doubled.y());
        assertEquals(initial.width() * 2, doubled.width());
        assertTrue(Math.abs(initial.height() * 2 - doubled.height()) <= 1);
    }

    @Test
    public void onlyHypixelHostsAreAccepted() {
        assertTrue(HypixelLocation.isHypixelAddress("mc.hypixel.net:25565"));
        assertTrue(HypixelLocation.isHypixelAddress("HYPIXEL.NET."));
        assertFalse(HypixelLocation.isHypixelAddress("hypixel.net.example.com"));
        assertFalse(HypixelLocation.isHypixelAddress("fakehypixel.net"));
        assertFalse(HypixelLocation.isHypixelAddress("localhost"));
    }

    @Test
    public void observesCanceledChatBeforeModificationAndIgnoresActionBars() {
        AtomicBoolean testing = new AtomicBoolean(true);
        AtomicInteger observed = new AtomicInteger();
        SafariSession session = new SafariSession();
        session.select(1, "Critter Safari");
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !testing.get());
        SafariOverlayFeature.registerMessageObserver(text -> {
            if (testing.get()) {
                observed.incrementAndGet();
                session.observeMessage(text, "Beng114");
            }
        });
        Component catchMessage = Component.literal("CAPTURE! You caught a Flitter and gained a Flitter Shard!");
        try {
            assertFalse(ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(catchMessage, false));
            assertEquals(1, observed.get());
            assertEquals(1, session.caught().size());
            ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(catchMessage, true);
            assertEquals(1, observed.get());
            ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(catchMessage, false);
            assertEquals(1, observed.get());
        } finally {
            testing.set(false);
        }
    }
}
