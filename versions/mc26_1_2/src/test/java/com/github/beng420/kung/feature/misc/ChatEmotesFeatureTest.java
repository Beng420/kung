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
            assertEquals(":iman: :ironman:", chat(":iman: :ironman:"));
            assertEquals("pc :ironman:", command("pc :ironman:"));
            config.misc.setChatEmotesEnabled(true);
            assertFalse(config.misc.chatCommandsEnabled());
            assertTrue(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage(":iman:"));
            assertTrue(ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage("pc :iman:"));
            assertEquals("hi \u2672 \u2672!", chat("hi :iman: :iman:!"));
            assertEquals("pc \u2672", command("pc :iman:"));
            assertEquals("gc hi \u2672", command("gc hi :iman:"));
            assertEquals("msg Beng114 \u2672", command("msg Beng114 :iman:"));
            assertEquals("hi \u2672 \u2672 \u2672!", chat("hi :ironman: :iman: :ironman:!"));
            assertEquals("pc \u2672", command("pc :ironman:"));
            assertEquals("gc \u2672 \u2672", command("gc :iman: :ironman:"));
            assertEquals("msg Beng114 \u2672", command("msg Beng114 :ironman:"));
            assertEquals("\u2672 :cute: o/ :IMAN: :IRONMAN:", chat("\u2672 :cute: o/ :IMAN: :IRONMAN:"));

            // Custom emotes: exact text, applied before the built-ins; blank shortcuts do nothing.
            config.misc.addCustomChatEmote();
            var gg = config.misc.customChatEmotes().getFirst();
            assertEquals(":gg:", chat(":gg:"));
            // Stored as the word between the colons, whether typed with them or not.
            config.misc.setChatEmoteShortcut(gg, "gg");
            assertEquals("gg", chat("gg"));
            config.misc.setChatEmoteShortcut(gg, " :gg: ");
            assertEquals("gg", gg.shortcut());
            config.misc.setChatEmoteText(gg, "\u2714");
            assertEquals("\u2714 well played \u2714", chat(":gg: well played :gg:"));
            assertEquals("pc \u2714", command("pc :gg:"));
            config.misc.addCustomChatEmote();
            config.misc.setChatEmoteShortcut(config.misc.customChatEmotes().getLast(), ":iman:");
            config.misc.setChatEmoteText(config.misc.customChatEmotes().getLast(), "IM");
            assertEquals("IM", chat(":iman:"));
            // Emotes of any length go through as typed.
            config.misc.setChatEmoteText(gg, "(\u2310\u25A0_\u25A0)");
            assertEquals("pc (\u2310\u25A0_\u25A0)", command("pc :gg:"));
            config.misc.removeCustomChatEmote(gg);
            assertEquals(":gg:", chat(":gg:"));

            // A custom override of a built-in also covers its bare word; then back to the built-in.
            config.misc.setChatEmotesReplaceWords(true);
            assertEquals("IM", chat("iman"));
            config.misc.setChatEmotesReplaceWords(false);
            config.misc.removeCustomChatEmote(config.misc.customChatEmotes().getLast());

            // Replace In Text: bare whole words too, but not inside words, command names or recipients.
            assertEquals("but i am on an iman account", chat("but i am on an iman account"));
            config.misc.setChatEmotesReplaceWords(true);
            assertEquals("but i am on an ♲ account", chat("but i am on an iman account"));
            assertEquals("♲, Ironmanship", chat("Ironman, Ironmanship"));
            assertEquals("pc ♲ now", command("pc iman now"));
            assertEquals("msg iman hi ♲", command("msg iman hi iman"));

            config.misc = new MiscConfig();
            assertEquals(":iman: :ironman:", chat(":iman: :ironman:"));
            assertEquals("pc :iman: :ironman:", command("pc :iman: :ironman:"));
        } finally {
            feature.shutdown();
            config.misc = previous;
        }
        assertEquals(":iman: :ironman:", chat(":iman: :ironman:"));
    }

    private static String chat(String message) {
        return ClientSendMessageEvents.MODIFY_CHAT.invoker().modifySendChatMessage(message);
    }

    private static String command(String command) {
        return ClientSendMessageEvents.MODIFY_COMMAND.invoker().modifySendCommandMessage(command);
    }
}
