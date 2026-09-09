package com.github.beng420.kung.feature.misc.commands;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.misc.ChatCommandsFeature;
import com.github.beng420.kung.util.CatacombsAverageCalculator;
import com.github.beng420.kung.util.CatacombsAverageCalculator.Goal;
import com.github.beng420.kung.util.KungDebugRecorder;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CataChatCommand implements ChatCommand {
    private static final CatacombsAverageCalculator CALCULATOR = new CatacombsAverageCalculator();
    private final String commandName;
    private final Goal goal;
    private final Pattern pattern;

    public CataChatCommand(String commandName, Goal goal) {
        this.commandName = commandName;
        this.goal = goal;
        this.pattern = Pattern.compile("^!" + commandName + "(?:\\s+(?<name>[A-Za-z0-9_]{1,16}))?\\s*$", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public Pattern pattern() {
        return pattern;
    }

    @Override
    public boolean isEnabled(ChatCommandsFeature.ChatChannel channel) {
        MiscConfig config = KungConfig.get().misc;
        if (!config.chatCommandsEnabled() || !commandEnabled(config)) {
            return false;
        }
        return switch (channel) {
            case PARTY -> commandPartyEnabled(config);
            case GUILD -> commandGuildEnabled(config);
            case ALL -> commandAllChatEnabled(config);
            case PRIVATE -> commandPrivateEnabled(config);
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
        CALCULATOR.calculateAsync(targetName, goal, false).whenComplete((result, throwable) -> client.execute(() -> {
            if (throwable != null) {
                KungMod.LOGGER.warn("Failed to answer !" + commandName + " command.", throwable);
                responseConsumer.accept("Could not calculate " + goal.id().toUpperCase(Locale.ROOT) + " for " + targetName + ".");
                return;
            }
            if (result.debugDetails() != null && !result.debugDetails().isBlank()) {
                KungDebugRecorder.event("cata-result", "command=" + goal.id() + " channel=" + channel.name() + " target=" + targetName
                    + " success=" + result.success() + " " + result.debugDetails());
            }
            if (!result.success()) {
                responseConsumer.accept("Could not calculate " + goal.id().toUpperCase(Locale.ROOT) + " for " + targetName + ": " + result.message());
                return;
            }
            responseConsumer.accept(result.message());
        }));
    }

    private boolean commandEnabled(MiscConfig config) {
        return goal == Goal.CATACOMBS_50 ? config.c50ChatCommandEnabled() : config.ca50ChatCommandEnabled();
    }

    private boolean commandPartyEnabled(MiscConfig config) {
        return goal == Goal.CATACOMBS_50 ? config.c50PartyCommandsEnabled() : config.ca50PartyCommandsEnabled();
    }

    private boolean commandGuildEnabled(MiscConfig config) {
        return goal == Goal.CATACOMBS_50 ? config.c50GuildCommandsEnabled() : config.ca50GuildCommandsEnabled();
    }

    private boolean commandAllChatEnabled(MiscConfig config) {
        return goal == Goal.CATACOMBS_50 ? config.c50AllChatCommandsEnabled() : config.ca50AllChatCommandsEnabled();
    }

    private boolean commandPrivateEnabled(MiscConfig config) {
        return goal == Goal.CATACOMBS_50 ? config.c50PrivateCommandsEnabled() : config.ca50PrivateCommandsEnabled();
    }
}
