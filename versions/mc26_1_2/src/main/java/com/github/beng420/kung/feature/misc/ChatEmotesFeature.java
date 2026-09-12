package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public final class ChatEmotesFeature extends ConfigurableFeature<MiscConfig> implements Feature {
    public ChatEmotesFeature() {
        super(config -> config.misc);
    }

    @Override
    protected void onInitialize() {
        // These callbacks run after ChatScreen saves the original input to history.
        // Modifying the existing send also preserves signing and avoids duplicate sends.
        ClientSendMessageEvents.MODIFY_CHAT.register(this::replaceEmotes);
        ClientSendMessageEvents.MODIFY_COMMAND.register(this::replaceEmotes);
    }

    @Override
    public boolean isEnabled() {
        return initialized() && config().chatEmotesEnabled();
    }

    private String replaceEmotes(String message) {
        return isEnabled() ? message.replace(":iman:", "\u2672") : message;
    }
}
