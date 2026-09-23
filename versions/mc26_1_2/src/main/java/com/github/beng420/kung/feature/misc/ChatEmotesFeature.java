package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public final class ChatEmotesFeature extends ConfigurableFeature<MiscConfig> {
    /** A direct message's recipient is a player name, never text to replace. */
    private static final Pattern DIRECT_MESSAGE = Pattern.compile("^(?:msg|m|w|tell|whisper|message)\\s+\\S+\\s*",
        Pattern.CASE_INSENSITIVE);

    public ChatEmotesFeature() {
        super(config -> config.misc);
    }

    @Override
    protected void onInitialize() {
        // These callbacks run after ChatScreen saves the original input to history.
        // Modifying the existing send also preserves signing and avoids duplicate sends.
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> replaceEmotes(message, false));
        ClientSendMessageEvents.MODIFY_COMMAND.register(command -> replaceEmotes(command, true));
    }

    @Override
    public boolean isEnabled() {
        return initialized() && config().chatEmotesEnabled();
    }

    private String replaceEmotes(String message, boolean command) {
        if (!isEnabled()) return message;
        // Keyed by the word between the colons. Custom ones first, so a user can redefine a built-in.
        Map<String, String> emotes = new LinkedHashMap<>();
        for (var emote : config().customChatEmotes()) {
            if (!emote.shortcut().isEmpty()) emotes.putIfAbsent(emote.shortcut(), emote.emote());
        }
        emotes.putIfAbsent("iman", "♲");
        emotes.putIfAbsent("ironman", "♲");
        for (var emote : emotes.entrySet()) message = message.replace(":" + emote.getKey() + ":", emote.getValue());
        if (!config().chatEmotesReplaceWords()) return message;

        // Replace In Text: the bare word too, "iman" as well as ":iman:", but never the command name
        // or a direct message's recipient.
        int textStart = command ? commandTextStart(message) : 0;
        String text = message.substring(textStart);
        for (var emote : emotes.entrySet()) {
            text = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(emote.getKey()) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).replaceAll(Matcher.quoteReplacement(emote.getValue()));
        }
        return message.substring(0, textStart) + text;
    }

    private static int commandTextStart(String command) {
        Matcher direct = DIRECT_MESSAGE.matcher(command);
        if (direct.find()) return direct.end();
        int space = command.indexOf(' ');
        return space < 0 ? command.length() : space + 1;
    }
}
