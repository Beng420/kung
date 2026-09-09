package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.KungConfig;

import com.github.beng420.kung.KungMod;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class DungeonScanRecorder {
    private static final long SCAN_INTERVAL_TICKS = 20;
    private static final long DOOR_TITLE_SCAN_INTERVAL_TICKS = 2;
    private static final long CHECKMARK_INTERVAL_TICKS = 5;
    private static final int DOOR_SAMPLE_Y = 69;
    private static final DateTimeFormatter FILE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DungeonScan scanner = new DungeonScan();
    private final DungeonMapSnapshot mapSnapshot = new DungeonMapSnapshot();
    private long lastScanTick;
    private String runTimestamp;
    private int scanNumber;
    private boolean recording;
    private long lastCheckmarkTick = Long.MIN_VALUE;

    public void initializeClient(DungeonStateTracker tracker) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, tracker));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopRecording());
    }

    public DungeonMapSnapshot mapSnapshot() {
        return mapSnapshot;
    }

    public String runTimestamp() {
        return runTimestamp;
    }

    public boolean isRecording() {
        return recording;
    }

    public void scanNow(Minecraft client, DungeonStateTracker tracker) {
        if (client.level == null || client.player == null || !tracker.state().isInDungeon()) {
            return;
        }

        if (!recording) {
            startRecording();
        }

        PlayerScanContext currentPlayerContext = PlayerScanContext.from(client);
        if (!currentPlayerContext.isInsideDungeonGrid()) {
            return;
        }

        mapSnapshot.observePlayerGrid(currentPlayerContext.gridX(), currentPlayerContext.gridZ());
        observeVisibleTeammateRooms(client, tracker.runStats());
        observeMapState(client, tracker.runStats(), tracker.dungeonTick());
        lastScanTick = tracker.dungeonTick();
        recordScan(client, tracker.runStats(), scanner.scan(client.level, mapSnapshot));
    }

    public void restartRecording() {
        recording = false;
        startRecording();
    }

    private void tick(Minecraft client, DungeonStateTracker tracker) {
        if (client.level == null || client.player == null) {
            return;
        }

        if (!tracker.state().isInDungeon()) {
            stopRecording();
            return;
        }

        startRecording();
        PlayerScanContext currentPlayerContext = PlayerScanContext.from(client);
        if (!currentPlayerContext.isInsideDungeonGrid()) {
            return;
        }

        mapSnapshot.observePlayerGrid(currentPlayerContext.gridX(), currentPlayerContext.gridZ());
        observeVisibleTeammateRooms(client, tracker.runStats());
        long nowTick = tracker.dungeonTick();
        observeMapState(client, tracker.runStats(), nowTick);

        long scanInterval = tracker.shouldFastScanDoors() ? DOOR_TITLE_SCAN_INTERVAL_TICKS : SCAN_INTERVAL_TICKS;
        if (nowTick - lastScanTick < scanInterval) {
            return;
        }

        lastScanTick = nowTick;
        List<DungeonScanPoint> points = scanner.scan(client.level, mapSnapshot);
        recordScan(client, tracker.runStats(), points);
    }

    private void startRecording() {
        if (recording) {
            return;
        }

        recording = true;
        runTimestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        scanNumber = 0;
        lastScanTick = 0;
        lastCheckmarkTick = Long.MIN_VALUE;
        mapSnapshot.reset();
    }

    private void stopRecording() {
        if (!recording) {
            return;
        }

        recording = false;
        runTimestamp = null;
        scanNumber = 0;
        lastScanTick = 0;
        lastCheckmarkTick = Long.MIN_VALUE;
        mapSnapshot.reset();
        KungMod.LOGGER.info("Dungeon scan recording stopped.");
    }

    private void recordScan(Minecraft client, DungeonRunStats stats, List<DungeonScanPoint> points) {
        int currentScan = scanNumber++;
        long timestamp = System.currentTimeMillis();
        PlayerScanContext playerContext = PlayerScanContext.from(client);

        mapSnapshot.addScan(currentScan, timestamp, playerContext.gridX(), playerContext.gridZ(), points);
        observeVisibleTeammateRooms(client, stats);
    }

    private void observeMapState(Minecraft client, DungeonRunStats stats, long nowTick) {
        if (nowTick - lastCheckmarkTick < CHECKMARK_INTERVAL_TICKS) {
            return;
        }
        lastCheckmarkTick = nowTick;
        DungeonMapCheckmarkReader.observe(client, mapSnapshot);
        observeMapPlayerRooms(client, stats, nowTick);
    }

    private void observeMapPlayerRooms(Minecraft client, DungeonRunStats stats, long nowTick) {
        MapItemSavedData mapData = DungeonMapItems.mapData(client);
        if (mapData == null || client.player == null) {
            return;
        }

        List<DungeonRunStats.DungeonPlayerSlot> slots = stats.dungeonPlayerSlots(client);
        Set<UUID> assignedSlotUuids = new HashSet<>();
        int teammateSlotCursor = 1;
        for (MapDecoration decoration : mapData.getDecorations()) {
            if (!isPlayerDecoration(decoration)) {
                continue;
            }
            String name = decorationPlayerName(decoration);
            if (name.isEmpty()) {
                SlotNameResult slotName = resolvedDecorationPlayerName(
                    decoration,
                    slots,
                    assignedSlotUuids,
                    teammateSlotCursor
                );
                teammateSlotCursor = slotName.nextTeammateSlotCursor();
                name = slotName.name();
            }

            DungeonMapCheckmarkReader.MapRoom room =
                DungeonMapCheckmarkReader.roomFromDecoration(decoration, client, mapSnapshot);
            if (room == null) {
                continue;
            }
            if (!isAcceptedMapPlayerDecoration(client, stats, name, room.roomGridX(), room.roomGridZ())) {
                continue;
            }

            mapSnapshot.observeMapPlayerRoom(room.roomGridX(), room.roomGridZ());
            if (name.isEmpty()) {
                stats.observeMapPlayerRoom(room.roomGridX(), room.roomGridZ(), nowTick);
            } else {
                stats.observeMapPlayerRoom(name, room.roomGridX(), room.roomGridZ(), nowTick);
            }
        }
    }

    static int doorSampleY() {
        return DOOR_SAMPLE_Y;
    }

    private void observeVisibleTeammateRooms(Minecraft client, DungeonRunStats stats) {
        if (!KungConfig.get().dungeon.playerTrackingEnabled()) {
            return;
        }
        if (client.level == null || client.player == null || client.getConnection() == null) {
            return;
        }

        for (java.util.UUID uuid : stats.knownTrackedPlayerUuids()) {
            if (uuid.equals(client.player.getUUID())) {
                continue;
            }
            if (!(client.level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer player)) {
                continue;
            }
            if (player == client.player || player.isInvisible()) {
                continue;
            }

            DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(player.blockPosition());
            mapSnapshot.observeVisitedRoom(grid.gridX(), grid.gridZ());
        }
    }

    private static boolean isPlayerDecoration(MapDecoration decoration) {
        return decoration.type().equals(MapDecorationTypes.PLAYER)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)
            || decoration.type().equals(MapDecorationTypes.BLUE_MARKER)
            || decoration.type().equals(MapDecorationTypes.FRAME);
    }

    private static String decorationPlayerName(MapDecoration decoration) {
        return decoration.name()
            .map(component -> component.getString().replaceAll("\u00a7.", "").trim())
            .orElse("");
    }

    private static SlotNameResult resolvedDecorationPlayerName(
        MapDecoration decoration,
        List<DungeonRunStats.DungeonPlayerSlot> slots,
        Set<UUID> assignedSlotUuids,
        int teammateSlotCursor
    ) {
        if (decoration.type().equals(MapDecorationTypes.FRAME)) {
            DungeonRunStats.DungeonPlayerSlot slot = playerSlotAt(slots, 0);
            if (slot != null && slot.uuid() != null && assignedSlotUuids.add(slot.uuid())) {
                return new SlotNameResult(slot.name(), teammateSlotCursor);
            }
            return new SlotNameResult("", teammateSlotCursor);
        }

        if (!decoration.type().equals(MapDecorationTypes.BLUE_MARKER)) {
            return new SlotNameResult("", teammateSlotCursor);
        }

        while (teammateSlotCursor < 5) {
            DungeonRunStats.DungeonPlayerSlot slot = playerSlotAt(slots, teammateSlotCursor++);
            if (slot == null || slot.uuid() == null || !assignedSlotUuids.add(slot.uuid())) {
                continue;
            }
            return new SlotNameResult(slot.name(), teammateSlotCursor);
        }
        return new SlotNameResult("", teammateSlotCursor);
    }

    private static DungeonRunStats.DungeonPlayerSlot playerSlotAt(
        List<DungeonRunStats.DungeonPlayerSlot> slots,
        int index
    ) {
        for (DungeonRunStats.DungeonPlayerSlot slot : slots) {
            if (slot.index() == index) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isAcceptedMapPlayerDecoration(
        Minecraft client,
        DungeonRunStats stats,
        String name,
        int roomGridX,
        int roomGridZ
    ) {
        if (!name.isEmpty()) {
            return name.equalsIgnoreCase(client.player.getName().getString()) || stats.isKnownTrackedPlayer(name);
        }
        DungeonScanUtils.GridPosition selfGrid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        if (selfGrid.gridX() == roomGridX && selfGrid.gridZ() == roomGridZ) {
            return true;
        }
        if (client.level == null || client.getConnection() == null) {
            return false;
        }
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == client.player || player.isInvisible() || !stats.isKnownTrackedPlayer(player.getName().getString())) {
                continue;
            }
            if (client.getConnection().getPlayerInfo(player.getUUID()) == null) {
                continue;
            }
            DungeonScanUtils.GridPosition playerGrid = DungeonScanUtils.getRoomGridPosition(player.blockPosition());
            if (playerGrid.gridX() == roomGridX && playerGrid.gridZ() == roomGridZ) {
                return true;
            }
        }
        return false;
    }

    private record PlayerScanContext(int blockX, int blockY, int blockZ, int gridX, int gridZ) {
        static PlayerScanContext from(Minecraft client) {
            BlockPos position = client.player.blockPosition();
            DungeonScanUtils.GridPosition gridPosition = DungeonScanUtils.getRoomGridPosition(position);
            return new PlayerScanContext(
                position.getX(),
                position.getY(),
                position.getZ(),
                gridPosition.gridX(),
                gridPosition.gridZ()
            );
        }

        boolean isInsideDungeonGrid() {
            return gridX >= 0
                && gridZ >= 0
                && gridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
                && gridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
        }
    }

    private record SlotNameResult(String name, int nextTeammateSlotCursor) {
    }
}
