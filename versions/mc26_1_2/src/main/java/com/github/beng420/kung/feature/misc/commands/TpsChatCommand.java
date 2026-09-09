package com.github.beng420.kung.feature.misc.commands;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.misc.ChatCommandsFeature;
import com.github.beng420.kung.util.ServerTpsTracker;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

public final class TpsChatCommand implements ChatCommand {
    private static final Pattern PATTERN = Pattern.compile("^!tps\\s*$", Pattern.CASE_INSENSITIVE);

    @Override
    public Pattern pattern() {
        return PATTERN;
    }

    @Override
    public boolean isEnabled(ChatCommandsFeature.ChatChannel channel) {
        MiscConfig config = KungConfig.get().misc;
        if (!config.chatCommandsEnabled() || !config.tpsChatCommandEnabled()) {
            return false;
        }
        return switch (channel) {
            case PARTY -> config.tpsPartyCommandsEnabled();
            case GUILD -> config.tpsGuildCommandsEnabled();
            case ALL -> config.tpsAllChatCommandsEnabled();
            case PRIVATE -> config.tpsPrivateCommandsEnabled();
        };
    }

    @Override
    public void execute(
        Minecraft client,
        ChatCommandsFeature.ChatChannel channel,
        String senderName,
        String targetName,
        Matcher matcher,
        Consumer<String> responseConsumer
    ) {
        responseConsumer.accept(ServerTpsTracker.INSTANCE.snapshot().message());
    }
}
