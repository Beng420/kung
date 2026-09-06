package com.github.beng420.kung.command;

import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.config.KungHudEditorScreen;
import com.github.beng420.kung.config.KungConfigScreen;
import com.github.beng420.kung.feature.dungeon.CatacombsCalculatorScreen;
import com.github.beng420.kung.feature.dungeon.DungeonDoorKind;
import com.github.beng420.kung.feature.dungeon.DungeonKnownRoomCatalog;
import com.github.beng420.kung.feature.dungeon.DungeonMapOverlayConfig;
import com.github.beng420.kung.feature.dungeon.DungeonRoomDataSyncClient;
import com.github.beng420.kung.feature.dungeon.DungeonRoomClassifier;
import com.github.beng420.kung.feature.dungeon.DungeonScanUtils;
import com.github.beng420.kung.feature.dungeon.DungeonSplitTracker;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.util.KungChat;
import com.github.beng420.kung.util.KungDebugRecorder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class KungCommands {
    private KungCommands() {
    }

    public static void register(DungeonStateTracker dungeonStateTracker) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("kung")
                .executes(KungCommands::openSettings)
                .then(literal("room")
                    .then(literal("roomdata")
                        .executes(context -> copyRoomData(context, dungeonStateTracker))
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(context -> exportRoomInput(context, dungeonStateTracker))))
                    .then(literal("type")
                        .then(argument("type", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(roomTypeNames(), builder))
                            .executes(context -> learnRoomType(context, dungeonStateTracker))))
                    .then(literal("debug")
                        .executes(context -> debugRooms(context, dungeonStateTracker)))
                    .then(literal("mapdebug")
                        .executes(context -> debugMapDecorations(context, dungeonStateTracker)))
                    .then(literal("undo")
                        .executes(KungCommands::undoLastRoomLearn))
                    .then(literal("delete")
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(KungCommands::deleteRoomInput)))
                    .then(literal("export")
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(context -> exportRoomInput(context, dungeonStateTracker))))
                    .then(literal("look")
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(context -> learnLookedRoomInput(context, dungeonStateTracker))))
                    .then(literal("learnmulti")
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(context -> learnMultiRoomInput(context, dungeonStateTracker))))
                    .then(literal("learn")
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(context -> learnRoomInput(context, dungeonStateTracker)))))
                .then(literal("roomdata")
                    .executes(context -> copyRoomData(context, dungeonStateTracker))
                    .then(argument("input", StringArgumentType.greedyString())
                        .suggests(KungCommands::suggestRoomNames)
                        .executes(context -> exportRoomInput(context, dungeonStateTracker))))
                .then(literal("roomsync")
                    .then(literal("status")
                        .executes(KungCommands::roomSyncStatus))
                    .then(literal("ping")
                        .executes(KungCommands::pingRoomSync))
                    .then(literal("enable")
                        .executes(context -> setRoomSyncEnabled(context, true)))
                    .then(literal("disable")
                        .executes(context -> setRoomSyncEnabled(context, false)))
                    .then(literal("upload")
                        .then(argument("enabled", BoolArgumentType.bool())
                            .executes(KungCommands::setRoomSyncUpload)))
                    .then(literal("server")
                        .then(argument("url", StringArgumentType.greedyString())
                            .executes(KungCommands::setRoomSyncServer)))
                    .then(literal("token")
                        .then(literal("clear")
                            .executes(KungCommands::clearRoomSyncToken))
                        .then(argument("token", StringArgumentType.greedyString())
                            .executes(KungCommands::setRoomSyncToken)))
                    .then(literal("pull")
                        .executes(KungCommands::pullRoomSync))
                    .then(literal("push")
                        .then(argument("input", StringArgumentType.greedyString())
                            .suggests(KungCommands::suggestRoomNames)
                            .executes(context -> pushRoomSyncInput(context, dungeonStateTracker)))))
                .then(literal("dungeon")
                    .then(literal("debug")
                        .executes(context -> copyUltraDebug(context, dungeonStateTracker))))
                .then(literal("instance")
                    .executes(KungCommands::debugInstanceContext))
                .then(literal("checkserverconnection")
                    .executes(KungCommands::pingRoomSync))
                .then(literal("calculatecata")
                    .executes(context -> openCatacombsCalculator(context, ""))
                    .then(argument("username", StringArgumentType.word())
                        .executes(context -> openCatacombsCalculator(
                            context,
                            StringArgumentType.getString(context, "username")
                        ))))
                .then(literal("log")
                    .executes(context -> copyDebugLog(context, 5000))
                    .then(literal("copy")
                        .executes(context -> copyDebugLog(context, 5000)))
                    .then(literal("tail")
                        .executes(context -> copyDebugLog(context, 200))
                        .then(argument("lines", IntegerArgumentType.integer(1, 5000))
                            .executes(context -> copyDebugLog(
                                context,
                                IntegerArgumentType.getInteger(context, "lines")
                            ))))
                    .then(literal("save")
                        .executes(KungCommands::saveDebugLog))
                    .then(literal("clear")
                        .executes(KungCommands::clearDebugLog)))
                .then(literal("test")
                    .then(literal("title")
                        .executes(context -> testDoorTitle(context, dungeonStateTracker, 4))
                        .then(argument("doors", IntegerArgumentType.integer(0, 20))
                            .executes(context -> testDoorTitle(context, dungeonStateTracker))))
                    .then(literal("dungeonstart")
                        .executes(context -> testDungeonStartTitle(context, dungeonStateTracker, 4))
                        .then(argument("doors", IntegerArgumentType.integer(0, 20))
                            .executes(context -> testDungeonStartTitle(context, dungeonStateTracker)))))
                .then(literal("crypts")
                    .then(argument("count", IntegerArgumentType.integer(0, 99))
                        .executes(context -> updateCurrentRoomCrypts(context, dungeonStateTracker))))
                .then(literal("door")
                    .then(literal("probe")
                        .executes(KungCommands::probeDoorAhead)
                        .then(argument("direction", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(directionNames(), builder))
                            .executes(KungCommands::probeDoorDirection))))
                .then(literal("split")
                    .then(literal("mark")
                        .then(argument("name", StringArgumentType.greedyString())
                            .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(DungeonSplitTracker.defaultSplitNames(), builder))
                            .executes(context -> markSplit(context, dungeonStateTracker))))
                    .then(literal("reset")
                        .executes(context -> resetSplits(context, dungeonStateTracker))))
                .then(literal("settings")
                    .executes(KungCommands::openSettings))
                .then(literal("hud")
                    .executes(context -> openHudEditor(context, dungeonStateTracker)))
            )
        );
    }

    private static int learnRoomType(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        RoomType roomType = parseRoomType(StringArgumentType.getString(context, "type"));
        if (roomType == null) {
            context.getSource().sendFeedback(Component.literal(
                "Unbekannter RoomType. Nutze: " + String.join(", ", roomTypeNames())
            ));
            return 0;
        }

        DungeonStateTracker.LearnRoomTypeResult result =
            dungeonStateTracker.learnCurrentRoomType(context.getSource().getClient(), roomType);

        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.learned() ? 1 : 0;
    }

    private static int copyRoomData(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        Minecraft client = context.getSource().getClient();
        DungeonStateTracker.RoomDataResult result = dungeonStateTracker.currentRoomData(client);
        if (result.found()) {
            client.keyboardHandler.setClipboard(result.clipboardText());
        }
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.found() ? 1 : 0;
    }

    private static int learnLookedRoomInput(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        RoomLearnInput input = parseRoomLearnInput(context);
        if (input == null) {
            return 0;
        }

        Minecraft client = context.getSource().getClient();
        BlockPos targetPosition = lookedRoomPosition(context, client);
        if (targetPosition == null) {
            return 0;
        }

        if (!input.hasExplicitMetadata()) {
            input = resolveRoomLearnInput(context, client, targetPosition, input);
            if (input == null) {
                return 0;
            }
        }

        DungeonStateTracker.LearnRoomResult result = dungeonStateTracker.learnRoomAt(
            client,
            targetPosition,
            input.name(),
            input.type(),
            input.secrets(),
            input.crypts()
        );
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.learned() ? 1 : 0;
    }

    private static int learnRoomInput(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        RoomLearnInput input = parseRoomLearnInput(context);
        if (input == null) {
            return 0;
        }

        Minecraft client = context.getSource().getClient();
        if (!input.hasExplicitMetadata()) {
            input = resolveRoomLearnInput(
                context,
                client,
                client.player == null ? null : client.player.blockPosition(),
                input
            );
            if (input == null) {
                return 0;
            }
        }

        DungeonStateTracker.LearnRoomResult result = dungeonStateTracker.learnCurrentRoom(
            client,
            input.name(),
            input.type(),
            input.secrets(),
            input.crypts()
        );
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.learned() ? 1 : 0;
    }

    private static int deleteRoomInput(CommandContext<FabricClientCommandSource> context) {
        RoomLearnInput input = parseRoomLearnInput(context);
        if (input == null) {
            return 0;
        }

        try {
            DungeonKnownRoomCatalog.DeleteRoomResult result = input.hasExplicitMetadata()
                ? DungeonKnownRoomCatalog.deleteRoom(input.name(), input.type(), input.secrets())
                : DungeonKnownRoomCatalog.deleteRoom(input.name());
            context.getSource().sendFeedback(Component.literal(result.message()));
            return result.deleted() ? 1 : 0;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            context.getSource().sendFeedback(Component.literal(
                "Konnte Raumdaten nicht loeschen. Siehe latest.log."
            ));
            return 0;
        }
    }

    private static int learnMultiRoomInput(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        RoomLearnInput input = parseRoomLearnInput(context);
        if (input == null) {
            return 0;
        }

        Minecraft client = context.getSource().getClient();
        if (!input.hasExplicitMetadata()) {
            input = resolveRoomLearnInput(
                context,
                client,
                client.player == null ? null : client.player.blockPosition(),
                input
            );
            if (input == null) {
                return 0;
            }
        }

        DungeonStateTracker.LearnRoomResult result = dungeonStateTracker.learnCurrentMultiRoom(
            client,
            input.name(),
            input.type(),
            input.secrets(),
            input.crypts()
        );
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.learned() ? 1 : 0;
    }

    private static int exportRoomInput(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        RoomLearnInput input = parseRoomLearnInput(context);
        if (input == null) {
            return 0;
        }

        Minecraft client = context.getSource().getClient();
        if (!input.hasExplicitMetadata()) {
            DungeonKnownRoomCatalog.KnownRoomInfo info = client.player == null
                ? knownRoomInfo(context, input.name())
                : knownRoomInfo(context, client, client.player.blockPosition(), input.name());
            if (info == null) {
                return 0;
            }
            input = new RoomLearnInput(info.name(), info.secrets(), info.type(), info.crypts(), true);
        }

        DungeonStateTracker.RoomDataResult result = dungeonStateTracker.exportCurrentRoom(
            client,
            input.name(),
            input.type(),
            input.secrets(),
            input.crypts()
        );
        if (result.found()) {
            client.keyboardHandler.setClipboard(result.clipboardText());
        }
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.found() ? 1 : 0;
    }

    private static int pushRoomSyncInput(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        RoomLearnInput input = parseRoomLearnInput(context);
        if (input == null) {
            return 0;
        }

        Minecraft client = context.getSource().getClient();
        if (!input.hasExplicitMetadata()) {
            DungeonKnownRoomCatalog.KnownRoomInfo info = client.player == null
                ? knownRoomInfo(context, input.name())
                : knownRoomInfo(context, client, client.player.blockPosition(), input.name());
            if (info == null) {
                return 0;
            }
            input = new RoomLearnInput(info.name(), info.secrets(), info.type(), info.crypts(), true);
        }

        DungeonStateTracker.RoomDataResult result = dungeonStateTracker.exportCurrentRoom(
            client,
            input.name(),
            input.type(),
            input.secrets(),
            input.crypts()
        );
        if (!result.found()) {
            context.getSource().sendFeedback(Component.literal(result.message()));
            return 0;
        }

        DungeonRoomDataSyncClient.INSTANCE.pushRoomReportAsync(client, result.clipboardText());
        context.getSource().sendFeedback(Component.literal("Room Sync push started: " + input.name()));
        return 1;
    }

    private static int pullRoomSync(CommandContext<FabricClientCommandSource> context) {
        DungeonRoomDataSyncClient.INSTANCE.pullAsync(context.getSource().getClient());
        return 1;
    }

    private static int pingRoomSync(CommandContext<FabricClientCommandSource> context) {
        DungeonRoomDataSyncClient.INSTANCE.pingAsync(context.getSource().getClient());
        return 1;
    }

    private static int roomSyncStatus(CommandContext<FabricClientCommandSource> context) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        String url = config.roomSyncServerUrl().isBlank() ? "none" : config.roomSyncServerUrl();
        context.getSource().sendFeedback(Component.literal(
            "Room Sync: enabled=" + config.roomSyncEnabled()
                + " upload=" + config.roomSyncUploadEnabled()
                + " server=" + url
                + " token=" + (!config.roomSyncToken().isBlank())
                + " status=" + DungeonRoomDataSyncClient.INSTANCE.statusMessage()
        ));
        return 1;
    }

    private static int setRoomSyncEnabled(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        DungeonMapOverlayConfig.INSTANCE.setRoomSyncEnabled(enabled);
        DungeonKnownRoomCatalog.reload();
        context.getSource().sendFeedback(Component.literal(
            "Room Sync " + (enabled ? "enabled for this session." : "disabled.")
        ));
        return 1;
    }

    private static int setRoomSyncUpload(CommandContext<FabricClientCommandSource> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        DungeonMapOverlayConfig.INSTANCE.setRoomSyncUploadEnabled(enabled);
        context.getSource().sendFeedback(Component.literal(
            "Room Sync upload " + (enabled ? "enabled for this session." : "disabled.")
        ));
        return 1;
    }

    private static int setRoomSyncServer(CommandContext<FabricClientCommandSource> context) {
        String url = StringArgumentType.getString(context, "url").trim();
        if (url.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Room Sync URL cannot be empty."));
            return 0;
        }
        DungeonMapOverlayConfig.INSTANCE.setRoomSyncServerUrl(url);
        context.getSource().sendFeedback(Component.literal("Room Sync server saved."));
        return 1;
    }

    private static int setRoomSyncToken(CommandContext<FabricClientCommandSource> context) {
        String token = StringArgumentType.getString(context, "token").trim();
        if (token.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Room Sync token cannot be empty."));
            return 0;
        }
        DungeonMapOverlayConfig.INSTANCE.setRoomSyncToken(token);
        context.getSource().sendFeedback(Component.literal("Room Sync token saved."));
        return 1;
    }

    private static int clearRoomSyncToken(CommandContext<FabricClientCommandSource> context) {
        DungeonMapOverlayConfig.INSTANCE.setRoomSyncToken("");
        context.getSource().sendFeedback(Component.literal("Room Sync token cleared."));
        return 1;
    }

    private static RoomLearnInput parseRoomLearnInput(CommandContext<FabricClientCommandSource> context) {
        String input = StringArgumentType.getString(context, "input").trim();
        if (input.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Der Raumname darf nicht leer sein."));
            return null;
        }

        String[] parts = input.split("\\s+");
        if (parts.length >= 4) {
            Integer crypts = parseInteger(parts[parts.length - 1]);
            RoomType roomType = parseRoomType(parts[parts.length - 2]);
            Integer secrets = parseInteger(parts[parts.length - 3]);
            if (crypts != null && roomType != null && secrets != null) {
                return explicitRoomInput(context, parts, parts.length - 3, secrets, roomType, crypts);
            }
        }
        if (parts.length >= 3) {
            RoomType roomType = parseRoomType(parts[parts.length - 1]);
            Integer secrets = parseInteger(parts[parts.length - 2]);
            if (roomType != null && secrets != null) {
                return explicitRoomInput(context, parts, parts.length - 2, secrets, roomType, 0);
            }
        }
        if (parts.length >= 2) {
            Integer secrets = parseInteger(parts[parts.length - 1]);
            if (secrets != null) {
                return explicitRoomInput(context, parts, parts.length - 1, secrets, RoomType.NORMAL, 0);
            }
        }

        String name = normalizeRoomNameInput(input);
        if (name.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Der Raumname darf nicht leer sein."));
            return null;
        }
        return new RoomLearnInput(name, 0, RoomType.NORMAL, 0, false);
    }

    private static String normalizeRoomNameInput(String value) {
        String name = value == null ? "" : value.trim();
        if (name.length() >= 2
            && ((name.startsWith("\"") && name.endsWith("\""))
                || (name.startsWith("'") && name.endsWith("'")))) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name.replace("\\\"", "\"").replace("\\'", "'");
    }

    private static RoomLearnInput explicitRoomInput(
        CommandContext<FabricClientCommandSource> context,
        String[] parts,
        int namePartCount,
        int secrets,
        RoomType roomType,
        int crypts
    ) {
        if (namePartCount <= 0) {
            context.getSource().sendFeedback(Component.literal("Der Raumname darf nicht leer sein."));
            return null;
        }
        String name = normalizeRoomNameInput(String.join(" ", java.util.Arrays.copyOf(parts, namePartCount)));
        if (name.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Der Raumname darf nicht leer sein."));
            return null;
        }
        return new RoomLearnInput(
            name,
            secrets,
            roomType,
            crypts,
            true
        );
    }

    private static BlockPos lookedRoomPosition(
        CommandContext<FabricClientCommandSource> context,
        Minecraft client
    ) {
        if (client.level == null || client.player == null) {
            context.getSource().sendFeedback(Component.literal("Du bist gerade nicht in einer Welt."));
            return null;
        }

        DirectionStep direction = directionFromYaw(client.player.getYRot());
        DungeonScanUtils.GridPosition playerRoom =
            DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        int targetRoomGridX = playerRoom.gridX() + direction.dx();
        int targetRoomGridZ = playerRoom.gridZ() + direction.dz();
        if (!isValidRoomGrid(targetRoomGridX, targetRoomGridZ)) {
            context.getSource().sendFeedback(Component.literal(
                "In Blickrichtung liegt keine gueltige Dungeon-Raumzelle. roomGrid="
                    + playerRoom.gridX() + "," + playerRoom.gridZ()
                    + " dir=" + direction.name()
            ));
            return null;
        }

        int scanGridX = targetRoomGridX * 2;
        int scanGridZ = targetRoomGridZ * 2;
        return new BlockPos(
            DungeonScanUtils.worldXForScanGrid(scanGridX),
            client.player.blockPosition().getY(),
            DungeonScanUtils.worldZForScanGrid(scanGridZ)
        );
    }

    private static int updateCurrentRoomCrypts(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        DungeonStateTracker.LearnRoomResult result = dungeonStateTracker.updateCurrentRoomCrypts(
            context.getSource().getClient(),
            IntegerArgumentType.getInteger(context, "count")
        );
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.learned() ? 1 : 0;
    }

    private static DungeonKnownRoomCatalog.KnownRoomInfo knownRoomInfo(
        CommandContext<FabricClientCommandSource> context,
        String name
    ) {
        if (name.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Der Raumname darf nicht leer sein."));
            return null;
        }

        List<DungeonKnownRoomCatalog.KnownRoomInfo> infos = DungeonKnownRoomCatalog.knownRoomInfos(name);
        if (infos.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + name + "\" ist noch nicht bekannt. Nutze secrets/type zum ersten Speichern."
            ));
            return null;
        }
        if (infos.size() > 1) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + name + "\" ist mehrdeutig. Nutze bitte secrets/type."
            ));
            return null;
        }
        return infos.get(0);
    }

    private static RoomLearnInput resolveRoomLearnInput(
        CommandContext<FabricClientCommandSource> context,
        Minecraft client,
        BlockPos roomPosition,
        RoomLearnInput input
    ) {
        List<DungeonKnownRoomCatalog.KnownRoomInfo> infos = DungeonKnownRoomCatalog.knownRoomInfos(input.name());
        if (infos.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + input.name() + "\" ist noch nicht bekannt. Speichere als NORMAL mit 0 Secrets."
            ));
            return new RoomLearnInput(input.name(), 0, RoomType.NORMAL, 0, true);
        }
        if (infos.size() == 1) {
            DungeonKnownRoomCatalog.KnownRoomInfo info = infos.get(0);
            return new RoomLearnInput(info.name(), info.secrets(), info.type(), info.crypts(), true);
        }
        if (client == null || roomPosition == null) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + input.name() + "\" ist mehrdeutig. Nutze bitte secrets/type."
            ));
            return null;
        }

        RoomCore roomCore = roomCoreAt(client, roomPosition);
        if (!roomCore.valid()) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + input.name() + "\" ist mehrdeutig und der Zielraum konnte nicht gehasht werden: "
                    + roomCore.message() + " Nutze secrets/type."
            ));
            return null;
        }

        List<DungeonKnownRoomCatalog.KnownRoomInfo> matchingInfos =
            DungeonKnownRoomCatalog.knownRoomInfos(input.name(), roomCore.coreHash(), roomCore.stableCoreHash());
        if (matchingInfos.size() == 1) {
            DungeonKnownRoomCatalog.KnownRoomInfo info = matchingInfos.get(0);
            return new RoomLearnInput(info.name(), info.secrets(), info.type(), info.crypts(), true);
        }

        context.getSource().sendFeedback(Component.literal(
            "Raum \"" + input.name() + "\" ist mehrdeutig. Nutze bitte secrets/type."
        ));
        return null;
    }

    private static DungeonKnownRoomCatalog.KnownRoomInfo knownRoomInfo(
        CommandContext<FabricClientCommandSource> context,
        Minecraft client,
        BlockPos roomPosition,
        String name
    ) {
        if (name.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Der Raumname darf nicht leer sein."));
            return null;
        }

        List<DungeonKnownRoomCatalog.KnownRoomInfo> infos = DungeonKnownRoomCatalog.knownRoomInfos(name);
        if (infos.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + name + "\" ist noch nicht bekannt. Nutze secrets/type zum ersten Speichern."
            ));
            return null;
        }
        if (infos.size() == 1) {
            return infos.getFirst();
        }

        RoomCore roomCore = roomCoreAt(client, roomPosition);
        if (!roomCore.valid()) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + name + "\" ist mehrdeutig und der Zielraum konnte nicht gehasht werden: "
                    + roomCore.message() + " Nutze secrets/type."
            ));
            return null;
        }

        List<DungeonKnownRoomCatalog.KnownRoomInfo> matchingInfos =
            DungeonKnownRoomCatalog.knownRoomInfos(name, roomCore.coreHash(), roomCore.stableCoreHash());
        if (matchingInfos.size() == 1) {
            return matchingInfos.getFirst();
        }
        if (matchingInfos.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                "Raum \"" + name + "\" ist mehrdeutig. Ziel-hash="
                    + roomCore.coreHash() + "/" + roomCore.stableCoreHash()
                    + " passt zu keiner bekannten Variante. Nutze secrets/type."
            ));
            return null;
        }

        context.getSource().sendFeedback(Component.literal(
            "Raum \"" + name + "\" ist trotz Ziel-hash="
                + roomCore.coreHash() + "/" + roomCore.stableCoreHash()
                + " mehrdeutig. Nutze secrets/type."
        ));
        return null;
    }

    private static RoomCore roomCoreAt(Minecraft client, BlockPos position) {
        if (client.level == null || client.player == null) {
            return RoomCore.failed("Du bist gerade nicht in einer Welt.");
        }

        DungeonScanUtils.GridPosition roomGrid = DungeonScanUtils.getRoomGridPosition(position);
        int scanGridX = roomGrid.gridX() * 2;
        int scanGridZ = roomGrid.gridZ() * 2;
        if (scanGridX < 0
            || scanGridZ < 0
            || scanGridX >= DungeonScanUtils.SCAN_GRID_SIZE
            || scanGridZ >= DungeonScanUtils.SCAN_GRID_SIZE) {
            return RoomCore.failed("Ziel liegt nicht im bekannten Dungeon-Raster.");
        }

        int worldX = DungeonScanUtils.worldXForScanGrid(scanGridX);
        int worldZ = DungeonScanUtils.worldZForScanGrid(scanGridZ);
        if (!DungeonScanUtils.isChunkLoaded(client.level, worldX, worldZ)) {
            return RoomCore.failed("Die Mitte dieses Raums ist noch nicht geladen.");
        }

        int coreHash = DungeonScanUtils.getCoreHash(client.level, worldX, worldZ);
        if (DungeonRoomClassifier.isEmptyCore(coreHash)) {
            return RoomCore.failed("Der Zielraum sieht fuer den Scanner leer aus.");
        }

        return RoomCore.valid(coreHash, DungeonScanUtils.getStableCoreHash(client.level, worldX, worldZ));
    }

    private static int undoLastRoomLearn(CommandContext<FabricClientCommandSource> context) {
        try {
            com.github.beng420.kung.feature.dungeon.DungeonKnownRoomCatalog.UndoResult result =
                com.github.beng420.kung.feature.dungeon.DungeonKnownRoomCatalog.undoLast();
            context.getSource().sendFeedback(Component.literal(result.message()));
            return result.undone() ? 1 : 0;
        } catch (java.io.IOException exception) {
            context.getSource().sendFeedback(Component.literal(
                "Konnte den letzten Room-Eintrag nicht rueckgaengig machen. Siehe latest.log."
            ));
            return 0;
        }
    }

    private static int debugRooms(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        for (String line : dungeonStateTracker.debugRoomSummary()) {
            context.getSource().sendFeedback(KungChat.message("RoomDebug", line));
        }
        return 1;
    }

    private static int debugMapDecorations(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        Minecraft client = context.getSource().getClient();
        List<String> lines = dungeonStateTracker.debugMapDecorations(client);
        client.keyboardHandler.setClipboard(String.join("\n", lines));
        for (String line : lines) {
            context.getSource().sendFeedback(KungChat.message("MapDebug", line));
        }
        context.getSource().sendFeedback(KungChat.message("MapDebug", "In die Zwischenablage kopiert."));
        return 1;
    }

    private static int copyUltraDebug(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        Minecraft client = context.getSource().getClient();
        DungeonStateTracker.RoomDataResult result = dungeonStateTracker.ultraDebug(client);
        client.keyboardHandler.setClipboard(result.clipboardText());
        context.getSource().sendFeedback(KungChat.message("DungeonDebug", result.message()));
        return result.found() ? 1 : 0;
    }

    private static int probeDoorAhead(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = context.getSource().getClient();
        if (client.player == null) {
            context.getSource().sendFeedback(Component.literal("Du bist gerade nicht in einer Welt."));
            return 0;
        }
        return probeDoor(context, directionFromYaw(client.player.getYRot()));
    }

    private static int probeDoorDirection(CommandContext<FabricClientCommandSource> context) {
        DirectionStep direction = parseDirection(StringArgumentType.getString(context, "direction"));
        if (direction == null) {
            context.getSource().sendFeedback(Component.literal(
                "Unbekannte Richtung. Nutze: " + String.join(", ", directionNames())
            ));
            return 0;
        }
        return probeDoor(context, direction);
    }

    private static int probeDoor(CommandContext<FabricClientCommandSource> context, DirectionStep direction) {
        Minecraft client = context.getSource().getClient();
        if (client.level == null || client.player == null) {
            context.getSource().sendFeedback(Component.literal("Du bist gerade nicht in einer Welt."));
            return 0;
        }
        DungeonScanUtils.GridPosition roomGrid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        int doorGridX = roomGrid.gridX() * 2 + direction.dx();
        int doorGridZ = roomGrid.gridZ() * 2 + direction.dz();
        if (!DungeonScanUtils.isDoorScanPoint(doorGridX, doorGridZ)) {
            context.getSource().sendFeedback(Component.literal(
                "Keine gueltige Door-Zelle vor dir. roomGrid="
                    + roomGrid.gridX() + "," + roomGrid.gridZ()
                    + " dir=" + direction.name()
            ));
            return 0;
        }

        int worldX = DungeonScanUtils.worldXForScanGrid(doorGridX);
        int worldZ = DungeonScanUtils.worldZForScanGrid(doorGridZ);
        DungeonDoorKind doorKind = DungeonScanUtils.detectDoorKind(
            client.level,
            doorGridX,
            doorGridZ,
            worldX,
            worldZ
        );
        DoorProbeSamples samples = sampleDoor(client, doorGridX, doorGridZ, worldX, worldZ);
        context.getSource().sendFeedback(Component.literal(
            "doorProbe dir=" + direction.name()
                + " roomGrid=" + roomGrid.gridX() + "," + roomGrid.gridZ()
                + " doorGrid=" + doorGridX + "," + doorGridZ
                + " world=" + worldX + "," + worldZ
                + " kind=" + doorKind.name()
                + " centerPattern=" + samples.centerPattern()
                + " centerY66-74=" + samples.centerBlocks()
        ));
        return 1;
    }

    private static int openSettings(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = context.getSource().getClient();
        client.execute(() -> client.setScreen(new KungConfigScreen()));
        return 1;
    }

    private static int debugInstanceContext(CommandContext<FabricClientCommandSource> context) {
        HypixelInstanceTracker tracker = HypixelInstanceTracker.INSTANCE;
        context.getSource().sendFeedback(Component.literal(
            "Tracking="
                + tracker.tracking()
                + " dungeonHub="
                + tracker.dungeonHub()
                + " catacombs="
                + tracker.catacombs()
                + " dungeonContext="
                + tracker.dungeonRunContext()
                + " server="
                + blankAsUnknown(tracker.serverId())
                + " instance="
                + blankAsUnknown(tracker.instanceLine())
        ));
        return 1;
    }

    private static int openCatacombsCalculator(
        CommandContext<FabricClientCommandSource> context,
        String username
    ) {
        Minecraft client = context.getSource().getClient();
        client.execute(() -> client.setScreen(new CatacombsCalculatorScreen(username)));
        return 1;
    }

    private static int copyDebugLog(CommandContext<FabricClientCommandSource> context, int maxLines) {
        Minecraft client = context.getSource().getClient();
        String dump = KungDebugRecorder.dump(maxLines);
        client.keyboardHandler.setClipboard(dump);
        context.getSource().sendFeedback(KungChat.message(
            "Log",
            "Trace in die Zwischenablage kopiert (" + maxLines + " Zeilen max)."
        ));
        return 1;
    }

    private static int saveDebugLog(CommandContext<FabricClientCommandSource> context) {
        try {
            Path path = KungDebugRecorder.saveToFile(context.getSource().getClient()).toAbsolutePath();
            context.getSource().sendFeedback(KungChat.message("Log", "Trace gespeichert: " + path));
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFeedback(KungChat.message(
                "Log",
                "Trace konnte nicht gespeichert werden: " + exception.getClass().getSimpleName()
            ));
            return 0;
        }
    }

    private static int clearDebugLog(CommandContext<FabricClientCommandSource> context) {
        KungDebugRecorder.clear();
        context.getSource().sendFeedback(KungChat.message("Log", "Trace geleert."));
        return 1;
    }

    private static String blankAsUnknown(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    private static int testDoorTitle(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        return testDoorTitle(context, dungeonStateTracker, IntegerArgumentType.getInteger(context, "doors"));
    }

    private static int testDoorTitle(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker,
        int doors
    ) {
        Minecraft client = context.getSource().getClient();
        client.execute(() -> dungeonStateTracker.debugShowDoorTitle(client, doors));
        context.getSource().sendFeedback(Component.literal("Kung test title shown: " + doors + " doors"));
        return 1;
    }

    private static int testDungeonStartTitle(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        return testDungeonStartTitle(context, dungeonStateTracker, IntegerArgumentType.getInteger(context, "doors"));
    }

    private static int testDungeonStartTitle(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker,
        int doors
    ) {
        dungeonStateTracker.debugScheduleDoorTitle(doors);
        context.getSource().sendFeedback(Component.literal(
            "Kung dungeon-start title scheduled: " + doors + " doors"
        ));
        return 1;
    }

    private static int openHudEditor(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        Minecraft client = context.getSource().getClient();
        client.execute(() -> client.setScreen(new KungHudEditorScreen(dungeonStateTracker)));
        return 1;
    }

    private static int markSplit(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        String name = StringArgumentType.getString(context, "name").trim();
        if (name.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("Der Split-Name darf nicht leer sein."));
            return 0;
        }

        dungeonStateTracker.splitTracker().mark(name, dungeonStateTracker.dungeonTick());
        context.getSource().sendFeedback(Component.literal("Split markiert. Naechster Abschnitt: " + name));
        return 1;
    }

    private static int resetSplits(
        CommandContext<FabricClientCommandSource> context,
        DungeonStateTracker dungeonStateTracker
    ) {
        dungeonStateTracker.splitTracker().reset();
        context.getSource().sendFeedback(Component.literal("Splits zurueckgesetzt."));
        return 1;
    }

    private static RoomType parseRoomType(String value) {
        try {
            return RoomType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String[] roomTypeNames() {
        RoomType[] values = RoomType.values();
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].name().toLowerCase(Locale.ROOT);
        }
        return names;
    }

    private static CompletableFuture<Suggestions> suggestRoomNames(
        CommandContext<FabricClientCommandSource> context,
        SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining();
        boolean quoted = remaining.startsWith("\"");
        String prefix = quoted ? remaining.substring(1) : remaining;
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String name : DungeonKnownRoomCatalog.knownRoomNames()) {
            if (!name.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                continue;
            }
            builder.suggest(quoted ? quoteStringArgument(name) : name);
        }
        return builder.buildFuture();
    }

    private static String quoteStringArgument(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        escaped.append('"');
        return escaped.toString();
    }

    private static DirectionStep directionFromYaw(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90.0f), 4)) {
            case 1 -> new DirectionStep("west", -1, 0);
            case 2 -> new DirectionStep("north", 0, -1);
            case 3 -> new DirectionStep("east", 1, 0);
            default -> new DirectionStep("south", 0, 1);
        };
    }

    private static DirectionStep parseDirection(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "north", "n" -> new DirectionStep("north", 0, -1);
            case "south", "s" -> new DirectionStep("south", 0, 1);
            case "east", "e" -> new DirectionStep("east", 1, 0);
            case "west", "w" -> new DirectionStep("west", -1, 0);
            default -> null;
        };
    }

    private static String[] directionNames() {
        return new String[] {"north", "south", "east", "west"};
    }

    private static boolean isValidRoomGrid(int roomGridX, int roomGridZ) {
        return roomGridX >= 0
            && roomGridZ >= 0
            && roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    private static DoorProbeSamples sampleDoor(Minecraft client, int gridX, int gridZ, int worldX, int worldZ) {
        StringBuilder centerBlocks = new StringBuilder();
        StringBuilder centerPattern = new StringBuilder();

        for (int y = 66; y <= 74; y++) {
            if (y > 66) {
                centerBlocks.append('/');
            }
            BlockState state = client.level.getBlockState(new BlockPos(worldX, y, worldZ));
            centerBlocks.append(y)
                .append(':')
                .append(Block.getId(state))
                .append(':')
                .append(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        }

        appendCenterPattern(client, centerPattern, worldX, 68, worldZ);
        for (int y = 69; y <= 72; y++) {
            centerPattern.append('/');
            appendCenterPattern(client, centerPattern, worldX, y, worldZ);
        }
        centerPattern.append('/');
        appendCenterPattern(client, centerPattern, worldX, 73, worldZ);
        centerPattern.append('/');
        appendCenterPattern(client, centerPattern, worldX, 74, worldZ);

        return new DoorProbeSamples(centerPattern.toString(), centerBlocks.toString());
    }

    private static void appendCenterPattern(
        Minecraft client,
        StringBuilder builder,
        int worldX,
        int y,
        int worldZ
    ) {
        BlockPos pos = new BlockPos(worldX, y, worldZ);
        BlockState state = client.level.getBlockState(pos);
        if (isClear(client, pos)) {
            builder.append(y).append(":clear");
            return;
        }

        String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        builder.append(y)
            .append(':')
            .append(Block.getId(state))
            .append(':')
            .append(blockName);
    }

    private static boolean isClear(Minecraft client, BlockPos pos) {
        return client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty();
    }

    private record DirectionStep(String name, int dx, int dz) {
    }

    private record RoomLearnInput(
        String name,
        int secrets,
        RoomType type,
        int crypts,
        boolean hasExplicitMetadata
    ) {
    }

    private record RoomCore(boolean valid, String message, int coreHash, int stableCoreHash) {
        static RoomCore valid(int coreHash, int stableCoreHash) {
            return new RoomCore(true, "", coreHash, stableCoreHash);
        }

        static RoomCore failed(String message) {
            return new RoomCore(false, message, 0, 0);
        }
    }

    private record DoorProbeSamples(String centerPattern, String centerBlocks) {
    }
}
