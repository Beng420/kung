package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.runtime.AppServices;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ChatEmotesFeatureTest {
    @Test
    public void outgoingEventsFollowLiveSettingsWithoutCancelingChatOrCommands() {
        KungConfig config = KungConfig.get();
        MiscConfig previous = config.misc;
        config.misc = new MiscConfig();
        ChatEmotesFeature feature = new ChatEmotesFeature();
        feature.initialize(AppServices.create(config, new DungeonStateTracker()));
        try {
            assertEquals(":iman:", chat(":iman:"));
            assertEquals("pc :iman:", command("pc :iman:"));
            config.misc.setChatEmotesEnabled(true);
            assertFalse(config.misc.chatCommandsEnabled());
            assertTrue(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage(":iman:"));
            assertTrue(ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage("pc :iman:"));
            assertEquals("hi \u2672 \u2672!", chat("hi :iman: :iman:!"));
            assertEquals("pc \u2672", command("pc :iman:"));
            assertEquals("gc hi \u2672", command("gc hi :iman:"));
            assertEquals("msg Beng114 \u2672", command("msg Beng114 :iman:"));
            assertEquals("\u2672 :cute: o/ :IMAN:", chat("\u2672 :cute: o/ :IMAN:"));

            config.misc = new MiscConfig();
            assertEquals(":iman:", chat(":iman:"));
            assertEquals("pc :iman:", command("pc :iman:"));
        } finally {
            feature.shutdown();
            config.misc = previous;
        }
        assertEquals(":iman:", chat(":iman:"));
    }

    private static String chat(String message) {
        return ClientSendMessageEvents.MODIFY_CHAT.invoker().modifySendChatMessage(message);
    }

    private static String command(String command) {
        return ClientSendMessageEvents.MODIFY_COMMAND.invoker().modifySendCommandMessage(command);
    }
}
