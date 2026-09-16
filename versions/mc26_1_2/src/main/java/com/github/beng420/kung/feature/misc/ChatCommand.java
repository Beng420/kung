package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.misc.ChatCommandsFeature.ChatChannel;
import com.github.beng420.kung.util.CatacombsAverageCalculator;
import com.github.beng420.kung.util.CatacombsAverageCalculator.Goal;
import com.github.beng420.kung.util.KungDebugRecorder;
import com.github.beng420.kung.util.ServerTpsTracker;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

enum ChatCommand {
    C50(Goal.CATACOMBS_50), CA50(Goal.CLASS_AVERAGE_50), TPS(null);

    private static final CatacombsAverageCalculator CALCULATOR = new CatacombsAverageCalculator();
    private final Goal goal;
    final Pattern pattern;

    ChatCommand(Goal goal) {
        this.goal = goal;
        pattern = Pattern.compile("^!" + name().toLowerCase(Locale.ROOT)
            + (goal == null ? "" : "(?:\\s+(?<name>[A-Za-z0-9_]{1,16}))?") + "\\s*$", Pattern.CASE_INSENSITIVE);
    }

    String target(Matcher match) { return this == TPS ? null : match.group("name"); }

    boolean isEnabled(MiscConfig config, ChatChannel channel) {
        if (!config.chatCommandsEnabled()) return false;
        return switch (this) {
            case C50 -> config.c50ChatCommandEnabled() && switch (channel) {
                case PARTY -> config.c50PartyCommandsEnabled();
                case GUILD -> config.c50GuildCommandsEnabled();
                case ALL -> config.c50AllChatCommandsEnabled();
                case PRIVATE -> config.c50PrivateCommandsEnabled();
            };
            case CA50 -> config.ca50ChatCommandEnabled() && switch (channel) {
                case PARTY -> config.ca50PartyCommandsEnabled();
                case GUILD -> config.ca50GuildCommandsEnabled();
                case ALL -> config.ca50AllChatCommandsEnabled();
                case PRIVATE -> config.ca50PrivateCommandsEnabled();
            };
            case TPS -> config.tpsChatCommandEnabled() && switch (channel) {
                case PARTY -> config.tpsPartyCommandsEnabled();
                case GUILD -> config.tpsGuildCommandsEnabled();
                case ALL -> config.tpsAllChatCommandsEnabled();
                case PRIVATE -> config.tpsPrivateCommandsEnabled();
            };
        };
    }

    void execute(Minecraft client, ChatChannel channel, String targetName, Consumer<String> responseConsumer) {
        if (this == TPS) {
            responseConsumer.accept(ServerTpsTracker.INSTANCE.snapshot().message());
            return;
        }
        CALCULATOR.calculateAsync(targetName, goal, false).whenComplete((result, throwable) -> client.execute(() -> {
            if (throwable != null) {
                KungMod.LOGGER.warn("Failed to answer !" + name().toLowerCase(Locale.ROOT) + " command.", throwable);
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
}
