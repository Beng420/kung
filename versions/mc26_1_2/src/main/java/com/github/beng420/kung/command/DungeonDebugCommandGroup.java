package com.github.beng420.kung.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.github.beng420.kung.feature.dungeon.DungeonSplitTracker;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

final class DungeonDebugCommandGroup {
    private DungeonDebugCommandGroup() {
    }

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root, DungeonStateTracker tracker) {
        root.then(literal("dungeon").then(literal("debug")
                .executes(context -> KungCommandActions.copyUltraDebug(context, tracker))))
            .then(literal("instance").executes(KungCommandActions::debugInstanceContext))
            .then(literal("log")
                .executes(context -> KungCommandActions.copyDebugLog(context, 5000))
                .then(literal("copy").executes(context -> KungCommandActions.copyDebugLog(context, 5000)))
                .then(literal("tail")
                    .executes(context -> KungCommandActions.copyDebugLog(context, 200))
                    .then(argument("lines", IntegerArgumentType.integer(1, 5000))
                        .executes(context -> KungCommandActions.copyDebugLog(
                            context,
                            IntegerArgumentType.getInteger(context, "lines")
                        ))))
                .then(literal("save").executes(KungCommandActions::saveDebugLog))
                .then(literal("clear").executes(KungCommandActions::clearDebugLog)))
            .then(literal("test")
                .then(literal("title")
                    .executes(context -> KungCommandActions.testDoorTitle(context, tracker, 4))
                    .then(argument("doors", IntegerArgumentType.integer(0, 20))
                        .executes(context -> KungCommandActions.testDoorTitle(context, tracker))))
                .then(literal("dungeonstart")
                    .executes(context -> KungCommandActions.testDungeonStartTitle(context, tracker, 4))
                    .then(argument("doors", IntegerArgumentType.integer(0, 20))
                        .executes(context -> KungCommandActions.testDungeonStartTitle(context, tracker)))))
            .then(literal("door").then(literal("probe")
                .executes(KungCommandActions::probeDoorAhead)
                .then(argument("direction", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(KungCommandActions.directionNames(), builder))
                    .executes(KungCommandActions::probeDoorDirection))))
            .then(literal("split")
                .then(literal("mark").then(argument("name", StringArgumentType.greedyString())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(DungeonSplitTracker.defaultSplitNames(), builder))
                    .executes(context -> KungCommandActions.markSplit(context, tracker))))
                .then(literal("reset").executes(context -> KungCommandActions.resetSplits(context, tracker))));
    }
}
