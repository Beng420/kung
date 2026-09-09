package com.github.beng420.kung.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class RoomSyncCommandGroup {
    private RoomSyncCommandGroup() {
    }

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root, DungeonStateTracker tracker) {
        root.then(literal("roomsync")
                .then(literal("status").executes(KungCommandActions::roomSyncStatus))
                .then(literal("ping").executes(KungCommandActions::pingRoomSync))
                .then(literal("enable").executes(context -> KungCommandActions.setRoomSyncEnabled(context, true)))
                .then(literal("disable").executes(context -> KungCommandActions.setRoomSyncEnabled(context, false)))
                .then(literal("upload").then(argument("enabled", BoolArgumentType.bool())
                    .executes(KungCommandActions::setRoomSyncUpload)))
                .then(literal("server").then(argument("url", StringArgumentType.greedyString())
                    .executes(KungCommandActions::setRoomSyncServer)))
                .then(literal("token")
                    .then(literal("clear").executes(KungCommandActions::clearRoomSyncToken))
                    .then(argument("token", StringArgumentType.greedyString()).executes(KungCommandActions::setRoomSyncToken)))
                .then(literal("pull").executes(KungCommandActions::pullRoomSync))
                .then(literal("push").then(argument("input", StringArgumentType.greedyString())
                    .suggests(KungCommandActions::suggestRoomNames)
                    .executes(context -> KungCommandActions.pushRoomSyncInput(context, tracker)))))
            .then(literal("checkserverconnection").executes(KungCommandActions::pingRoomSync));
    }
}
