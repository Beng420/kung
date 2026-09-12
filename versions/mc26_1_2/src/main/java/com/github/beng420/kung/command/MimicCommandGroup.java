package com.github.beng420.kung.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.github.beng420.kung.feature.dungeon.DungeonMimicStaticChests;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

final class MimicCommandGroup {
    private MimicCommandGroup() { }

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root, DungeonStateTracker tracker) {
        root.then(literal("mimic")
            .executes(context -> reply(context.getSource(), DungeonMimicStaticChests.status(context.getSource().getClient(), tracker)))
            .then(literal("ignore").executes(context -> reply(context.getSource(),
                DungeonMimicStaticChests.confirmLookedChest(context.getSource().getClient(), tracker))))
            .then(literal("undo").executes(context -> reply(context.getSource(), DungeonMimicStaticChests.undo())))
            .then(literal("copy").executes(context -> reply(context.getSource(),
                DungeonMimicStaticChests.copyCaptures(context.getSource().getClient()))))
        );
    }

    private static int reply(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal("[Kung] " + message));
        return 1;
    }
}
