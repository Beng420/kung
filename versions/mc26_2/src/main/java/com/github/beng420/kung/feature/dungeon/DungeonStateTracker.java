package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class DungeonStateTracker {
    private static final int MAX_MULTI_LEARN_COMPONENTS = 4;
    private static final long STATS_OBSERVE_INTERVAL_TICKS = 5;
    private static final long CLEAR_STATE_OBSERVE_INTERVAL_TICKS = 5;
    private static final long RUN_SUMMARY_DELAY_TICKS = 20;
    private static final long LIFECYCLE_DUPLICATE_WINDOW_TICKS = 20;
    private static final int DEBUG_ROOM_SIZE = 19;
    private static final int DEBUG_DOOR_SIZE = 6;
    private static final int DEBUG_CELL_GAP = 1;
    private static final int PLAYER_IDENTITY_MATCH_DISTANCE = 12;
    private static final Pattern RUN_START_SIGNAL = Pattern.compile("^Starting in 1 second\\.$");
    private static final Pattern RUN_FINISHED_SIGNAL = Pattern.compile(
        "^\\s*(?:\\S\\s+)?Defeated\\s+(.+?)\\s+in\\s+0?([\\dhms ]+?)\\s*(?:\\(NEW RECORD!\\))?$"
    );

    private final DungeonState state = new DungeonState();
    private final DungeonRunDetector runDetector = new DungeonRunDetector();
    private final DungeonScanRecorder scanRecorder = new DungeonScanRecorder();
    private final DungeonRunStats runStats = new DungeonRunStats();
    private final DungeonSplitTracker splitTracker = new DungeonSplitTracker();
    private boolean lastInDungeon;
    private boolean dungeonInstanceActive;
    private boolean inDungeonArea;
    private boolean mapVisibleArea;
    private long dungeonTick;
    private long lastDungeonAreaSeenTick;
    private int missingRunTicks;
    private Object activeLevel;
    private long cachedRenderPlanRevision = Long.MIN_VALUE;
    private long cachedRenderPlanCatalogRevision = Long.MIN_VALUE;
    private DungeonLiveMapWriter.MatchRenderPlan cachedRenderPlan;
    private long lastStatsObserveTick = Long.MIN_VALUE;
    private long lastClearStateObserveTick = Long.MIN_VALUE;
    private long lastObservedClearStateRevision = Long.MIN_VALUE;
    private long pendingRunSummaryTick = Long.MIN_VALUE;
    private long lastRunStartSignalTick = Long.MIN_VALUE;
    private long lastRunFinishedSignalTick = Long.MIN_VALUE;
    private boolean runSummarySent;

    public DungeonStateTracker() {
    }

    public DungeonState state() {
        return state;
    }

    public DungeonMapSnapshot mapSnapshot() {
        return scanRecorder.mapSnapshot();
    }

    public String runTimestamp() {
        return scanRecorder.runTimestamp();
    }

    public boolean isRecording() {
        return scanRecorder.isRecording();
    }

    public boolean isInDungeonArea() {
        return mapVisibleArea;
    }

    public long dungeonTick() {
        return dungeonTick;
    }

    public boolean wasInDungeonAreaRecently(long ignoredNowMillis) {
        return inDungeonArea || dungeonTick - lastDungeonAreaSeenTick <= 400L;
    }

    public DungeonRunStats runStats() {
        return runStats;
    }

    public DungeonSplitTracker splitTracker() {
        return splitTracker;
    }

    public DungeonLiveMapWriter.MatchRenderPlan renderPlan() {
        DungeonMapSnapshot snapshot = scanRecorder.mapSnapshot();
        long revision = snapshot.revision();
        long catalogRevision = DungeonKnownRoomCatalog.revision();
        if (cachedRenderPlan == null
            || cachedRenderPlanRevision != revision
            || cachedRenderPlanCatalogRevision != catalogRevision) {
            DungeonLiveMapWriter.MatchRenderPlan renderPlan = DungeonLiveMapWriter.MatchRenderPlan.from(snapshot);
            if (autoLearnStableHashes(snapshot, renderPlan)) {
                renderPlan = DungeonLiveMapWriter.MatchRenderPlan.from(snapshot);
                catalogRevision = DungeonKnownRoomCatalog.revision();
            }
            cachedRenderPlan = renderPlan;
            cachedRenderPlanRevision = revision;
            cachedRenderPlanCatalogRevision = catalogRevision;
        }
        return cachedRenderPlan;
    }

    private boolean autoLearnStableHashes(
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        boolean learnedAny = false;
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            try {
                DungeonKnownRoomCatalog.AutoLearnResult result =
                    DungeonKnownRoomCatalog.autoLearnStableHashes(match, snapshot);
                if (result.learned()) {
                    learnedAny = true;
                    KungMod.LOGGER.info(
                        "Auto-learned {} stable dungeon room hashes for {}.",
                        result.componentCount(),
                        result.roomName()
                    );
                }
            } catch (IOException | IllegalArgumentException exception) {
                KungMod.LOGGER.warn("Failed to auto-learn stable dungeon room hashes.", exception);
            }
        }
        return learnedAny;
    }

    public List<String> debugRoomSummary() {
        DungeonMapSnapshot snapshot = scanRecorder.mapSnapshot();
        return DungeonRoomDebugFormatter.summaryLines(runTimestamp(), snapshot, renderPlan());
    }

    public List<String> debugMapDecorations(Minecraft client) {
        MapItemSavedData map = DungeonMapItems.mapData(client);
        if (map == null || client.player == null) {
            return List.of("Keine Dungeon-Map im Inventar/Offhand/ausgewaehlten Slot gefunden.");
        }

        List<String> lines = new ArrayList<>();
        int decorationCount = 0;
        for (MapDecoration ignored : map.getDecorations()) {
            decorationCount++;
        }
        lines.add("MapDecorations count=" + decorationCount);
        for (MapDecoration decoration : map.getDecorations()) {
            String name = decoration.name()
                .map(component -> component.getString().replaceAll("\u00a7.", "").trim())
                .orElse("");
            DungeonMapCheckmarkReader.MapPixel pixel = DungeonMapCheckmarkReader.mapPixelForDecoration(decoration);
            DungeonMapCheckmarkReader.MapPixel overlayPixel =
                DungeonMapCheckmarkReader.overlayPixelForDecoration(decoration, client, scanRecorder.mapSnapshot(), 19, 6, 1);
            DungeonMapCheckmarkReader.MapRoom room =
                DungeonMapCheckmarkReader.roomFromDecoration(decoration, client, scanRecorder.mapSnapshot());
            boolean strictParty = isStrictPartyMapDecoration(client, decoration, name);
            lines.add("type=" + decorationTypeLabel(decoration)
                + " name=" + (name.isEmpty() ? "<empty>" : name)
                + " raw=" + decoration.x() + "," + decoration.y()
                + " pixel=" + pixel.x() + "," + pixel.z()
                + " overlay=" + (overlayPixel == null ? "none" : overlayPixel.x() + "," + overlayPixel.z())
                + " rot=" + decoration.rot()
                + " room=" + (room == null ? "none" : room.roomGridX() + "," + room.roomGridZ())
                + " accepted=" + strictParty);
        }
        return lines;
    }

    public RoomDataResult ultraDebug(Minecraft client) {
        StringBuilder debug = new StringBuilder(8192);
        DungeonMapSnapshot snapshot = scanRecorder.mapSnapshot();
        DungeonLiveMapWriter.MatchRenderPlan plan = renderPlan();

        appendLine(debug, "Kung Dungeon Ultra Debug");
        appendLine(debug, "createdAtMillis=" + System.currentTimeMillis());
        appendLine(debug, "state dungeonInstanceActive=" + dungeonInstanceActive
            + " inDungeonArea=" + inDungeonArea
            + " mapVisibleArea=" + mapVisibleArea
            + " stateInDungeon=" + state.isInDungeon()
            + " recording=" + scanRecorder.isRecording()
            + " dungeonTick=" + dungeonTick);
        appendLine(debug, "config enabled=" + DungeonMapOverlayConfig.INSTANCE.enabled()
            + " playerTracking=" + DungeonMapOverlayConfig.INSTANCE.playerTrackingEnabled()
            + " debugMessages=" + DungeonMapOverlayConfig.INSTANCE.debugMessages()
            + " roomCrypts=" + DungeonMapOverlayConfig.INSTANCE.debugRoomCrypts()
            + " roomDebug=" + DungeonMapOverlayConfig.INSTANCE.debugRoomMatches()
            + " scale=" + DungeonMapOverlayConfig.INSTANCE.scale()
            + " unopenedAlpha=" + DungeonMapOverlayConfig.INSTANCE.unopenedRoomAlpha());

        if (client.player == null || client.level == null) {
            appendLine(debug, "client=no-player-or-world");
            return RoomDataResult.found("Ultra-Debug kopiert: keine Welt/kein Spieler.", debug.toString());
        }

        DungeonScanUtils.GridPosition selfGrid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        appendLine(debug, "self name=" + client.player.getName().getString()
            + " uuid=" + client.player.getUUID()
            + " block=" + client.player.blockPosition().getX()
            + "," + client.player.blockPosition().getY()
            + "," + client.player.blockPosition().getZ()
            + " roomGrid=" + selfGrid.gridX() + "," + selfGrid.gridZ()
            + " yaw=" + client.player.getYRot());

        appendSnapshotDebug(debug, snapshot);
        appendRunStatsDebug(debug, client);
        appendLoadedPlayersDebug(debug, client);
        appendMapDecorationDebug(debug, client, snapshot);
        appendRenderPlanDebug(debug, plan);

        RoomDataResult roomData = currentRoomData(client);
        appendLine(debug, "currentRoomData found=" + roomData.found() + " message=" + roomData.message());
        if (roomData.found()) {
            appendLine(debug, "currentRoomDataClipboard=" + roomData.clipboardText());
        }

        return RoomDataResult.found("Ultra-Debug in die Zwischenablage kopiert.", debug.toString());
    }

    private void appendSnapshotDebug(StringBuilder debug, DungeonMapSnapshot snapshot) {
        appendLine(debug, "");
        appendLine(debug, "[snapshot]");
        appendLine(debug, "runTimestamp=" + runTimestamp()
            + " revision=" + snapshot.revision()
            + " lastScan=" + snapshot.lastScanNumber()
            + " lastTimestamp=" + snapshot.lastTimestamp()
            + " observedRooms=" + snapshot.observedRoomCount()
            + " playerGrid=" + snapshot.playerGridX() + "," + snapshot.playerGridZ()
            + " startRoom=" + gridKeyText(snapshot.startRoom()));
        appendLine(debug, "mapPlayerRooms=" + gridKeysText(snapshot.mapPlayerRooms()));
        appendLine(debug, "traversedDoors=" + gridKeysText(snapshot.traversedDoors()));
        appendLine(debug, "openedLockedDoors=" + gridKeysText(snapshot.openedLockedDoors()));

        StringBuilder roomStates = new StringBuilder();
        for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                DungeonMapSnapshot.ObservedPoint point = snapshot.pointAt(roomGridX * 2, roomGridZ * 2);
                if (point == null
                    && !snapshot.isVisitedRoom(roomGridX, roomGridZ)
                    && !snapshot.isClearedRoom(roomGridX, roomGridZ)
                    && !snapshot.isCompletedRoom(roomGridX, roomGridZ)) {
                    continue;
                }
                if (!roomStates.isEmpty()) {
                    roomStates.append(' ');
                }
                roomStates.append(roomGridX).append(',').append(roomGridZ)
                    .append(":hash=")
                    .append(point == null ? "none" : point.point().coreHash() + "/" + point.point().stableCoreHash())
                    .append(":v=").append(snapshot.isVisitedRoom(roomGridX, roomGridZ))
                    .append(":c=").append(snapshot.isClearedRoom(roomGridX, roomGridZ))
                    .append(":done=").append(snapshot.isCompletedRoom(roomGridX, roomGridZ));
            }
        }
        appendLine(debug, "rooms=" + roomStates);
    }

    private void appendRunStatsDebug(StringBuilder debug, Minecraft client) {
        appendLine(debug, "");
        appendLine(debug, "[runStats]");
        appendLine(debug, "floor=" + runStats.floor()
            + " masterMode=" + runStats.masterMode()
            + " playersKnown=" + runStats.dungeonPlayerCount()
            + " score=" + runStats.score()
            + " secrets=" + runStats.secretsFound()
            + "/" + runStats.secretsAvailable()
            + " totalSecrets=" + runStats.secretsTotalAvailable()
            + " secretsPercent=" + runStats.secretsPercent()
            + " deaths=" + runStats.deaths()
            + " crypts=" + runStats.cryptsOpened()
            + "/" + runStats.cryptsAvailable());
        int index = 0;
        for (DungeonRunStats.PlayerStats stats : runStats.dungeonPlayersInOrder()) {
            appendLine(debug, "orderedPlayer[" + index++ + "] name=" + stats.name()
                + " uuid=" + stats.uuid()
                + " class=" + stats.dungeonClass()
                + " roomGrid=" + stats.roomGridX() + "," + stats.roomGridZ()
                + " lastSeenTick=" + stats.lastSeenTick()
                + " loaded=" + (loadedPlayer(client, stats.uuid()) != null));
        }
        for (Map.Entry<UUID, DungeonRunStats.PlayerStats> entry : runStats.players().entrySet()) {
            DungeonRunStats.PlayerStats stats = entry.getValue();
            appendLine(debug, "statsPlayer name=" + stats.name()
                + " uuid=" + entry.getKey()
                + " class=" + stats.dungeonClass()
                + " knownDungeon=" + runStats.isKnownDungeonPlayer(stats.name())
                + " roomGrid=" + stats.roomGridX() + "," + stats.roomGridZ());
        }
    }

    private void appendLoadedPlayersDebug(StringBuilder debug, Minecraft client) {
        appendLine(debug, "");
        appendLine(debug, "[loadedPlayers]");
        int index = 0;
        for (AbstractClientPlayer player : client.level.players()) {
            DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(player.blockPosition());
            appendLine(debug, "loaded[" + index++ + "] name=" + player.getName().getString()
                + " uuid=" + player.getUUID()
                + " invisible=" + player.isInvisible()
                + " tabInfo=" + (client.getConnection() != null && client.getConnection().getPlayerInfo(player.getUUID()) != null)
                + " knownDungeon=" + runStats.isKnownDungeonPlayer(player.getName().getString())
                + " block=" + player.blockPosition().getX()
                + "," + player.blockPosition().getY()
                + "," + player.blockPosition().getZ()
                + " roomGrid=" + grid.gridX() + "," + grid.gridZ()
                + " overlayFromWorld=" + mapPixelForWorldX(player.getX()) + "," + mapPixelForWorldZ(player.getZ())
                + " class=" + classText(player.getUUID()));
        }
    }

    private void appendMapDecorationDebug(StringBuilder debug, Minecraft client, DungeonMapSnapshot snapshot) {
        appendLine(debug, "");
        appendLine(debug, "[mapDecorations]");
        MapItemSavedData map = DungeonMapItems.mapData(client);
        if (map == null) {
            appendLine(debug, "map=none");
            return;
        }

        int decorationCount = 0;
        for (MapDecoration ignored : map.getDecorations()) {
            decorationCount++;
        }
        appendLine(debug, "count=" + decorationCount);

        int decorationIndex = 0;
        int partyPlayerIndex = 1;
        int drawableCount = 0;
        Set<UUID> resolvedPlayerUuids = new HashSet<>();
        for (MapDecoration decoration : map.getDecorations()) {
            String name = decorationName(decoration);
            boolean selfDecoration = isSelfDecoration(client, decoration, name);

            DungeonMapCheckmarkReader.MapPixel mapPixel = DungeonMapCheckmarkReader.mapPixelForDecoration(decoration);
            DungeonMapCheckmarkReader.MapPixel anchoredOverlay =
                DungeonMapCheckmarkReader.overlayPixelForDecoration(
                    decoration,
                    client,
                    snapshot,
                    DEBUG_ROOM_SIZE,
                    DEBUG_DOOR_SIZE,
                    DEBUG_CELL_GAP
                );
            DungeonMapCheckmarkReader.MapPixel rawOverlay = rawOverlayPixel(decoration);
            DungeonMapCheckmarkReader.MapPixel renderOverlay = anchoredOverlay != null ? anchoredOverlay : rawOverlay;
            UUID resolvedUuid = resolveDecorationUuid(
                client,
                decoration,
                name,
                partyPlayerIndex,
                renderOverlay,
                resolvedPlayerUuids
            );
            if (!selfDecoration) {
                partyPlayerIndex++;
            }
            DungeonMapCheckmarkReader.MapRoom room =
                DungeonMapCheckmarkReader.roomFromDecoration(decoration, client, snapshot);
            AbstractClientPlayer loaded = loadedPlayer(client, resolvedUuid);
            boolean playerDecoration = isPlayerMapDecoration(decoration);
            boolean drawable = playerDecoration && (anchoredOverlay != null || rawOverlay != null || loaded != null);
            if (drawable) {
                drawableCount++;
                if (resolvedUuid != null) {
                    resolvedPlayerUuids.add(resolvedUuid);
                }
            }

            appendLine(debug, "decoration[" + decorationIndex++ + "] type=" + decorationTypeLabel(decoration)
                + " playerDecoration=" + playerDecoration
                + " name=" + (name.isEmpty() ? "<empty>" : name)
                + " self=" + selfDecoration
                + " raw=" + decoration.x() + "," + decoration.y()
                + " mapPixel=" + mapPixel.x() + "," + mapPixel.z()
                + " anchoredOverlay=" + pixelText(anchoredOverlay)
                + " rawOverlay=" + pixelText(rawOverlay)
                + " renderOverlay=" + (loaded == null ? pixelText(renderOverlay) : mapPixelForWorldX(loaded.getX()) + "," + mapPixelForWorldZ(loaded.getZ()))
                + " rot=" + decoration.rot()
                + " room=" + (room == null ? "none" : room.roomGridX() + "," + room.roomGridZ())
                + " oldAccepted=" + isStrictPartyMapDecoration(client, decoration, name)
                + " drawableNow=" + drawable
                + " resolvedUuid=" + uuidText(resolvedUuid)
                + " resolvedClass=" + classText(resolvedUuid)
                + " resolvedLoaded=" + (loaded != null)
                + " partyIndexUsed=" + (selfDecoration ? "self" : partyPlayerIndex - 1));
        }
        appendLine(debug, "drawablePlayerDecorations=" + drawableCount);
    }

    private void appendRenderPlanDebug(StringBuilder debug, DungeonLiveMapWriter.MatchRenderPlan plan) {
        appendLine(debug, "");
        appendLine(debug, "[renderPlan]");
        appendLine(debug, "matches=" + plan.matches().size()
            + " hints=" + plan.hints().size()
            + " externalDoors=" + plan.externalDoors().size()
            + " visited=" + plan.visitedRooms().size()
            + " cleared=" + plan.clearedRooms().size()
            + " completed=" + plan.completedRooms().size());
        for (String line : DungeonRoomDebugFormatter.summaryLines(runTimestamp(), scanRecorder.mapSnapshot(), plan)) {
            appendLine(debug, line);
        }
    }

    private boolean isStrictPartyMapDecoration(Minecraft client, MapDecoration decoration, String name) {
        if (!isPlayerMapDecoration(decoration)) {
            return false;
        }
        if (name.isEmpty()) {
            return true;
        }
        return !name.isEmpty()
            && (name.equalsIgnoreCase(client.player.getName().getString()) || runStats.isKnownDungeonPlayer(name));
    }

    private static boolean isPlayerMapDecoration(MapDecoration decoration) {
        return decoration.type().equals(MapDecorationTypes.PLAYER)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)
            || decoration.type().equals(MapDecorationTypes.BLUE_MARKER)
            || decoration.type().equals(MapDecorationTypes.FRAME);
    }

    private UUID resolveDecorationUuid(
        Minecraft client,
        MapDecoration decoration,
        String name,
        int partyPlayerIndex,
        DungeonMapCheckmarkReader.MapPixel markerPixel,
        Set<UUID> assignedUuids
    ) {
        if (!name.isEmpty()) {
            if (client.player != null && name.equalsIgnoreCase(client.player.getName().getString())) {
                return client.player.getUUID();
            }
            return runStats.dungeonPlayerUuid(name);
        }
        if (isSelfDecoration(client, decoration, name)) {
            return client.player.getUUID();
        }
        UUID nearestLoadedPlayerUuid = nearestLoadedDungeonPlayerUuid(client, markerPixel, assignedUuids);
        if (nearestLoadedPlayerUuid != null) {
            return nearestLoadedPlayerUuid;
        }
        List<DungeonRunStats.PlayerStats> players = runStats.dungeonPlayersInOrder();
        while (partyPlayerIndex < players.size()) {
            DungeonRunStats.PlayerStats stats = players.get(partyPlayerIndex++);
            if (stats != null
                && !stats.uuid().equals(client.player.getUUID())
                && !assignedUuids.contains(stats.uuid())) {
                return stats.uuid();
            }
        }
        return null;
    }

    private UUID nearestLoadedDungeonPlayerUuid(
        Minecraft client,
        DungeonMapCheckmarkReader.MapPixel markerPixel,
        Set<UUID> assignedUuids
    ) {
        if (client.level == null || client.player == null || markerPixel == null) {
            return null;
        }

        UUID bestUuid = null;
        int bestDistance = Integer.MAX_VALUE;
        for (AbstractClientPlayer player : client.level.players()) {
            UUID uuid = player.getUUID();
            if (uuid.equals(client.player.getUUID())
                || assignedUuids.contains(uuid)
                || player.isInvisible()
                || !runStats.isKnownDungeonPlayer(player.getName().getString())) {
                continue;
            }
            if (client.getConnection() == null || client.getConnection().getPlayerInfo(uuid) == null) {
                continue;
            }

            int dx = mapPixelForWorldX(player.getX()) - markerPixel.x();
            int dz = mapPixelForWorldZ(player.getZ()) - markerPixel.z();
            int distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestUuid = uuid;
            }
        }

        return bestDistance <= PLAYER_IDENTITY_MATCH_DISTANCE * PLAYER_IDENTITY_MATCH_DISTANCE ? bestUuid : null;
    }

    private static boolean isSelfDecoration(Minecraft client, MapDecoration decoration, String name) {
        return decoration.type().equals(MapDecorationTypes.FRAME)
            || (!name.isEmpty()
                && client.player != null
                && name.equalsIgnoreCase(client.player.getName().getString()));
    }

    private static AbstractClientPlayer loadedPlayer(Minecraft client, UUID uuid) {
        if (client.level == null || uuid == null) {
            return null;
        }
        return client.level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer player ? player : null;
    }

    private String classText(UUID uuid) {
        DungeonRunStats.PlayerStats stats = uuid == null ? null : runStats.playerStats(uuid);
        return stats == null ? "UNKNOWN(no-stats)" : stats.dungeonClass().name();
    }

    private static DungeonMapCheckmarkReader.MapPixel rawOverlayPixel(MapDecoration decoration) {
        int mapX = (decoration.x() >> 1) + 64;
        int mapZ = (decoration.y() >> 1) + 64;
        return new DungeonMapCheckmarkReader.MapPixel(
            Math.clamp(Math.round(mapX * debugGridPixelSize() / 127.0F), 0, debugGridPixelSize()),
            Math.clamp(Math.round(mapZ * debugGridPixelSize() / 127.0F), 0, debugGridPixelSize())
        );
    }

    private static int mapPixelForWorldX(double worldX) {
        double roomProgress = (worldX - DungeonScanUtils.START_X) / DungeonScanUtils.ROOM_SIZE_BLOCKS;
        return Math.clamp(
            (int) Math.round(DEBUG_ROOM_SIZE / 2.0 + roomProgress * debugPixelsPerRoom()),
            0,
            debugGridPixelSize()
        );
    }

    private static int mapPixelForWorldZ(double worldZ) {
        double roomProgress = (worldZ - DungeonScanUtils.START_Z) / DungeonScanUtils.ROOM_SIZE_BLOCKS;
        return Math.clamp(
            (int) Math.round(DEBUG_ROOM_SIZE / 2.0 + roomProgress * debugPixelsPerRoom()),
            0,
            debugGridPixelSize()
        );
    }

    private static int debugGridPixelSize() {
        return debugScanGridToPixel(DungeonScanUtils.SCAN_GRID_SIZE);
    }

    private static int debugPixelsPerRoom() {
        return DEBUG_ROOM_SIZE + DEBUG_DOOR_SIZE + DEBUG_CELL_GAP * 2;
    }

    private static int debugScanGridToPixel(int gridPosition) {
        int pixel = 0;
        for (int index = 0; index < gridPosition; index++) {
            pixel += (index & 1) == 0 ? DEBUG_ROOM_SIZE : DEBUG_DOOR_SIZE;
            pixel += DEBUG_CELL_GAP;
        }
        return pixel;
    }

    private static String decorationName(MapDecoration decoration) {
        return decoration.name()
            .map(component -> component.getString().replaceAll("\u00a7.", "").trim())
            .orElse("");
    }

    private static String gridKeysText(Set<DungeonMapSnapshot.GridKey> keys) {
        List<String> parts = new ArrayList<>();
        for (DungeonMapSnapshot.GridKey key : keys) {
            parts.add(gridKeyText(key));
        }
        parts.sort(String::compareTo);
        return String.join(" ", parts);
    }

    private static String gridKeyText(DungeonMapSnapshot.GridKey key) {
        return key == null ? "none" : key.gridX() + "," + key.gridZ();
    }

    private static String pixelText(DungeonMapCheckmarkReader.MapPixel pixel) {
        return pixel == null ? "none" : pixel.x() + "," + pixel.z();
    }

    private static String uuidText(UUID uuid) {
        return uuid == null ? "none" : uuid.toString();
    }

    private static void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String decorationTypeLabel(MapDecoration decoration) {
        if (decoration.type().equals(MapDecorationTypes.PLAYER)) {
            return "PLAYER";
        }
        if (decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)) {
            return "PLAYER_OFF_MAP";
        }
        if (decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)) {
            return "PLAYER_OFF_LIMITS";
        }
        if (decoration.type().equals(MapDecorationTypes.BLUE_MARKER)) {
            return "BLUE_MARKER";
        }
        if (decoration.type().equals(MapDecorationTypes.FRAME)) {
            return "FRAME";
        }
        return String.valueOf(decoration.type());
    }

    public void initializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        DungeonServerTickEvents.register(() -> {
            if (dungeonInstanceActive) {
                splitTracker.serverTick(System.currentTimeMillis());
            }
        });
        ClientReceiveMessageEvents.GAME.register(this::observeGameMessage);
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            observeChatMessage(message));
        scanRecorder.initializeClient(this);
    }

    private void observeGameMessage(Component message, boolean overlay) {
        try {
            observeDungeonMessage(Minecraft.getInstance(), message.getString(), overlay);
        } catch (RuntimeException | LinkageError exception) {
            KungMod.LOGGER.warn("Failed to process dungeon game message.", exception);
        }
    }

    private void observeChatMessage(Component message) {
        try {
            observeDungeonMessage(Minecraft.getInstance(), message.getString(), false);
        } catch (RuntimeException | LinkageError exception) {
            KungMod.LOGGER.warn("Failed to process dungeon chat message.", exception);
        }
    }

    private void observeDungeonMessage(Minecraft client, String text, boolean overlay) {
        observeRunStartSignal(client, text);
        runStats.observeMessage(client, text, dungeonTick);
        if (overlay && mapVisibleArea) {
            runStats.observeRoomSecretOverlay(client, text, renderPlan());
        }
        splitTracker.configureForFloor(runStats.floor(), runStats.masterMode());
        splitTracker.observeMessage(text, dungeonTick);
        observeRunFinishedSignal(text);
    }

    public LearnRoomTypeResult learnCurrentRoomType(Minecraft client, RoomType roomType) {
        CurrentRoomCore currentRoomCore = currentRoomCore(client);
        if (!currentRoomCore.valid()) {
            return LearnRoomTypeResult.failed(currentRoomCore.message());
        }

        try {
            DungeonRoomClassifier.learnRoomType(currentRoomCore.coreHash(), roomType);
        } catch (IOException | IllegalArgumentException exception) {
            KungMod.LOGGER.warn("Failed to learn dungeon room type.", exception);
            return LearnRoomTypeResult.failed("Konnte den Raumtyp nicht speichern. Siehe latest.log.");
        }

        return LearnRoomTypeResult.learned(
            currentRoomCore.coreHash(),
            currentRoomCore.roomGridX(),
            currentRoomCore.roomGridZ(),
            roomType
        );
    }

    public LearnRoomResult learnCurrentRoom(Minecraft client, String name, RoomType roomType, int secrets) {
        return learnCurrentRoom(client, name, roomType, secrets, 0);
    }

    public LearnRoomResult learnCurrentRoom(Minecraft client, String name, RoomType roomType, int secrets, int crypts) {
        CurrentRoomCore currentRoomCore = currentRoomCore(client);
        return learnRoomCore(currentRoomCore, name, roomType, secrets, crypts);
    }

    public LearnRoomResult learnCurrentKnownRoom(Minecraft client, DungeonKnownRoomCatalog.KnownRoomInfo info) {
        return learnCurrentRoom(client, info.name(), info.type(), info.secrets(), info.crypts());
    }

    public LearnRoomResult learnCurrentMultiRoom(Minecraft client, String name, RoomType roomType, int secrets, int crypts) {
        CurrentRoomCore currentRoomCore = currentRoomCore(client);
        if (!currentRoomCore.valid()) {
            return LearnRoomResult.failed(currentRoomCore.message());
        }

        List<DungeonKnownRoomCatalog.LearnedRoom> roomComponents =
            connectedLearnedRoomComponents(currentRoomCore, name, roomType, secrets, crypts);
        if (roomComponents.size() <= 1) {
            return LearnRoomResult.failed(
                "Nur eine passende Zelle gefunden. Nutze normales learn oder lade/betritt weitere Raumteile."
            );
        }
        if (roomComponents.size() > MAX_MULTI_LEARN_COMPONENTS) {
            return LearnRoomResult.failed(
                "Multicell-Learn abgebrochen: "
                    + roomComponents.size()
                    + " Zellen gefunden, maximal erlaubt sind "
                    + MAX_MULTI_LEARN_COMPONENTS
                    + "."
            );
        }

        List<DungeonKnownRoomCatalog.LearnedRoom> syncComponents = new ArrayList<>(roomComponents);
        try {
            for (DungeonKnownRoomCatalog.LearnedRoom component : roomComponents) {
                DungeonRoomClassifier.learnRoomType(component.coreHash(), roomType);
            }
            DungeonKnownRoomCatalog.appendAll(roomComponents);
            for (DungeonKnownRoomCatalog.LearnedRoom component : roomComponents) {
                for (DungeonKnownRoomCatalog.LearnedRoom initialHash : initialLearnedRoomComponents(
                    component,
                    name,
                    roomType,
                    secrets,
                    crypts
                )) {
                    DungeonKnownRoomCatalog.append(initialHash);
                    syncComponents.add(initialHash);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            KungMod.LOGGER.warn("Failed to learn multicell dungeon room.", exception);
            return LearnRoomResult.failed("Konnte den Mehrzellen-Raum nicht speichern. Siehe latest.log.");
        }

        pushRoomSyncReport(name, roomType, secrets, crypts, syncComponents);
        return LearnRoomResult.learnedMulti(
            name,
            currentRoomCore.coreHash(),
            currentRoomCore.roomGridX(),
            currentRoomCore.roomGridZ(),
            roomType,
            secrets,
            crypts,
            roomComponents.size()
        );
    }

    public LearnRoomResult learnRoomAt(
        Minecraft client,
        BlockPos targetPosition,
        String name,
        RoomType roomType,
        int secrets
    ) {
        return learnRoomAt(client, targetPosition, name, roomType, secrets, 0);
    }

    public LearnRoomResult learnRoomAt(
        Minecraft client,
        BlockPos targetPosition,
        String name,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        CurrentRoomCore targetRoomCore = roomCoreAt(client, targetPosition);
        return learnRoomCore(targetRoomCore, name, roomType, secrets, crypts);
    }

    private LearnRoomResult learnRoomCore(
        CurrentRoomCore currentRoomCore,
        String name,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        if (!currentRoomCore.valid()) {
            return LearnRoomResult.failed(currentRoomCore.message());
        }

        List<DungeonKnownRoomCatalog.LearnedRoom> components = learnedRoomComponentsWithInitialHash(
            currentRoomCore,
            name,
            roomType,
            secrets,
            crypts
        );
        try {
            DungeonRoomClassifier.learnRoomType(currentRoomCore.coreHash(), roomType);
            for (DungeonKnownRoomCatalog.LearnedRoom component : components) {
                DungeonKnownRoomCatalog.append(component);
            }
        } catch (IOException | IllegalArgumentException exception) {
            KungMod.LOGGER.warn("Failed to learn dungeon room.", exception);
            return LearnRoomResult.failed("Konnte den Raum nicht speichern. Siehe latest.log.");
        }

        pushRoomSyncReport(name, roomType, secrets, crypts, components);
        return LearnRoomResult.learned(
            name,
            currentRoomCore.coreHash(),
            currentRoomCore.roomGridX(),
            currentRoomCore.roomGridZ(),
            roomType,
            secrets,
            crypts
        );
    }

    private List<DungeonKnownRoomCatalog.LearnedRoom> learnedRoomComponentsWithInitialHash(
        CurrentRoomCore currentRoomCore,
        String name,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        DungeonKnownRoomCatalog.LearnedRoom current = learnedRoomComponent(
            System.currentTimeMillis(),
            name,
            roomType,
            secrets,
            crypts,
            currentRoomCore.coreHash(),
            currentRoomCore.stableCoreHash(),
            currentRoomCore.roomGridX(),
            currentRoomCore.roomGridZ()
        );
        List<DungeonKnownRoomCatalog.LearnedRoom> components = new ArrayList<>();
        components.add(current);
        components.addAll(initialLearnedRoomComponents(current, name, roomType, secrets, crypts));
        return components;
    }

    private List<DungeonKnownRoomCatalog.LearnedRoom> initialLearnedRoomComponents(
        DungeonKnownRoomCatalog.LearnedRoom current,
        String name,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        DungeonMapSnapshot.ObservedPoint initialPoint =
            scanRecorder.mapSnapshot().initialRoomPointAt(current.roomGridX(), current.roomGridZ());
        if (initialPoint == null
            || initialPoint.point().kind() != DungeonScanPointKind.ROOM
            || DungeonRoomClassifier.isEmptyCore(initialPoint.point().coreHash())
            || sameHash(current, initialPoint.point())) {
            return List.of();
        }
        return List.of(learnedRoomComponent(
            current.order() + 1,
            name,
            roomType,
            secrets,
            crypts,
            initialPoint.point().coreHash(),
            initialPoint.point().stableCoreHash(),
            current.roomGridX(),
            current.roomGridZ()
        ));
    }

    private static DungeonKnownRoomCatalog.LearnedRoom learnedRoomComponent(
        long order,
        String name,
        RoomType roomType,
        int secrets,
        int crypts,
        int coreHash,
        int stableCoreHash,
        int roomGridX,
        int roomGridZ
    ) {
        return new DungeonKnownRoomCatalog.LearnedRoom(
            order,
            name,
            roomType,
            secrets,
            crypts,
            coreHash,
            stableCoreHash,
            roomGridX,
            roomGridZ
        );
    }

    private static boolean sameHash(DungeonKnownRoomCatalog.LearnedRoom current, DungeonScanPoint point) {
        return current.coreHash() == point.coreHash()
            && current.stableCoreHash() == point.stableCoreHash();
    }

    private List<DungeonKnownRoomCatalog.LearnedRoom> connectedLearnedRoomComponents(
        CurrentRoomCore currentRoomCore,
        String name,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        DungeonMapSnapshot snapshot = scanRecorder.mapSnapshot();
        List<DungeonKnownRoomCatalog.LearnedRoom> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<int[]> queue = new ArrayList<>();
        queue.add(new int[] { currentRoomCore.roomGridX(), currentRoomCore.roomGridZ() });
        visited.add(currentRoomCore.roomGridX() + "," + currentRoomCore.roomGridZ());
        long order = System.currentTimeMillis();

        for (int index = 0; index < queue.size(); index++) {
            int roomGridX = queue.get(index)[0];
            int roomGridZ = queue.get(index)[1];
            DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(roomGridX * 2, roomGridZ * 2);
            CurrentRoomCore roomCore = observedPoint == null
                ? (roomGridX == currentRoomCore.roomGridX() && roomGridZ == currentRoomCore.roomGridZ()
                    ? currentRoomCore
                    : CurrentRoomCore.failed("Nicht geladen."))
                : CurrentRoomCore.valid(
                    observedPoint.point().coreHash(),
                    observedPoint.point().stableCoreHash(),
                    roomGridX,
                    roomGridZ
                );
            if (!roomCore.valid()
                || DungeonRoomClassifier.isEmptyCore(roomCore.coreHash())
                || isBlockedMultiLearnRoom(snapshot, roomGridX, roomGridZ, roomCore.coreHash())
                || hasConflictingKnownHint(roomCore.coreHash(), roomCore.stableCoreHash(), name, roomType, secrets)) {
                continue;
            }

            components.add(learnedRoomComponent(
                order++,
                name,
                roomType,
                secrets,
                crypts,
                roomCore.coreHash(),
                roomCore.stableCoreHash(),
                roomGridX,
                roomGridZ
            ));

            addConnectedMultiLearnNeighbor(snapshot, queue, visited, roomGridX, roomGridZ, 1, 0, name, roomType, secrets);
            addConnectedMultiLearnNeighbor(snapshot, queue, visited, roomGridX, roomGridZ, -1, 0, name, roomType, secrets);
            addConnectedMultiLearnNeighbor(snapshot, queue, visited, roomGridX, roomGridZ, 0, 1, name, roomType, secrets);
            addConnectedMultiLearnNeighbor(snapshot, queue, visited, roomGridX, roomGridZ, 0, -1, name, roomType, secrets);
        }

        return components;
    }

    private static void addConnectedMultiLearnNeighbor(
        DungeonMapSnapshot snapshot,
        List<int[]> queue,
        Set<String> visited,
        int roomGridX,
        int roomGridZ,
        int dx,
        int dz,
        String name,
        RoomType roomType,
        int secrets
    ) {
        int nextRoomGridX = roomGridX + dx;
        int nextRoomGridZ = roomGridZ + dz;
        String key = nextRoomGridX + "," + nextRoomGridZ;
        if (visited.contains(key) || !isValidRoomGrid(nextRoomGridX, nextRoomGridZ)) {
            return;
        }

        DungeonMapSnapshot.ObservedPoint doorPoint = snapshot.pointAt(roomGridX * 2 + dx, roomGridZ * 2 + dz);
        if (doorPoint != null
            && doorPoint.point().kind() == DungeonScanPointKind.DOOR
            && doorPoint.point().doorKind().visible()) {
            return;
        }

        DungeonMapSnapshot.ObservedPoint neighbor = snapshot.pointAt(nextRoomGridX * 2, nextRoomGridZ * 2);
        if (neighbor == null
            || neighbor.point().kind() != DungeonScanPointKind.ROOM
            || DungeonRoomClassifier.isEmptyCore(neighbor.point().coreHash())
            || isBlockedMultiLearnRoom(snapshot, nextRoomGridX, nextRoomGridZ, neighbor.point().coreHash())
            || hasConflictingKnownHint(
                neighbor.point().coreHash(),
                neighbor.point().stableCoreHash(),
                name,
                roomType,
                secrets
            )) {
            return;
        }

        visited.add(key);
        queue.add(new int[] { nextRoomGridX, nextRoomGridZ });
    }

    private static boolean isBlockedMultiLearnRoom(
        DungeonMapSnapshot snapshot,
        int roomGridX,
        int roomGridZ,
        int coreHash
    ) {
        return snapshot.isStartRoom(roomGridX * 2, roomGridZ * 2)
            || DungeonRoomClassifier.classifyRoom(coreHash) == RoomType.START;
    }

    private static boolean hasConflictingKnownHint(
        int coreHash,
        int stableCoreHash,
        String name,
        RoomType roomType,
        int secrets
    ) {
        DungeonKnownRoomCatalog.KnownCoreHint hint = DungeonKnownRoomCatalog.knownCoreHint(coreHash);
        if (hint == null && stableCoreHash != 0) {
            hint = DungeonKnownRoomCatalog.knownCoreHint(stableCoreHash);
        }
        return hint != null
            && (!hint.name().equalsIgnoreCase(name)
                || hint.type() != roomType
                || hint.secrets() != secrets);
    }

    public LearnRoomResult updateCurrentRoomCrypts(Minecraft client, int crypts) {
        if (client.level == null || client.player == null) {
            return LearnRoomResult.failed("Du bist gerade nicht in einer Welt.");
        }

        DungeonScanUtils.GridPosition roomGrid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        DungeonKnownRoomCatalog.MatchedRoom match = matchedRoomAt(roomGrid.gridX(), roomGrid.gridZ());
        if (match == null) {
            return LearnRoomResult.failed("Der aktuelle Raum ist noch nicht eindeutig bekannt.");
        }

        try {
            DungeonKnownRoomCatalog.updateRoomCrypts(
                match.template().name(),
                match.template().type(),
                match.template().secrets(),
                crypts
            );
        } catch (IOException | IllegalArgumentException exception) {
            KungMod.LOGGER.warn("Failed to update dungeon room crypts.", exception);
            return LearnRoomResult.failed("Konnte Crypts fuer diesen Raum nicht speichern. Siehe latest.log.");
        }

        return LearnRoomResult.learned(
            match.template().name(),
            0,
            roomGrid.gridX(),
            roomGrid.gridZ(),
            match.template().type(),
            match.template().secrets(),
            crypts
        );
    }

    public RoomDataResult currentRoomData(Minecraft client) {
        CurrentRoomCore currentRoomCore = currentRoomCore(client);
        if (!currentRoomCore.valid()) {
            return RoomDataResult.failed(currentRoomCore.message());
        }

        DungeonKnownRoomCatalog.KnownCoreHint coreHint = DungeonKnownRoomCatalog.knownCoreHint(currentRoomCore.coreHash());
        DungeonKnownRoomCatalog.KnownCoreHint stableHint = currentRoomCore.stableCoreHash() == 0
            ? null
            : DungeonKnownRoomCatalog.knownCoreHint(currentRoomCore.stableCoreHash());
        DungeonKnownRoomCatalog.MatchedRoom match =
            matchedRoomAt(currentRoomCore.roomGridX(), currentRoomCore.roomGridZ());
        DungeonMapSnapshot.ObservedPoint initialPoint =
            scanRecorder.mapSnapshot().initialRoomPointAt(currentRoomCore.roomGridX(), currentRoomCore.roomGridZ());

        String clipboardText = "roomGrid=" + currentRoomCore.roomGridX() + "," + currentRoomCore.roomGridZ()
            + " coreHash=" + currentRoomCore.coreHash()
            + " stableCoreHash=" + currentRoomCore.stableCoreHash()
            + " initial=" + formatInitialPoint(initialPoint)
            + " coreHint=" + formatHint(coreHint)
            + " stableHint=" + formatHint(stableHint)
            + " match=" + formatMatch(match);

        return RoomDataResult.found("RoomData kopiert: " + clipboardText, clipboardText);
    }

    public RoomDataResult exportCurrentRoom(
        Minecraft client,
        String name,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        CurrentRoomCore currentRoomCore = currentRoomCore(client);
        if (!currentRoomCore.valid()) {
            return RoomDataResult.failed(currentRoomCore.message());
        }

        List<DungeonKnownRoomCatalog.LearnedRoom> components =
            connectedLearnedRoomComponents(currentRoomCore, name, roomType, secrets, crypts);
        if (components.isEmpty()) {
            return RoomDataResult.failed(
                "Room-Export abgebrochen: keine passende Raumzelle fuer \"" + name + "\" gefunden."
            );
        }
        if (components.size() > MAX_MULTI_LEARN_COMPONENTS) {
            return RoomDataResult.failed(
                "Room-Export abgebrochen: "
                    + components.size()
                    + " Zellen gefunden, maximal erlaubt sind "
                    + MAX_MULTI_LEARN_COMPONENTS
                    + "."
            );
        }

        components = new ArrayList<>(components);
        components.sort((first, second) -> {
            int comparison = Integer.compare(first.roomGridZ(), second.roomGridZ());
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(first.roomGridX(), second.roomGridX());
        });

        int minRoomGridX = components.stream().mapToInt(DungeonKnownRoomCatalog.LearnedRoom::roomGridX).min().orElse(0);
        int minRoomGridZ = components.stream().mapToInt(DungeonKnownRoomCatalog.LearnedRoom::roomGridZ).min().orElse(0);
        List<ExportCell> exportCells = new ArrayList<>();
        int hashCount = 0;
        for (DungeonKnownRoomCatalog.LearnedRoom component : components) {
            List<ExportHash> hashes = new ArrayList<>();
            addExportHash(hashes, "current", component.coreHash(), component.stableCoreHash());
            for (DungeonKnownRoomCatalog.LearnedRoom initial : initialLearnedRoomComponents(
                component,
                name,
                roomType,
                secrets,
                crypts
            )) {
                addExportHash(hashes, "initial", initial.coreHash(), initial.stableCoreHash());
            }
            hashCount += hashes.size();
            exportCells.add(new ExportCell(
                component.roomGridX() - minRoomGridX,
                component.roomGridZ() - minRoomGridZ,
                component.roomGridX(),
                component.roomGridZ(),
                hashes
            ));
        }

        String clipboardText = roomExportJson(name, roomType, secrets, crypts, exportCells);
        return RoomDataResult.found(
            "Room-Export kopiert: \""
                + name
                + "\" "
                + roomType.name()
                + " secrets="
                + secrets
                + (crypts > 0 ? " crypts=" + crypts : "")
                + " cells="
                + exportCells.size()
                + " hashes="
                + hashCount
                + ".",
            clipboardText
        );
    }

    private static void pushRoomSyncReport(
        String name,
        RoomType roomType,
        int secrets,
        int crypts,
        List<DungeonKnownRoomCatalog.LearnedRoom> components
    ) {
        if (components.isEmpty()) {
            return;
        }
        DungeonRoomDataSyncClient.INSTANCE.pushRoomReportAsync(
            roomExportJsonFromLearnedComponents(name, roomType, secrets, crypts, components)
        );
    }

    private static String roomExportJsonFromLearnedComponents(
        String name,
        RoomType roomType,
        int secrets,
        int crypts,
        List<DungeonKnownRoomCatalog.LearnedRoom> components
    ) {
        List<DungeonKnownRoomCatalog.LearnedRoom> sorted = new ArrayList<>(components);
        sorted.sort((first, second) -> {
            int comparison = Integer.compare(first.roomGridZ(), second.roomGridZ());
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(first.roomGridX(), second.roomGridX());
        });

        int minRoomGridX = sorted.stream().mapToInt(DungeonKnownRoomCatalog.LearnedRoom::roomGridX).min().orElse(0);
        int minRoomGridZ = sorted.stream().mapToInt(DungeonKnownRoomCatalog.LearnedRoom::roomGridZ).min().orElse(0);
        List<ExportCell> exportCells = new ArrayList<>();
        for (DungeonKnownRoomCatalog.LearnedRoom component : sorted) {
            ExportCell exportCell = exportCellAt(exportCells, component.roomGridX(), component.roomGridZ());
            if (exportCell == null) {
                exportCell = new ExportCell(
                    component.roomGridX() - minRoomGridX,
                    component.roomGridZ() - minRoomGridZ,
                    component.roomGridX(),
                    component.roomGridZ(),
                    new ArrayList<>()
                );
                exportCells.add(exportCell);
            }
            addExportHash(exportCell.hashes(), "learn", component.coreHash(), component.stableCoreHash());
        }
        return roomExportJson(name, roomType, secrets, crypts, exportCells);
    }

    private static ExportCell exportCellAt(List<ExportCell> cells, int roomGridX, int roomGridZ) {
        for (ExportCell cell : cells) {
            if (cell.roomGridX() == roomGridX && cell.roomGridZ() == roomGridZ) {
                return cell;
            }
        }
        return null;
    }

    private static void addExportHash(List<ExportHash> hashes, String source, int coreHash, int stableCoreHash) {
        for (ExportHash hash : hashes) {
            if (hash.coreHash() == coreHash && hash.stableCoreHash() == stableCoreHash) {
                return;
            }
        }
        hashes.add(new ExportHash(source, coreHash, stableCoreHash));
    }

    private static String roomExportJson(
        String name,
        RoomType roomType,
        int secrets,
        int crypts,
        List<ExportCell> cells
    ) {
        StringBuilder json = new StringBuilder();
        json.append('{')
            .append("\"schema\":1,")
            .append("\"kind\":\"kung-room-export\",")
            .append("\"name\":");
        appendJsonString(json, name);
        json.append(',')
            .append("\"type\":\"").append(roomType.name()).append("\",")
            .append("\"secrets\":").append(secrets).append(',')
            .append("\"crypts\":").append(crypts).append(',')
            .append("\"cellCount\":").append(cells.size()).append(',')
            .append("\"createdAt\":").append(System.currentTimeMillis()).append(',')
            .append("\"cells\":[");
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            ExportCell cell = cells.get(index);
            json.append('{')
                .append("\"dx\":").append(cell.dx()).append(',')
                .append("\"dz\":").append(cell.dz()).append(',')
                .append("\"roomGridX\":").append(cell.roomGridX()).append(',')
                .append("\"roomGridZ\":").append(cell.roomGridZ()).append(',')
                .append("\"hashes\":[");
            for (int hashIndex = 0; hashIndex < cell.hashes().size(); hashIndex++) {
                if (hashIndex > 0) {
                    json.append(',');
                }
                ExportHash hash = cell.hashes().get(hashIndex);
                json.append('{')
                    .append("\"source\":\"").append(hash.source()).append("\",")
                    .append("\"coreHash\":").append(hash.coreHash()).append(',')
                    .append("\"stableCoreHash\":").append(hash.stableCoreHash())
                    .append('}');
            }
            json.append("]}");
        }
        json.append("]}");
        return json.toString();
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private DungeonKnownRoomCatalog.MatchedRoom matchedRoomAt(int roomGridX, int roomGridZ) {
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan().matches()) {
            if (match.contains(roomGridX, roomGridZ)) {
                return match;
            }
        }
        return null;
    }

    private static String formatInitialPoint(DungeonMapSnapshot.ObservedPoint initialPoint) {
        if (initialPoint == null || initialPoint.point().kind() != DungeonScanPointKind.ROOM) {
            return "none";
        }
        return initialPoint.point().coreHash() + "/" + initialPoint.point().stableCoreHash();
    }

    private static String formatHint(DungeonKnownRoomCatalog.KnownCoreHint hint) {
        if (hint == null) {
            return "unknown";
        }
        return hint.name() + "|" + hint.type().name() + "|secrets=" + hint.secrets() + "|crypts=" + hint.crypts();
    }

    private static String formatMatch(DungeonKnownRoomCatalog.MatchedRoom match) {
        if (match == null) {
            return "none";
        }
        return match.template().name()
            + "|"
            + match.template().type().name()
            + "|secrets="
            + match.template().secrets()
            + "|crypts="
            + match.template().crypts()
            + "|cells="
            + match.components().size();
    }

    private CurrentRoomCore currentRoomCore(Minecraft client) {
        if (client.level == null || client.player == null) {
            return CurrentRoomCore.failed("Du bist gerade nicht in einer Welt.");
        }

        return roomCoreAt(client, client.player.blockPosition());
    }

    private CurrentRoomCore roomCoreAt(Minecraft client, BlockPos position) {
        if (client.level == null || client.player == null) {
            return CurrentRoomCore.failed("Du bist gerade nicht in einer Welt.");
        }

        DungeonScanUtils.GridPosition roomGrid = DungeonScanUtils.getRoomGridPosition(position);
        int scanGridX = roomGrid.gridX() * 2;
        int scanGridZ = roomGrid.gridZ() * 2;

        if (!isValidScanRoom(scanGridX, scanGridZ)) {
            return CurrentRoomCore.failed(
                "Deine Position liegt nicht im bekannten Dungeon-Raster: "
                    + roomGrid.gridX() + "," + roomGrid.gridZ()
            );
        }

        int worldX = DungeonScanUtils.worldXForScanGrid(scanGridX);
        int worldZ = DungeonScanUtils.worldZForScanGrid(scanGridZ);
        if (!DungeonScanUtils.isChunkLoaded(client.level, worldX, worldZ)) {
            CurrentRoomCore snapshotCore = snapshotRoomCore(roomGrid.gridX(), roomGrid.gridZ());
            return snapshotCore.valid()
                ? snapshotCore
                : CurrentRoomCore.failed("Die Mitte dieses Raums ist noch nicht geladen.");
        }

        int coreHash = DungeonScanUtils.getCoreHash(client.level, worldX, worldZ);
        if (DungeonRoomClassifier.isEmptyCore(coreHash)) {
            CurrentRoomCore snapshotCore = snapshotRoomCore(roomGrid.gridX(), roomGrid.gridZ());
            return snapshotCore.valid()
                ? snapshotCore
                : CurrentRoomCore.failed("Der aktuelle Raum sieht fuer den Scanner leer aus.");
        }
        int stableCoreHash = DungeonScanUtils.getStableCoreHash(client.level, worldX, worldZ);

        return CurrentRoomCore.valid(
            coreHash,
            stableCoreHash,
            roomGrid.gridX(),
            roomGrid.gridZ()
        );
    }

    private CurrentRoomCore snapshotRoomCore(int roomGridX, int roomGridZ) {
        DungeonMapSnapshot.ObservedPoint observedPoint =
            scanRecorder.mapSnapshot().pointAt(roomGridX * 2, roomGridZ * 2);
        if (observedPoint == null
            || observedPoint.point().kind() != DungeonScanPointKind.ROOM
            || DungeonRoomClassifier.isEmptyCore(observedPoint.point().coreHash())) {
            return CurrentRoomCore.failed("Kein geladener Raum-Hash im Snapshot.");
        }

        return CurrentRoomCore.valid(
            observedPoint.point().coreHash(),
            observedPoint.point().stableCoreHash(),
            roomGridX,
            roomGridZ
        );
    }

    private void tick(Minecraft client) {
        dungeonTick++;
        if (client.level == null || client.player == null || !runDetector.isConnectedToHypixel(client)) {
            endDungeonInstance(client);
            state.setInDungeon(false);
            inDungeonArea = false;
            mapVisibleArea = false;
            activeLevel = null;
            return;
        }

        if (activeLevel != null && client.level != activeLevel) {
            endDungeonInstance(client);
        }

        if (runDetector.isDungeonHub(client)) {
            endDungeonInstance(client);
            state.setInDungeon(false);
            inDungeonArea = false;
            mapVisibleArea = false;
            activeLevel = client.level;
            return;
        }

        boolean detectedInRun = runDetector.isInDungeonRun(client);
        boolean detectedInArea = runDetector.isInDungeonArea(client);
        boolean catacombsContext = runDetector.hasCatacombsContext(client);
        boolean detectedInstance = detectedInRun || detectedInArea || catacombsContext;
        if (!dungeonInstanceActive && detectedInstance) {
            startDungeonInstance(client);
        }

        inDungeonArea = detectedInArea || (dungeonInstanceActive && catacombsContext);
        mapVisibleArea = dungeonInstanceActive || inDungeonArea;
        if (mapVisibleArea) {
            lastDungeonAreaSeenTick = dungeonTick;
        }

        if (dungeonInstanceActive) {
            if (shouldObserve(lastStatsObserveTick, STATS_OBSERVE_INTERVAL_TICKS)) {
                lastStatsObserveTick = dungeonTick;
                runStats.observePlayers(client, dungeonTick);
                splitTracker.configureForFloor(runStats.floor(), runStats.masterMode());
            }
        }
        if (mapVisibleArea) {
            observeBloodDoorOpenFromMap();
            observeClearStates(client);
        }
        if (pendingRunSummaryTick != Long.MIN_VALUE && dungeonTick >= pendingRunSummaryTick) {
            sendRunSummaryOnce(client);
        }
        state.setInDungeon(dungeonInstanceActive);
    }

    private boolean shouldObserve(long lastObserveTick, long intervalTicks) {
        return lastObserveTick == Long.MIN_VALUE || dungeonTick - lastObserveTick >= intervalTicks;
    }

    private boolean stickyActiveRun(boolean catacombsContext) {
        return lastInDungeon
            && catacombsContext
            && missingRunTicks < 200;
    }

    private void startDungeonInstance(Minecraft client) {
        KungMod.LOGGER.info("Dungeon instance detected. Starting instance tracking.");
        dungeonInstanceActive = true;
        lastInDungeon = true;
        missingRunTicks = 0;
        activeLevel = client.level;
        cachedRenderPlan = null;
        cachedRenderPlanRevision = Long.MIN_VALUE;
        cachedRenderPlanCatalogRevision = Long.MIN_VALUE;
        lastStatsObserveTick = Long.MIN_VALUE;
        lastClearStateObserveTick = Long.MIN_VALUE;
        lastObservedClearStateRevision = Long.MIN_VALUE;
        state.setRooms(java.util.List.of());
        runStats.reset();
        runStats.startRun(dungeonTick);
        splitTracker.startRun(dungeonTick, runStats.floor(), runStats.masterMode());
        pendingRunSummaryTick = Long.MIN_VALUE;
        runSummarySent = false;
        state.setInDungeon(true);
    }

    private void restartDungeonRun(Minecraft client) {
        if (!dungeonInstanceActive) {
            startDungeonInstance(client);
            return;
        }

        KungMod.LOGGER.info("Dungeon run start detected. Restarting run tracking.");
        cachedRenderPlan = null;
        cachedRenderPlanRevision = Long.MIN_VALUE;
        cachedRenderPlanCatalogRevision = Long.MIN_VALUE;
        lastStatsObserveTick = Long.MIN_VALUE;
        lastClearStateObserveTick = Long.MIN_VALUE;
        lastObservedClearStateRevision = Long.MIN_VALUE;
        pendingRunSummaryTick = Long.MIN_VALUE;
        runSummarySent = false;
        state.setRooms(java.util.List.of());
        runStats.reset();
        runStats.startRun(dungeonTick);
        splitTracker.startRun(dungeonTick, runStats.floor(), runStats.masterMode());
        scanRecorder.restartRecording();
        state.setInDungeon(true);
        if (client != null && client.level != null) {
            activeLevel = client.level;
        }
    }

    private void endDungeonInstance(Minecraft client) {
        if (!dungeonInstanceActive && !lastInDungeon) {
            return;
        }

        KungMod.LOGGER.info("Dungeon instance ended. Resetting instance tracking.");
        runStats.stopRun(dungeonTick);
        sendRunSummaryOnce(client);
        splitTracker.stopRun();
        runStats.reset();
        dungeonInstanceActive = false;
        lastInDungeon = false;
        missingRunTicks = 0;
        pendingRunSummaryTick = Long.MIN_VALUE;
        runSummarySent = false;
        cachedRenderPlan = null;
        cachedRenderPlanRevision = Long.MIN_VALUE;
        cachedRenderPlanCatalogRevision = Long.MIN_VALUE;
        lastStatsObserveTick = Long.MIN_VALUE;
        lastClearStateObserveTick = Long.MIN_VALUE;
        lastObservedClearStateRevision = Long.MIN_VALUE;
        state.setRooms(java.util.List.of());
        state.setInDungeon(false);
    }

    private void observeRunStartSignal(Minecraft client, String text) {
        if (!isRunStartSignal(text) || duplicateLifecycleSignal(lastRunStartSignalTick)) {
            return;
        }
        if (!dungeonInstanceActive
            && (client == null
                || !runDetector.isConnectedToHypixel(client)
                || runDetector.isDungeonHub(client)
                || (!runDetector.hasCatacombsContext(client) && !runDetector.isInsideDungeonGrid(client)))) {
            return;
        }
        lastRunStartSignalTick = dungeonTick;
        restartDungeonRun(client);
    }

    private void observeRunFinishedSignal(String text) {
        if (!isRunFinishedSignal(text) || duplicateLifecycleSignal(lastRunFinishedSignalTick)) {
            return;
        }
        if (!dungeonInstanceActive && !lastInDungeon) {
            return;
        }
        lastRunFinishedSignalTick = dungeonTick;
        runStats.stopRun(dungeonTick);
        splitTracker.stopRun();
        if (!runSummarySent) {
            pendingRunSummaryTick = dungeonTick + RUN_SUMMARY_DELAY_TICKS;
        }
    }

    private void sendRunSummaryOnce(Minecraft client) {
        if (runSummarySent) {
            pendingRunSummaryTick = Long.MIN_VALUE;
            return;
        }
        if (client != null && client.level != null && client.player != null) {
            runStats.observePlayers(client, dungeonTick);
            if (mapVisibleArea) {
                observeClearStates(client);
            }
        }
        runStats.stopRun(dungeonTick);
        runStats.sendRunSummary(client, renderPlan());
        runSummarySent = true;
        pendingRunSummaryTick = Long.MIN_VALUE;
    }

    private boolean duplicateLifecycleSignal(long lastSignalTick) {
        return lastSignalTick != Long.MIN_VALUE
            && dungeonTick - lastSignalTick <= LIFECYCLE_DUPLICATE_WINDOW_TICKS;
    }

    private static boolean isRunStartSignal(String text) {
        return text != null && RUN_START_SIGNAL.matcher(cleanLifecycleMessage(text)).matches();
    }

    private static boolean isRunFinishedSignal(String text) {
        return text != null && RUN_FINISHED_SIGNAL.matcher(cleanLifecycleMessage(text)).matches();
    }

    private static String cleanLifecycleMessage(String text) {
        return text.replaceAll("\u00a7.", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private void observeBloodDoorOpenFromMap() {
        if (!splitTracker.running() || !"Blood Open".equals(splitTracker.currentSplitName())) {
            return;
        }

        for (DungeonLiveMapWriter.DoorRenderInfo door : renderPlan().externalDoors().values()) {
            if (door.kind() == DungeonDoorKind.OPEN && door.targetType() == RoomType.BLOOD) {
                splitTracker.markIfCurrent("Blood Open", "Blood Clear", dungeonTick);
                return;
            }
        }
    }

    private void observeClearStates(Minecraft client) {
        if (!shouldObserve(lastClearStateObserveTick, CLEAR_STATE_OBSERVE_INTERVAL_TICKS)) {
            return;
        }

        DungeonMapCheckmarkReader.observe(client, scanRecorder.mapSnapshot());
        long revision = scanRecorder.mapSnapshot().revision();
        runStats.observeClearStates(client, renderPlan());
        lastObservedClearStateRevision = revision;
        lastClearStateObserveTick = dungeonTick;
    }

    private static boolean isValidScanRoom(int scanGridX, int scanGridZ) {
        return scanGridX >= 0
            && scanGridZ >= 0
            && scanGridX < DungeonScanUtils.SCAN_GRID_SIZE
            && scanGridZ < DungeonScanUtils.SCAN_GRID_SIZE;
    }

    private static boolean isValidRoomGrid(int roomGridX, int roomGridZ) {
        return roomGridX >= 0
            && roomGridZ >= 0
            && roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    public record LearnRoomTypeResult(
        boolean learned,
        String message,
        int coreHash,
        int roomGridX,
        int roomGridZ,
        RoomType roomType
    ) {
        static LearnRoomTypeResult learned(int coreHash, int roomGridX, int roomGridZ, RoomType roomType) {
            return new LearnRoomTypeResult(
                true,
                "Gelernt: " + coreHash + "=" + roomType.name()
                    + " bei roomGrid " + roomGridX + "," + roomGridZ + ".",
                coreHash,
                roomGridX,
                roomGridZ,
                roomType
            );
        }

        static LearnRoomTypeResult failed(String message) {
            return new LearnRoomTypeResult(false, message, 0, 0, 0, RoomType.UNKNOWN);
        }
    }

    public record LearnRoomResult(
        boolean learned,
        String message,
        String name,
        int coreHash,
        int roomGridX,
        int roomGridZ,
        RoomType roomType,
        int secrets,
        int crypts
    ) {
        static LearnRoomResult learned(
            String name,
            int coreHash,
            int roomGridX,
            int roomGridZ,
            RoomType roomType,
            int secrets,
            int crypts
        ) {
            return new LearnRoomResult(
                true,
                "Raum gespeichert: \"" + name + "\" " + roomType.name()
                    + " secrets=" + secrets
                    + (crypts > 0 ? " crypts=" + crypts : "")
                    + " hash=" + coreHash
                    + " roomGrid=" + roomGridX + "," + roomGridZ + ".",
                name,
                coreHash,
                roomGridX,
                roomGridZ,
                roomType,
                secrets,
                crypts
            );
        }

        static LearnRoomResult learnedMulti(
            String name,
            int coreHash,
            int roomGridX,
            int roomGridZ,
            RoomType roomType,
            int secrets,
            int crypts,
            int componentCount
        ) {
            return new LearnRoomResult(
                true,
                "Mehrzellen-Raum gespeichert: \"" + name + "\" " + roomType.name()
                    + " secrets=" + secrets
                    + (crypts > 0 ? " crypts=" + crypts : "")
                    + " zellen=" + componentCount
                    + " startHash=" + coreHash
                    + " roomGrid=" + roomGridX + "," + roomGridZ + ".",
                name,
                coreHash,
                roomGridX,
                roomGridZ,
                roomType,
                secrets,
                crypts
            );
        }

        static LearnRoomResult failed(String message) {
            return new LearnRoomResult(false, message, "", 0, 0, 0, RoomType.UNKNOWN, 0, 0);
        }
    }

    public record RoomDataResult(boolean found, String message, String clipboardText) {
        static RoomDataResult found(String message, String clipboardText) {
            return new RoomDataResult(true, message, clipboardText);
        }

        static RoomDataResult failed(String message) {
            return new RoomDataResult(false, message, "");
        }
    }

    private record ExportCell(int dx, int dz, int roomGridX, int roomGridZ, List<ExportHash> hashes) {
    }

    private record ExportHash(String source, int coreHash, int stableCoreHash) {
    }

    private record CurrentRoomCore(
        boolean valid,
        String message,
        int coreHash,
        int stableCoreHash,
        int roomGridX,
        int roomGridZ
    ) {
        static CurrentRoomCore valid(
            int coreHash,
            int stableCoreHash,
            int roomGridX,
            int roomGridZ
        ) {
            return new CurrentRoomCore(
                true,
                "",
                coreHash,
                stableCoreHash,
                roomGridX,
                roomGridZ
            );
        }

        static CurrentRoomCore failed(String message) {
            return new CurrentRoomCore(false, message, 0, 0, 0, 0);
        }
    }
}
