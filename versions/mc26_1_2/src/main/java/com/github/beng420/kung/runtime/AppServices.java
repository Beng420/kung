package com.github.beng420.kung.runtime;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.message.HypixelChatSender;
import java.util.Objects;

public record AppServices(
    KungConfig config,
    DungeonStateTracker dungeonStateTracker,
    HypixelChatSender hypixelChat
) {
    public AppServices {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(dungeonStateTracker, "dungeonStateTracker");
        Objects.requireNonNull(hypixelChat, "hypixelChat");
    }

    public static AppServices create(KungConfig config, DungeonStateTracker dungeonStateTracker) {
        return new AppServices(
            config,
            dungeonStateTracker,
            HypixelChatSender.INSTANCE
        );
    }
}
