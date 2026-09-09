package com.github.beng420.kung.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class UserCommandGroup {
    private UserCommandGroup() {
    }

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root, DungeonStateTracker tracker) {
        root.then(literal("calculatecata")
                .executes(context -> KungCommandActions.openCatacombsCalculator(context, ""))
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> KungCommandActions.openCatacombsCalculator(
                        context,
                        StringArgumentType.getString(context, "username")
                    ))))
            .then(literal("settings").executes(KungCommandActions::openSettings))
            .then(literal("hud").executes(context -> KungCommandActions.openHudEditor(context, tracker)));
    }
}
