package com.github.beng420.kung.feature.misc.commands;

import com.github.beng420.kung.feature.misc.ChatCommandsFeature;
import net.minecraft.client.Minecraft;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ChatCommand {
    /** Pattern that triggers this command. */
    Pattern pattern();

    /** Returns whether this command is enabled for the selected channel. */
    boolean isEnabled(ChatCommandsFeature.ChatChannel channel);

    /** Executes the command and passes its response to the consumer. */
    void execute(
        Minecraft client,
        ChatCommandsFeature.ChatChannel channel,
        String senderName,
        String targetName,
        Matcher matcher,
        java.util.function.Consumer<String> responseConsumer
    );
}
