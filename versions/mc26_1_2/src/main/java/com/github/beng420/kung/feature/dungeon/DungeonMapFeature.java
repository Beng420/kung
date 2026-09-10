package com.github.beng420.kung.feature.dungeon;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

public final class DungeonMapFeature extends ConfigurableFeature<DungeonConfig> implements Feature {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "dungeon_map_overlay");
    private static final int ROOM_SIZE = 19;
    private static final int DOOR_SIZE = 6;
    private static final int CELL_GAP = 1;
    private static final int GRID_UNITS = DungeonScanUtils.SCAN_GRID_SIZE;
    private static final int GRID_PIXEL_SIZE = scanGridToPixel(GRID_UNITS);
    private static final int HEADER_HEIGHT = 0;
    private static final int LEGEND_HEIGHT = 38;
    private static final int PLAYER_HEAD_SIZE = 10;
    private static final int TEAMMATE_HEAD_SIZE = 8;
    private static final float ROOM_LABEL_SCALE = 0.56F;
    private static final float ROOM_SECRET_SCALE = 0.54F;
    private static final float PRINCE_ICON_SCALE = 0.62F;
    private static final int ROOM_TEXT_LINE_STEP = 5;
    private static final int MAX_ROOM_LABEL_LINES = 3;
    private static final int LONG_WORD_SPLIT_MIN_CHARS = 12;
    private static final int MAX_LABEL_LINE_CACHE_ENTRIES = 512;
    private static final int MAX_PLAYER_MARKERS = 5;
    private static final long PLAYER_MARKER_LOG_INTERVAL_MILLIS = 2_000L;
    private static final double PLAYER_MARKER_SMOOTHING = 18.0;
    private static final int PLAYER_MARKER_SNAP_DISTANCE = 18;

    private static final int BACKGROUND = 0x00000000;
    private static final int PANEL = 0x5514171C;
    private static final int EMPTY = 0x882A2E36;
    private static final int UNSEEN = 0x66111318;
    private static final int MAP_BORDER = 0xFF9AA0A8;
    private static final int OPEN_DOOR = 0xFF82511F;
    private static final int WITHER_DOOR = 0xFF050506;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SECRET_FOUND_TEXT = 0xFF55FFFF;
    private static final int SECRET_TARGET_TEXT = 0xFFFFFF55;
    private static final int SECRET_FULL_TEXT = 0xFFFF5555;
    private static final int GOOD_TEXT = 0xFF55FF55;
    private static final int BAD_TEXT = 0xFFFF5555;
    private static final int COMPLETED_TEXT = 0xFF55FF55;
    private static final int MUTED_TEXT = 0xFF7F8790;
    private static final int MIMIC_ROOM_OUTLINE = 0xFFFF3333;
    private static final int MIMIC_ROOM_GLOW = 0x44FF3333;
    private static final int UNKNOWN_CLASS_BORDER = 0xFFE9EDF2;
    private static final int MAX_UNOPENED_ALPHA = 28;
    private static final int FOOTER_HEIGHT = 26;
    private static final float FOOTER_TEXT_SCALE = 0.72F;
    private static final Map<String, SmoothedMarker> SMOOTHED_PLAYER_MARKERS = new HashMap<>();
    private static final Map<UUID, PlayerSkin> LAST_PLAYER_SKINS = new HashMap<>();
    private static final Map<LabelLineKey, List<String>> LABEL_LINE_CACHE = new HashMap<>();
    private static long playerMarkerFrame;
    private static long lastPlayerMarkerLogMillis;
    private static String lastPlayerMarkerLogState = "";
    private final DungeonStateTracker dungeonStateTracker;

    public DungeonMapFeature(DungeonStateTracker dungeonStateTracker) {
        super(config -> config.dungeon);
        this.dungeonStateTracker = java.util.Objects.requireNonNull(dungeonStateTracker);
    }

    @Override
    protected void onInitialize() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.PLAYER_LIST,
            HUD_ID,
            (graphics, deltaTracker) -> render(graphics, deltaTracker, dungeonStateTracker)
        );
    }

    @Override
    public boolean isEnabled() {
        return config().enabled();
    }

    private void render(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        DungeonStateTracker dungeonStateTracker
    ) {
        if (!config().enabled() || !dungeonStateTracker.isInDungeonArea()) return;
        long started = System.nanoTime();
        try {
            renderUnsafe(graphics, deltaTracker, dungeonStateTracker);
        } catch (RuntimeException | LinkageError exception) {
            dungeonStateTracker.reportRunError(Minecraft.getInstance(), "Map render", exception);
            KungMod.LOGGER.warn("Failed to render dungeon map overlay.", exception);
        } finally {
            DungeonTimings.record("map-render", started);
        }
    }

    private void renderUnsafe(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        DungeonStateTracker dungeonStateTracker
    ) {
        DungeonConfig config = config();
        if (!config.enabled() || !dungeonStateTracker.isInDungeonArea()) {
            return;
        }

        DungeonMapSnapshot snapshot = dungeonStateTracker.mapSnapshot();
        DungeonLiveMapWriter.MatchRenderPlan renderPlan = dungeonStateTracker.renderPlan();
        float scale = effectiveScale(config);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(config.x(), config.y());
            graphics.pose().scale(scale, scale);
            int left = 0;
            int top = 0;
            int gridTop = top;
            drawGrid(
                graphics,
                left,
                gridTop,
                snapshot,
                renderPlan,
                dungeonStateTracker.runStats(),
                dungeonStateTracker.isRecording(),
                renderPartialTick()
            );
            if (config.showLegend()) {
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(left, gridTop + GRID_PIXEL_SIZE + 5);
                    graphics.pose().scale(textScale(config), textScale(config));
                    drawLegend(graphics, 0, 0);
                } finally {
                    graphics.pose().popMatrix();
                }
            }
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(left, gridTop + footerTop(config));
                graphics.pose().scale(textScale(config), textScale(config));
                drawFooter(graphics, 0, 0, dungeonStateTracker.runStats(), snapshot, renderPlan);
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawGrid(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonRunStats stats,
        boolean recording,
        float partialTick
    ) {
        drawBorder(graphics, left - 3, top - 3, GRID_PIXEL_SIZE + 6, GRID_PIXEL_SIZE + 6, MAP_BORDER);

        GridViewport viewport = GridViewport.from(snapshot, renderPlan, stats);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(left + viewport.offsetX(), top + viewport.offsetY());
            graphics.pose().scale(viewport.scale(), viewport.scale());
            graphics.pose().translate(-viewport.minPixelX(), -viewport.minPixelY());
            drawGridContent(graphics, snapshot, renderPlan, stats, viewport);
            drawMimicRoomHighlights(graphics, snapshot, renderPlan, stats, viewport);
            if (!recording) {
                drawPreRunStartRoom(graphics, 0, 0);
            }
            drawRoomLabels(graphics, snapshot, renderPlan, stats, viewport);
            drawPlayerMarkers(graphics, 0, 0, snapshot, stats, partialTick);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        fill(graphics, x, y, x + width, y + 1, color);
        fill(graphics, x, y + height - 1, x + width, y + height, color);
        fill(graphics, x, y, x + 1, y + height, color);
        fill(graphics, x + width - 1, y, x + width, y + height, color);
    }

    private static void drawGridContent(
        GuiGraphicsExtractor graphics,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonRunStats stats,
        GridViewport viewport
    ) {
        for (int gridZ = 0; gridZ < DungeonScanUtils.SCAN_GRID_SIZE; gridZ++) {
            for (int gridX = 0; gridX < DungeonScanUtils.SCAN_GRID_SIZE; gridX++) {
                if (!viewport.containsScanCell(gridX, gridZ)) {
                    continue;
                }
                if (DungeonScanUtils.isRoomScanPoint(gridX, gridZ)
                    && renderPlan.isMatchedRoomCell(gridX / 2, gridZ / 2)) {
                    continue;
                }

                int x = scanGridToPixel(gridX);
                int y = scanGridToPixel(gridZ);
                int size = sizeFor(gridX, gridZ);
                if (DungeonScanUtils.isDoorScanPoint(gridX, gridZ)
                    && renderPlan.isInternalDoor(gridX, gridZ)) {
                    continue;
                }

                drawCell(graphics, snapshot, renderPlan, gridX, gridZ, x, y, size);
            }
        }

        drawMatchedRooms(graphics, 0, 0, renderPlan, viewport);
        drawExternalDoors(graphics, 0, 0, renderPlan.externalDoors(), viewport);
    }

    private static void drawMimicRoomHighlights(
        GuiGraphicsExtractor graphics,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonRunStats stats,
        GridViewport viewport
    ) {
        if (!KungConfig.get().dungeon.mimicEspEnabled()
            || stats == null
            || stats.mimicKilled()
            || snapshot.mimicRooms().isEmpty()) {
            return;
        }

        Map<String, Set<DungeonLiveMapWriter.CellKey>> highlightedGroups = new HashMap<>();
        for (DungeonMapSnapshot.GridKey mimicRoom : snapshot.mimicRooms()) {
            DungeonLiveMapWriter.CellKey cell = new DungeonLiveMapWriter.CellKey(mimicRoom.gridX(), mimicRoom.gridZ());
            if (renderPlan.roomTypeAt(cell.x(), cell.z()) == RoomType.TRAP) {
                continue;
            }
            Set<DungeonLiveMapWriter.CellKey> roomCells = sameOwnedRoomCells(renderPlan, cell);
            String groupKey = renderPlan.roomOwners().getOrDefault(cell, "single:" + cell.x() + "," + cell.z());
            highlightedGroups.computeIfAbsent(groupKey, ignored -> new HashSet<>()).addAll(roomCells);
        }

        for (Set<DungeonLiveMapWriter.CellKey> cells : highlightedGroups.values()) {
            if (containsVisibleRoomCell(cells, viewport)) {
                drawMimicRoomShape(graphics, cells);
            }
        }
    }

    private static boolean containsVisibleRoomCell(Set<DungeonLiveMapWriter.CellKey> cells, GridViewport viewport) {
        for (DungeonLiveMapWriter.CellKey cell : cells) {
            if (viewport.containsScanCell(cell.x() * 2, cell.z() * 2)) {
                return true;
            }
        }
        return false;
    }

    private static Set<DungeonLiveMapWriter.CellKey> sameOwnedRoomCells(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonLiveMapWriter.CellKey cell
    ) {
        String owner = renderPlan.roomOwners().get(cell);
        if (owner != null) {
            Set<DungeonLiveMapWriter.CellKey> cells = new HashSet<>();
            for (Map.Entry<DungeonLiveMapWriter.CellKey, String> entry : renderPlan.roomOwners().entrySet()) {
                if (owner.equals(entry.getValue())) {
                    cells.add(entry.getKey());
                }
            }
            return cells;
        }

        DungeonKnownRoomCatalog.MatchedRoom match = matchContaining(renderPlan, cell.x(), cell.z());
        if (match == null) {
            return Set.of(cell);
        }

        Set<DungeonLiveMapWriter.CellKey> cells = new HashSet<>();
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            cells.add(new DungeonLiveMapWriter.CellKey(component.roomGridX(), component.roomGridZ()));
        }
        return cells;
    }

    private static void drawMimicRoomShape(
        GuiGraphicsExtractor graphics,
        Set<DungeonLiveMapWriter.CellKey> roomCells
    ) {
        Set<DungeonLiveMapWriter.CellKey> occupiedScanCells = occupiedScanCellsForRoomShape(roomCells);
        for (DungeonLiveMapWriter.CellKey scanCell : occupiedScanCells) {
            Rect rect = expandedRectForScanCell(scanCell.x(), scanCell.z());
            fill(graphics, rect.x(), rect.y(), rect.right(), rect.bottom(), MIMIC_ROOM_GLOW);
        }
        for (DungeonLiveMapWriter.CellKey scanCell : occupiedScanCells) {
            drawMimicPerimeterEdges(graphics, occupiedScanCells, scanCell);
        }
    }

    private static Set<DungeonLiveMapWriter.CellKey> occupiedScanCellsForRoomShape(
        Set<DungeonLiveMapWriter.CellKey> roomCells
    ) {
        Set<DungeonLiveMapWriter.CellKey> occupied = new HashSet<>();
        for (DungeonLiveMapWriter.CellKey roomCell : roomCells) {
            occupied.add(new DungeonLiveMapWriter.CellKey(roomCell.x() * 2, roomCell.z() * 2));
            DungeonLiveMapWriter.CellKey right = new DungeonLiveMapWriter.CellKey(roomCell.x() + 1, roomCell.z());
            DungeonLiveMapWriter.CellKey down = new DungeonLiveMapWriter.CellKey(roomCell.x(), roomCell.z() + 1);
            if (roomCells.contains(right)) {
                occupied.add(new DungeonLiveMapWriter.CellKey(roomCell.x() * 2 + 1, roomCell.z() * 2));
            }
            if (roomCells.contains(down)) {
                occupied.add(new DungeonLiveMapWriter.CellKey(roomCell.x() * 2, roomCell.z() * 2 + 1));
            }
        }

        for (DungeonLiveMapWriter.CellKey roomCell : roomCells) {
            if (roomCells.contains(new DungeonLiveMapWriter.CellKey(roomCell.x() + 1, roomCell.z()))
                && roomCells.contains(new DungeonLiveMapWriter.CellKey(roomCell.x(), roomCell.z() + 1))
                && roomCells.contains(new DungeonLiveMapWriter.CellKey(roomCell.x() + 1, roomCell.z() + 1))) {
                occupied.add(new DungeonLiveMapWriter.CellKey(roomCell.x() * 2 + 1, roomCell.z() * 2 + 1));
            }
        }
        return occupied;
    }

    private static void drawMimicPerimeterEdges(
        GuiGraphicsExtractor graphics,
        Set<DungeonLiveMapWriter.CellKey> occupiedScanCells,
        DungeonLiveMapWriter.CellKey scanCell
    ) {
        Rect rect = expandedRectForScanCell(scanCell.x(), scanCell.z());
        if (!occupiedScanCells.contains(new DungeonLiveMapWriter.CellKey(scanCell.x(), scanCell.z() - 1))) {
            fill(graphics, rect.x(), rect.y() - 1, rect.right(), rect.y() + 1, MIMIC_ROOM_OUTLINE);
        }
        if (!occupiedScanCells.contains(new DungeonLiveMapWriter.CellKey(scanCell.x(), scanCell.z() + 1))) {
            fill(graphics, rect.x(), rect.bottom() - 1, rect.right(), rect.bottom() + 1, MIMIC_ROOM_OUTLINE);
        }
        if (!occupiedScanCells.contains(new DungeonLiveMapWriter.CellKey(scanCell.x() - 1, scanCell.z()))) {
            fill(graphics, rect.x() - 1, rect.y(), rect.x() + 1, rect.bottom(), MIMIC_ROOM_OUTLINE);
        }
        if (!occupiedScanCells.contains(new DungeonLiveMapWriter.CellKey(scanCell.x() + 1, scanCell.z()))) {
            fill(graphics, rect.right() - 1, rect.y(), rect.right() + 1, rect.bottom(), MIMIC_ROOM_OUTLINE);
        }
    }

    private static Rect expandedRectForScanCell(int gridX, int gridZ) {
        int x = scanGridToPixel(gridX);
        int y = scanGridToPixel(gridZ);
        int width = sizeFor(gridX, gridZ);
        int height = sizeFor(gridX, gridZ);
        if (DungeonScanUtils.isSeparatorScanPoint(gridX, gridZ)) {
            return new Rect(x - CELL_GAP, y - CELL_GAP, width + CELL_GAP * 2, height + CELL_GAP * 2);
        }
        if (DungeonScanUtils.isDoorScanPoint(gridX, gridZ)) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return new Rect(x - CELL_GAP, y, DOOR_SIZE + CELL_GAP * 2, ROOM_SIZE);
            }
            return new Rect(x, y - CELL_GAP, ROOM_SIZE, DOOR_SIZE + CELL_GAP * 2);
        }
        return new Rect(x, y, width, height);
    }

    private static DungeonKnownRoomCatalog.MatchedRoom matchContaining(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        int roomGridX,
        int roomGridZ
    ) {
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            if (match.contains(roomGridX, roomGridZ)) {
                return match;
            }
        }
        return null;
    }

    private static void drawRoomLabels(
        GuiGraphicsExtractor graphics,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonRunStats stats,
        GridViewport viewport
    ) {
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            if (viewport.containsMatch(match)) {
                drawMatchedRoomLabel(graphics, 0, 0, match, stats, renderPlan);
            }
        }

        for (int gridZ = 0; gridZ < DungeonScanUtils.SCAN_GRID_SIZE; gridZ += 2) {
            for (int gridX = 0; gridX < DungeonScanUtils.SCAN_GRID_SIZE; gridX += 2) {
                if (!viewport.containsScanCell(gridX, gridZ)
                    || renderPlan.isMatchedRoomCell(gridX / 2, gridZ / 2)) {
                    continue;
                }
                drawHintLabel(graphics, snapshot, renderPlan, stats, gridX, gridZ);
            }
        }
    }

    private static void drawPreRunStartRoom(GuiGraphicsExtractor graphics, int left, int top) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        DungeonScanUtils.GridPosition playerGrid =
            DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        if (!validRoomGrid(playerGrid.gridX(), playerGrid.gridZ())) {
            return;
        }

        int x = left + scanGridToPixel(playerGrid.gridX() * 2);
        int y = top + scanGridToPixel(playerGrid.gridZ() * 2);
        fill(graphics, x, y, x + ROOM_SIZE, y + ROOM_SIZE, RoomType.START.color());
    }

    private static void drawMatchedRooms(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        GridViewport viewport
    ) {
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            if (!viewport.containsMatch(match)) {
                continue;
            }
            int roomColor = match.template().type().color();

            for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                int x = left + scanGridToPixel(component.roomGridX() * 2);
                int y = top + scanGridToPixel(component.roomGridZ() * 2);
                drawRoomFill(
                    graphics,
                    x,
                    y,
                    ROOM_SIZE,
                    ROOM_SIZE,
                    roomColor,
                    renderPlan.isVisitedRoom(component.roomGridX(), component.roomGridZ())
                );
            }

            drawInternalRoomConnections(graphics, left, top, renderPlan, match, roomColor);
            drawInternalRoomCorners(graphics, left, top, renderPlan, match, roomColor);
        }
    }

    private static void drawRoomFill(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        int roomColor,
        boolean visited
    ) {
        fill(graphics, x, y, x + width, y + height, visited ? roomColor : unopenedRoomColor(roomColor));
        if (!visited) {
            fill(graphics, 
                x,
                y,
                x + width,
                y + height,
                alphaBlack(effectiveUnopenedRoomAlpha())
            );
        }
    }

    private static int unopenedRoomColor(int roomColor) {
        return blendRgb(roomColor, 0xFF747474, 0.34F);
    }

    private static int blendRgb(int firstColor, int secondColor, float secondWeight) {
        float weight = Math.clamp(secondWeight, 0.0F, 1.0F);
        float firstWeight = 1.0F - weight;
        int red = Math.round(((firstColor >> 16) & 0xFF) * firstWeight + ((secondColor >> 16) & 0xFF) * weight);
        int green = Math.round(((firstColor >> 8) & 0xFF) * firstWeight + ((secondColor >> 8) & 0xFF) * weight);
        int blue = Math.round((firstColor & 0xFF) * firstWeight + (secondColor & 0xFF) * weight);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int effectiveUnopenedRoomAlpha() {
        return Math.min(KungConfig.get().dungeon.unopenedRoomAlpha(), MAX_UNOPENED_ALPHA);
    }

    private static void drawInternalRoomConnections(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonKnownRoomCatalog.MatchedRoom match,
        int roomColor
    ) {
        for (DungeonKnownRoomCatalog.MatchedComponent first : match.components()) {
            for (DungeonKnownRoomCatalog.MatchedComponent second : match.components()) {
                int distanceX = Math.abs(first.roomGridX() - second.roomGridX());
                int distanceZ = Math.abs(first.roomGridZ() - second.roomGridZ());
                if (distanceX + distanceZ != 1) {
                    continue;
                }

                int doorGridX = first.roomGridX() + second.roomGridX();
                int doorGridZ = first.roomGridZ() + second.roomGridZ();
                int x = left + scanGridToPixel(doorGridX);
                int y = top + scanGridToPixel(doorGridZ);
                boolean visited = renderPlan.isVisitedRoom(first.roomGridX(), first.roomGridZ())
                    || renderPlan.isVisitedRoom(second.roomGridX(), second.roomGridZ());
                if (isHorizontalDoor(doorGridX, doorGridZ)) {
                    drawRoomFill(graphics, x - CELL_GAP, y, DOOR_SIZE + CELL_GAP * 2, ROOM_SIZE, roomColor, visited);
                } else {
                    drawRoomFill(graphics, x, y - CELL_GAP, ROOM_SIZE, DOOR_SIZE + CELL_GAP * 2, roomColor, visited);
                }
            }
        }
    }

    private static void drawInternalRoomCorners(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonKnownRoomCatalog.MatchedRoom match,
        int roomColor
    ) {
        for (int roomGridZ = 0; roomGridZ < DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX < DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                int filledCorners = 0;
                filledCorners += match.contains(roomGridX, roomGridZ) ? 1 : 0;
                filledCorners += match.contains(roomGridX + 1, roomGridZ) ? 1 : 0;
                filledCorners += match.contains(roomGridX, roomGridZ + 1) ? 1 : 0;
                filledCorners += match.contains(roomGridX + 1, roomGridZ + 1) ? 1 : 0;
                if (filledCorners != 4) {
                    continue;
                }

                int separatorGridX = roomGridX * 2 + 1;
                int separatorGridZ = roomGridZ * 2 + 1;
                int x = left + scanGridToPixel(separatorGridX);
                int y = top + scanGridToPixel(separatorGridZ);
                boolean visited = renderPlan.isVisitedRoom(roomGridX, roomGridZ)
                    || renderPlan.isVisitedRoom(roomGridX + 1, roomGridZ)
                    || renderPlan.isVisitedRoom(roomGridX, roomGridZ + 1)
                    || renderPlan.isVisitedRoom(roomGridX + 1, roomGridZ + 1);
                drawRoomFill(
                    graphics,
                    x - CELL_GAP,
                    y - CELL_GAP,
                    DOOR_SIZE + CELL_GAP * 2,
                    DOOR_SIZE + CELL_GAP * 2,
                    roomColor,
                    visited
                );
            }
        }
    }

    private static void drawMatchedRoomLabel(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonKnownRoomCatalog.MatchedRoom match,
        DungeonRunStats stats,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        if (!shouldDrawRoomText(match.template().type())) {
            return;
        }

        LabelPlacement placement = labelPlacementFor(match, left, top);
        int secrets = match.template().secrets();
        int secretsFound = secretsFoundFor(match, stats, renderPlan);
        boolean visited = false;
        boolean cleared = false;
        boolean completed = false;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            visited = visited || renderPlan.isVisitedRoom(component.roomGridX(), component.roomGridZ());
            cleared = cleared || renderPlan.isClearedRoom(component.roomGridX(), component.roomGridZ());
            completed = completed || renderPlan.isCompletedRoom(component.roomGridX(), component.roomGridZ());
        }
        if (completed) {
            secretsFound = secrets;
        }
        RoomTextState textState = roomTextState(match.template().type(), visited, cleared, completed, secretsFound, secrets);
        drawRoomText(
            graphics,
            placement.centerX(),
            placement.centerY(),
            placement.maxWidth(),
            match.template().name(),
            match.template().type(),
            secretsFound,
            secrets,
            match.template().crypts(),
            textState,
            DungeonRoomDebugFormatter.matchOverlayLines(match, renderPlan)
        );
        drawPrinceIcon(graphics, roomBounds(match), stats, match.template().prince());
    }

    private static int secretsFoundFor(
        DungeonKnownRoomCatalog.MatchedRoom match,
        DungeonRunStats stats,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        int localTotal = 0;
        int remoteBest = 0;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            localTotal += stats.roomSecretsFound(component.roomGridX(), component.roomGridZ());
            remoteBest = Math.max(
                remoteBest,
                renderPlan.remoteRoomSecretsFoundOnly(component.roomGridX(), component.roomGridZ())
            );
        }
        return Math.min(Math.max(localTotal, remoteBest), Math.max(0, match.template().secrets()));
    }

    private static LabelPlacement labelPlacementFor(
        DungeonKnownRoomCatalog.MatchedRoom match,
        int left,
        int top
    ) {
        RoomBounds bounds = roomBounds(match);
        if ((match.components().size() == 2
                && ((bounds.spanX() == 2 && bounds.spanZ() == 1)
                    || (bounds.spanX() == 1 && bounds.spanZ() == 2)))
            || (match.components().size() == 4
            && ((bounds.spanX() == 2 && bounds.spanZ() == 2)
                || (bounds.spanX() == 4 && bounds.spanZ() == 1)
                || (bounds.spanX() == 1 && bounds.spanZ() == 4)))) {
            return boundsLabelPlacement(bounds, left, top);
        }

        DungeonKnownRoomCatalog.MatchedComponent labelComponent = labelComponentFor(match);
        int cellX = left + scanGridToPixel(labelComponent.roomGridX() * 2);
        int cellY = top + scanGridToPixel(labelComponent.roomGridZ() * 2);
        return new LabelPlacement(
            cellX + ROOM_SIZE / 2,
            cellY + ROOM_SIZE / 2,
            labelMaxWidthFor(bounds)
        );
    }

    private static LabelPlacement boundsLabelPlacement(RoomBounds bounds, int left, int top) {
        int minPixelX = left + scanGridToPixel(bounds.minX() * 2);
        int maxPixelX = left + scanGridToPixel(bounds.maxX() * 2) + ROOM_SIZE;
        int minPixelY = top + scanGridToPixel(bounds.minZ() * 2);
        int maxPixelY = top + scanGridToPixel(bounds.maxZ() * 2) + ROOM_SIZE;
        return new LabelPlacement(
            (minPixelX + maxPixelX) / 2,
            (minPixelY + maxPixelY) / 2,
            labelMaxWidthFor(bounds)
        );
    }

    private static int labelMaxWidthFor(RoomBounds bounds) {
        int spanX = Math.max(1, bounds.spanX());
        int width = ROOM_SIZE * spanX + (spanX - 1) * (DOOR_SIZE + CELL_GAP);
        return Math.max(ROOM_SIZE - 3, width - 4);
    }

    private static RoomBounds roomBounds(DungeonKnownRoomCatalog.MatchedRoom match) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            minX = Math.min(minX, component.roomGridX());
            maxX = Math.max(maxX, component.roomGridX());
            minZ = Math.min(minZ, component.roomGridZ());
            maxZ = Math.max(maxZ, component.roomGridZ());
        }
        return new RoomBounds(minX, maxX, minZ, maxZ);
    }

    private static DungeonKnownRoomCatalog.MatchedComponent labelComponentFor(
        DungeonKnownRoomCatalog.MatchedRoom match
    ) {
        DungeonKnownRoomCatalog.MatchedComponent corner = lShapeCornerComponent(match);
        if (corner != null) {
            return corner;
        }

        double averageX = 0;
        double averageZ = 0;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            averageX += component.roomGridX();
            averageZ += component.roomGridZ();
        }
        averageX /= match.components().size();
        averageZ /= match.components().size();

        DungeonKnownRoomCatalog.MatchedComponent best = match.components().getFirst();
        double bestDistance = Double.MAX_VALUE;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            double distanceX = component.roomGridX() - averageX;
            double distanceZ = component.roomGridZ() - averageZ;
            double distance = distanceX * distanceX + distanceZ * distanceZ;
            if (distance < bestDistance
                || (distance == bestDistance && component.roomGridZ() > best.roomGridZ())
                || (distance == bestDistance
                    && component.roomGridZ() == best.roomGridZ()
                    && component.roomGridX() > best.roomGridX())) {
                best = component;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static DungeonKnownRoomCatalog.MatchedComponent lShapeCornerComponent(
        DungeonKnownRoomCatalog.MatchedRoom match
    ) {
        RoomBounds bounds = roomBounds(match);
        if (match.components().size() != 3 || bounds.spanX() != 2 || bounds.spanZ() != 2) {
            return null;
        }

        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            int neighbors = 0;
            for (DungeonKnownRoomCatalog.MatchedComponent other : match.components()) {
                int distanceX = Math.abs(component.roomGridX() - other.roomGridX());
                int distanceZ = Math.abs(component.roomGridZ() - other.roomGridZ());
                if (distanceX + distanceZ == 1) {
                    neighbors++;
                }
            }
            if (neighbors == 2) {
                return component;
            }
        }
        return null;
    }

    private static void drawExternalDoors(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        Map<DungeonLiveMapWriter.CellKey, DungeonLiveMapWriter.DoorRenderInfo> doors,
        GridViewport viewport
    ) {
        for (Map.Entry<DungeonLiveMapWriter.CellKey, DungeonLiveMapWriter.DoorRenderInfo> entry : doors.entrySet()) {
            DungeonLiveMapWriter.CellKey door = entry.getKey();
            if (!viewport.containsScanCell(door.x(), door.z())) {
                continue;
            }
            drawDoorConnection(graphics, left, top, door.x(), door.z(), colorForDoor(entry.getValue()));
        }
    }

    private static int colorForDoor(DungeonLiveMapWriter.DoorRenderInfo door) {
        int color;
        if (door.colorAsSpecial()) {
            color = door.targetType().color();
        } else if (door.kind() == DungeonDoorKind.OPEN) {
            color = OPEN_DOOR;
        } else {
            color = switch (door.kind()) {
                case WITHER -> WITHER_DOOR;
                case BLOOD -> RoomType.BLOOD.color();
                default -> PANEL;
            };
        }

        if (door.colorAsSpecial()
            || door.kind() == DungeonDoorKind.BLOOD
            || door.kind() == DungeonDoorKind.WITHER) {
            return color;
        }
        return door.targetVisited() ? color : unopenedRoomColor(color);
    }

    private static void drawDoorConnection(GuiGraphicsExtractor graphics, int left, int top, int gridX, int gridZ, int color) {
        int x = left + scanGridToPixel(gridX);
        int y = top + scanGridToPixel(gridZ);

        if (isHorizontalDoor(gridX, gridZ)) {
            int centeredY = y + (ROOM_SIZE - DOOR_SIZE) / 2;
            fill(graphics, x - CELL_GAP, centeredY, x + DOOR_SIZE + CELL_GAP, centeredY + DOOR_SIZE, color);
            return;
        }

        int centeredX = x + (ROOM_SIZE - DOOR_SIZE) / 2;
        fill(graphics, centeredX, y - CELL_GAP, centeredX + DOOR_SIZE, y + DOOR_SIZE + CELL_GAP, color);
    }

    private static void drawCell(
        GuiGraphicsExtractor graphics,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        int gridX,
        int gridZ,
        int x,
        int y,
        int size
    ) {
        DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(gridX, gridZ);
        if (observedPoint == null) {
            DungeonKnownRoomCatalog.KnownCoreHint remoteHint = DungeonScanUtils.isRoomScanPoint(gridX, gridZ)
                ? renderPlan.hintAt(gridX / 2, gridZ / 2)
                : null;
            if (remoteHint != null) {
                drawRoomFill(
                    graphics,
                    x,
                    y,
                    size,
                    size,
                    remoteHint.type().color(),
                    renderPlan.isVisitedRoom(gridX / 2, gridZ / 2)
                );
            } else if (DungeonScanUtils.isRoomScanPoint(gridX, gridZ)
                && renderPlan.isRemoteRoom(gridX / 2, gridZ / 2)) {
                fill(graphics, x, y, x + size, y + size, RoomType.UNKNOWN.color());
                drawCenteredText(graphics, "?", x + size / 2, y + size / 2 - 4, MUTED_TEXT, false);
            }
            return;
        }

        DungeonScanPoint point = observedPoint.point();
        if (point.kind() == DungeonScanPointKind.SEPARATOR || point.kind() == DungeonScanPointKind.DOOR) {
            return;
        }

        if (DungeonRoomClassifier.isEmptyCore(point.coreHash())) {
            return;
        }

        DungeonKnownRoomCatalog.KnownCoreHint hint = renderPlan.hintAt(gridX / 2, gridZ / 2);
        if (hint != null) {
            int roomColor = hint.type().color();
            drawRoomFill(
                graphics,
                x,
                y,
                size,
                size,
                roomColor,
                renderPlan.isVisitedRoom(gridX / 2, gridZ / 2)
            );
            return;
        }

        RoomType roomType = snapshot.isStartRoom(gridX, gridZ) ? RoomType.START : RoomType.UNKNOWN;
        if (roomType == RoomType.UNKNOWN) {
            drawCenteredText(graphics, "?", x + size / 2, y + size / 2 - 4, MUTED_TEXT, false);
            return;
        }

        fill(graphics, x, y, x + size, y + size, roomType.color());
    }

    private static void drawHintLabel(
        GuiGraphicsExtractor graphics,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        DungeonRunStats stats,
        int gridX,
        int gridZ
    ) {
        DungeonKnownRoomCatalog.KnownCoreHint hint = renderPlan.hintAt(gridX / 2, gridZ / 2);
        if (hint == null || !shouldDrawRoomText(hint.type())) {
            return;
        }

        int roomGridX = gridX / 2;
        int roomGridZ = gridZ / 2;
        int secrets = renderPlan.remoteRoomSecretsMax(roomGridX, roomGridZ, hint.secrets());
        int secretsFound = renderPlan.isCompletedRoom(roomGridX, roomGridZ)
            ? secrets
            : renderPlan.remoteRoomSecretsFound(roomGridX, roomGridZ, stats.roomSecretsFound(roomGridX, roomGridZ));
        DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(gridX, gridZ);
        int x = scanGridToPixel(gridX);
        int y = scanGridToPixel(gridZ);
        drawRoomText(
            graphics,
            x + ROOM_SIZE / 2,
            y + ROOM_SIZE / 2,
            ROOM_SIZE - 3,
            hint.name(),
            hint.type(),
            secretsFound,
            secrets,
            hint.crypts(),
            roomTextState(
                hint.type(),
                renderPlan.isVisitedRoom(roomGridX, roomGridZ),
                renderPlan.isClearedRoom(roomGridX, roomGridZ),
                renderPlan.isCompletedRoom(roomGridX, roomGridZ),
                secretsFound,
                secrets
            ),
            observedPoint == null
                ? List.of()
                : DungeonRoomDebugFormatter.hintOverlayLines(observedPoint.point(), roomGridX, roomGridZ)
        );
        drawPrinceIcon(graphics, roomGridX, roomGridZ, stats, hint.prince());
    }

    private static void drawPrinceIcon(
        GuiGraphicsExtractor graphics,
        RoomBounds bounds,
        DungeonRunStats stats,
        boolean hasPrince
    ) {
        int minPixelX = scanGridToPixel(bounds.minX() * 2);
        int maxPixelX = scanGridToPixel(bounds.maxX() * 2) + ROOM_SIZE;
        int minPixelY = scanGridToPixel(bounds.minZ() * 2);
        int maxPixelY = scanGridToPixel(bounds.maxZ() * 2) + ROOM_SIZE;
        drawPrinceIcon(graphics, minPixelX, minPixelY, maxPixelX, maxPixelY, stats, hasPrince);
    }

    private static void drawPrinceIcon(
        GuiGraphicsExtractor graphics,
        int roomGridX,
        int roomGridZ,
        DungeonRunStats stats,
        boolean hasPrince
    ) {
        int minPixelX = scanGridToPixel(roomGridX * 2);
        int minPixelY = scanGridToPixel(roomGridZ * 2);
        drawPrinceIcon(graphics, minPixelX, minPixelY, minPixelX + ROOM_SIZE, minPixelY + ROOM_SIZE, stats, hasPrince);
    }

    private static void drawPrinceIcon(
        GuiGraphicsExtractor graphics,
        int minPixelX,
        int minPixelY,
        int maxPixelX,
        int maxPixelY,
        DungeonRunStats stats,
        boolean hasPrince
    ) {
        if (!KungConfig.get().dungeon.princeIconsEnabled()
            || !hasPrince) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        String icon = "P";
        float iconScale = PRINCE_ICON_SCALE * textScale(KungConfig.get().dungeon);
        int width = Math.round(client.font.width(icon) * iconScale);
        int height = Math.round(client.font.lineHeight * iconScale);
        int x = Math.max(minPixelX + 1, maxPixelX - width - 2);
        int y = Math.max(minPixelY + 1, maxPixelY - height - 1);
        boolean done = stats.princeKilled();
        drawScaledText(graphics, icon, x, y, done ? MUTED_TEXT : SECRET_TARGET_TEXT, iconScale, true);
        if (done) {
            fill(graphics, x - 1, y + height / 2, x + width + 1, y + height / 2 + 1, BAD_TEXT);
        }
    }

    private static void drawRoomText(
        GuiGraphicsExtractor graphics,
        int centerX,
        int centerY,
        int maxWidth,
        String label,
        RoomType roomType,
        int secretsFound,
        int secrets,
        int crypts,
        RoomTextState textState,
        List<String> debugLines
    ) {
        if (!shouldDrawRoomText(roomType)) {
            return;
        }

        float textScale = textScale(KungConfig.get().dungeon);
        List<String> labelLines = labelLines(label, Math.max(1, Math.round(maxWidth / (ROOM_LABEL_SCALE * textScale))));
        List<RoomTextLine> textLines = new ArrayList<>();
        for (String labelLine : labelLines) {
            textLines.add(new RoomTextLine(labelLine, ROOM_LABEL_SCALE));
        }
        if (secrets > 0 && textState.showSecrets()) {
            textLines.add(new RoomTextLine(Math.min(secretsFound, secrets) + "/" + secrets, ROOM_SECRET_SCALE));
        }
        if (KungConfig.get().dungeon.debugRoomCrypts()) {
            textLines.add(new RoomTextLine("Crypts " + roomCryptText(label, crypts), ROOM_SECRET_SCALE));
        }
        if (KungConfig.get().dungeon.debugRoomMatches()) {
            for (String debugLine : debugLines) {
                textLines.add(new RoomTextLine(debugLine, 0.42F));
            }
        }

        int lineStep = Math.max(1, Math.round(ROOM_TEXT_LINE_STEP * textScale));
        int y = centerY - ((textLines.size() - 1) * lineStep) / 2 - Math.round(3 * textScale);
        for (int index = 0; index < textLines.size(); index++) {
            RoomTextLine line = textLines.get(index);
            drawScaledCenteredText(
                graphics,
                line.text(),
                centerX,
                y + index * lineStep,
                textState.color(),
                line.scale() * textScale,
                true
            );
        }
    }

    private static RoomTextState roomTextState(
        RoomType roomType,
        boolean visited,
        boolean cleared,
        boolean completed,
        int secretsFound,
        int secrets
    ) {
        if (completed) {
            return new RoomTextState(COMPLETED_TEXT, true);
        }
        if (cleared) {
            return new RoomTextState(TEXT, true);
        }
        return new RoomTextState(MUTED_TEXT, true);
    }

    private static boolean shouldDrawRoomText(RoomType roomType) {
        return roomType != RoomType.BLOOD && roomType != RoomType.FAIRY;
    }

    private static List<String> labelLines(String value, int maxWidth) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        LabelLineKey key = new LabelLineKey(trimmed, maxWidth);
        List<String> cached = LABEL_LINE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        List<String> lines;
        String[] words = trimmed.split("\\s+");
        if (words.length == 1) {
            lines = labelSingleWord(words[0], maxWidth);
        } else {
            lines = clampLabelLines(wrapWordsOnly(words, maxWidth));
        }

        if (LABEL_LINE_CACHE.size() >= MAX_LABEL_LINE_CACHE_ENTRIES) {
            LABEL_LINE_CACHE.clear();
        }
        LABEL_LINE_CACHE.put(key, lines);
        return lines;
    }

    private static List<String> labelSingleWord(String word, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        if (word.length() >= LONG_WORD_SPLIT_MIN_CHARS && client.font.width(word) > maxWidth) {
            return splitWord(word, maxWidth);
        }
        return List.of(word);
    }

    private static List<String> wrapWordsOnly(String[] words, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        String currentLine = "";
        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (!currentLine.isEmpty() && client.font.width(candidate) > maxWidth) {
                lines.add(currentLine);
                currentLine = word;
            } else {
                currentLine = candidate;
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        return lines;
    }

    private static List<String> splitWord(String value, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        if (client.font.width(value) <= maxWidth) {
            return List.of(value);
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            String candidate = current.toString() + character;
            if (!current.isEmpty()
                && client.font.width(candidate) > maxWidth
                && lines.size() < MAX_ROOM_LABEL_LINES - 1) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            current.append(character);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static List<String> clampLabelLines(List<String> lines) {
        if (lines.size() <= MAX_ROOM_LABEL_LINES) {
            return lines;
        }

        List<String> clamped = new ArrayList<>(lines.subList(0, MAX_ROOM_LABEL_LINES));
        int last = clamped.size() - 1;
        clamped.set(last, clamped.get(last) + "..");
        return clamped;
    }

    private static void drawCenteredText(
        GuiGraphicsExtractor graphics,
        String text,
        int centerX,
        int y,
        int color,
        boolean shadow
    ) {
        float textScale = textScale(KungConfig.get().dungeon);
        drawScaledCenteredText(graphics, text, centerX, y - Math.round(4 * (textScale - 1)), color, textScale, shadow);
    }

    private static void drawScaledCenteredText(
        GuiGraphicsExtractor graphics,
        String text,
        int centerX,
        int y,
        int color,
        float scale,
        boolean shadow
    ) {
        Minecraft client = Minecraft.getInstance();
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y);
        graphics.pose().scale(scale, scale);
        int x = -client.font.width(text) / 2;
        graphics.text(client.font, text, x, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    private static void drawScaledText(
        GuiGraphicsExtractor graphics,
        String text,
        int x,
        int y,
        int color,
        float scale,
        boolean shadow
    ) {
        Minecraft client = Minecraft.getInstance();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(client.font, text, 0, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    private static int alphaBlack(int alphaPercent) {
        int alpha = Math.clamp(alphaPercent, 0, 100) * 255 / 100;
        return alpha << 24;
    }

    private static void drawPlayerMarkers(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonMapSnapshot snapshot,
        DungeonRunStats stats,
        float partialTick
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        long frame = ++playerMarkerFrame;
        List<MarkerCenter> drawnMarkers = new ArrayList<>();
        Set<UUID> drawnPlayerUuids = new HashSet<>();
        boolean collectDiagnostics = shouldCollectPlayerMarkerDiagnostics();

        int loadedCandidates = stats.dungeonPlayerSlots(client).size();
        int loadedDrawn = 0;
        DecorationMarkerStats decorationStats =
            drawMapDecorationPlayerMarkers(
                graphics,
                left,
                top,
                client,
                snapshot,
                stats,
                drawnMarkers,
                drawnPlayerUuids,
                frame,
                partialTick,
                collectDiagnostics
            );
        if (!drawnPlayerUuids.contains(client.player.getUUID())) {
            ClassRenderInfo classInfo = classRenderInfoFor(
                stats,
                client.player.getUUID(),
                client.player.getName().getString(),
                DungeonRunStats.DungeonClass.UNKNOWN
            );
            drawPlayerMarker(
                graphics,
                left,
                top,
                client.player,
                classInfo.color(),
                frame,
                partialTick
            );
            SmoothPlayerTarget selfTarget = smoothTargetFromEntity(client.player, partialTick);
            int selfX = Math.round(selfTarget.x());
            int selfY = Math.round(selfTarget.y());
            drawnMarkers.add(new MarkerCenter(selfX, selfY));
            drawnPlayerUuids.add(client.player.getUUID());
        }
        logPlayerMarkerState(
            client,
            stats,
            loadedCandidates,
            loadedDrawn,
            decorationStats,
            drawnMarkers.size(),
            drawnPlayerUuids,
            collectDiagnostics
        );
        forgetOldPlayerMarkers(frame);
    }

    private static void drawPlayerMarker(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        AbstractClientPlayer player,
        int classColor,
        long frame,
        float partialTick
    ) {
        Minecraft client = Minecraft.getInstance();
        int headSize = client.player != null && player.getUUID().equals(client.player.getUUID())
            ? PLAYER_HEAD_SIZE
            : TEAMMATE_HEAD_SIZE;
        SmoothPlayerTarget target = smoothTargetFromEntity(player, partialTick);
        MarkerPose pose = smoothMarker(
            "entity:" + player.getUUID(),
            target.x(),
            target.y(),
            target.rotation(),
            frame
        );

        PlayerSkin skin = player.getSkin();
        graphics.pose().pushMatrix();
        graphics.pose().translate(left + pose.x(), top + pose.y());
        graphics.pose().rotate((float) Math.toRadians(pose.rotation() + 180.0F));
        drawPlayerFrame(graphics, classColor, headSize);
        PlayerFaceExtractor.extractRenderState(
            graphics,
            skin,
            -headSize / 2,
            -headSize / 2,
            headSize
        );
        graphics.pose().popMatrix();
    }

    private static DecorationMarkerStats drawMapDecorationPlayerMarkers(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        Minecraft client,
        DungeonMapSnapshot snapshot,
        DungeonRunStats stats,
        List<MarkerCenter> drawnMarkers,
        Set<UUID> drawnPlayerUuids,
        long frame,
        float partialTick,
        boolean collectDiagnostics
    ) {
        MapItemSavedData mapData = dungeonMapData(client);
        if (mapData == null || client.player == null) {
            return DecorationMarkerStats.noMap();
        }

        int drawn = 0;
        int decorationIndex = 0;
        int total = 0;
        int playerDecorations = 0;
        int named = 0;
        int resolved = 0;
        int rejectedLimit = 0;
        int rejectedNoUuid = 0;
        int rejectedDuplicate = 0;
        int rejectedNoPixel = 0;
        int rejectedNearLoaded = 0;
        int anonymousDrawn = 0;
        List<DungeonRunStats.DungeonPlayerSlot> playerSlots = stats.dungeonPlayerSlots(client);
        int slotCursor = 1;
        StringBuilder details = collectDiagnostics ? new StringBuilder() : null;
        for (MapDecoration decoration : mapData.getDecorations()) {
            int markerIndex = decorationIndex++;
            total++;
            boolean selfMarker = decoration.type().equals(MapDecorationTypes.FRAME);
            boolean teammateMarker = decoration.type().equals(MapDecorationTypes.BLUE_MARKER);
            if (!selfMarker && !teammateMarker) {
                continue;
            }
            playerDecorations++;
            if (drawnMarkers.size() >= MAX_PLAYER_MARKERS) {
                rejectedLimit++;
                continue;
            }

            DungeonMapCheckmarkReader.MapPixel decorationPixel =
                overlayPixelForDecorationOrRaw(decoration, client, snapshot);
            DungeonRunStats.DungeonPlayerSlot playerSlot;
            if (selfMarker) {
                playerSlot = playerSlotAt(playerSlots, 0);
            } else {
                playerSlot = null;
                while (slotCursor < MAX_PLAYER_MARKERS && playerSlot == null) {
                    playerSlot = playerSlotAt(playerSlots, slotCursor++);
                }
            }
            String playerName = decorationPlayerName(decoration);
            if (!playerName.isEmpty()) {
                named++;
            }
            if (playerSlot == null || playerSlot.uuid() == null) {
                rejectedNoUuid++;
                appendDecorationDecision(details, markerIndex, decoration, playerName, decorationPixel, null, "reject-no-tab-slot", null, playerSlot, null, false);
                continue;
            }

            UUID playerUuid = playerSlot.uuid();
            if (drawnPlayerUuids.contains(playerUuid)) {
                rejectedDuplicate++;
                appendDecorationDecision(details, markerIndex, decoration, playerName, decorationPixel, null, "reject-duplicate", playerUuid, playerSlot, null, false);
                continue;
            }
            resolved++;

            AbstractClientPlayer loadedPlayer = playerByUuid(client, playerUuid);
            DungeonMapCheckmarkReader.MapPixel markerPixel = decorationPixel;
            if (markerPixel == null && loadedPlayer != null) {
                SmoothPlayerTarget entityTarget = smoothTargetFromEntity(loadedPlayer, partialTick);
                markerPixel = new DungeonMapCheckmarkReader.MapPixel(
                    Math.round(entityTarget.x()),
                    Math.round(entityTarget.y())
                );
            }
            if (markerPixel == null) {
                rejectedNoPixel++;
                appendDecorationDecision(details, markerIndex, decoration, playerName, decorationPixel, null, "reject-no-pixel", playerUuid, playerSlot, null, loadedPlayer != null);
                continue;
            }
            SmoothPlayerTarget target = markerTarget(markerPixel, decoration, loadedPlayer, partialTick);
            MarkerPose pose = smoothMarker(
                "player:" + playerUuid,
                target.x(),
                target.y(),
                target.rotation(),
                frame
            );
            String markerName = loadedPlayer == null ? playerSlot.name() : loadedPlayer.getName().getString();
            ClassRenderInfo classInfo = classRenderInfoFor(stats, playerUuid, markerName, playerSlot.dungeonClass());

            drawFallbackPlayerMarker(
                graphics,
                left + pose.x(),
                top + pose.y(),
                skinForMarker(client, playerUuid, loadedPlayer),
                pose.rotation(),
                classInfo.color(),
                playerUuid.equals(client.player.getUUID()) ? PLAYER_HEAD_SIZE : TEAMMATE_HEAD_SIZE
            );
            drawnMarkers.add(new MarkerCenter(Math.round(target.x()), Math.round(target.y())));
            drawnPlayerUuids.add(playerUuid);
            appendDecorationDecision(
                details,
                markerIndex,
                decoration,
                playerName,
                decorationPixel,
                markerPixel,
                "draw",
                playerUuid,
                playerSlot,
                classInfo,
                loadedPlayer != null
            );
            drawn++;
        }
        return new DecorationMarkerStats(
            true,
            total,
            playerDecorations,
            named,
            resolved,
            drawn,
            rejectedLimit,
            rejectedNoUuid,
            rejectedDuplicate,
            rejectedNoPixel,
            rejectedNearLoaded,
            anonymousDrawn,
            details == null ? "" : details.toString()
        );
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

    private static void logPlayerMarkerState(
        Minecraft client,
        DungeonRunStats stats,
        int loadedCandidates,
        int loadedDrawn,
        DecorationMarkerStats decorations,
        int drawnMarkerCount,
        Set<UUID> drawnPlayerUuids,
        boolean collectDiagnostics
    ) {
        if (!collectDiagnostics) {
            return;
        }
        long now = System.currentTimeMillis();
        String state = "markers="
            + drawnMarkerCount
            + "/"
            + MAX_PLAYER_MARKERS
            + " identified="
            + drawnPlayerUuids.size()
            + "/"
            + MAX_PLAYER_MARKERS
            + " loadedCandidates="
            + loadedCandidates
            + " loadedDrawn="
            + loadedDrawn
            + " worldPlayers="
            + (client.level == null ? 0 : client.level.players().size())
            + " partyKnown="
            + stats.partyPlayerCount()
            + " dungeonKnown="
            + stats.dungeonPlayerCount()
            + " trackedUuids="
            + stats.knownTrackedPlayerUuids().size()
            + " slots="
            + dungeonPlayerSlotSummary(client, stats)
            + " classes="
            + trackedClassSummary(stats)
            + " map="
            + decorations.hasMap()
            + " mapDecorations="
            + decorations.total()
            + " playerDecorations="
            + decorations.playerDecorations()
            + " named="
            + decorations.named()
            + " resolved="
            + decorations.resolved()
            + " mapDrawn="
            + decorations.drawn()
            + " anonymousDrawn="
            + decorations.anonymousDrawn()
            + " rejectLimit="
            + decorations.rejectedLimit()
            + " rejectNoUuid="
            + decorations.rejectedNoUuid()
            + " rejectDuplicate="
            + decorations.rejectedDuplicate()
            + " rejectNoPixel="
            + decorations.rejectedNoPixel()
            + " rejectNear="
            + decorations.rejectedNearLoaded()
            + " details="
            + decorations.details()
            + " uuids="
            + shortUuids(drawnPlayerUuids);
        if (state.equals(lastPlayerMarkerLogState) && now - lastPlayerMarkerLogMillis < PLAYER_MARKER_LOG_INTERVAL_MILLIS) {
            return;
        }
        lastPlayerMarkerLogState = state;
        lastPlayerMarkerLogMillis = now;
        KungDebugRecorder.event("player-markers", state);
    }

    private static boolean shouldCollectPlayerMarkerDiagnostics() {
        return System.currentTimeMillis() - lastPlayerMarkerLogMillis >= PLAYER_MARKER_LOG_INTERVAL_MILLIS;
    }

    private static String dungeonPlayerSlotSummary(Minecraft client, DungeonRunStats stats) {
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (DungeonRunStats.DungeonPlayerSlot slot : stats.dungeonPlayerSlots(client)) {
            if (count++ > 0) {
                builder.append(',');
            }
            builder.append(slot.index())
                .append(':')
                .append(slot.name().isBlank() ? "<empty>" : slot.name())
                .append(':')
                .append(shortUuid(slot.uuid()))
                .append(':')
                .append(slot.dungeonClass());
        }
        return builder.append(']').toString();
    }

    private static String trackedClassSummary(DungeonRunStats stats) {
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (UUID uuid : stats.knownTrackedPlayerUuids()) {
            if (count++ > 0) {
                builder.append(',');
            }
            String value = uuid.toString();
            builder.append(value, 0, Math.min(8, value.length()))
                .append('=')
                .append(stats.dungeonClass(uuid));
            if (count >= MAX_PLAYER_MARKERS) {
                break;
            }
        }
        return builder.append(']').toString();
    }

    private static void appendDecorationDecision(
        StringBuilder details,
        int markerIndex,
        MapDecoration decoration,
        String playerName,
        DungeonMapCheckmarkReader.MapPixel decorationPixel,
        DungeonMapCheckmarkReader.MapPixel markerPixel,
        String action,
        UUID playerUuid
        ,
        DungeonRunStats.DungeonPlayerSlot playerSlot,
        ClassRenderInfo classInfo,
        boolean loadedPlayer
    ) {
        if (details == null) {
            return;
        }
        if (details.length() > 1600) {
            return;
        }
        if (!details.isEmpty()) {
            details.append(" | ");
        }
        details.append('#')
            .append(markerIndex)
            .append(':')
            .append(action)
            .append(":type=")
            .append(mapDecorationTypeName(decoration))
            .append(":name=")
            .append(playerName == null || playerName.isEmpty() ? "<empty>" : playerName)
            .append(":raw=")
            .append(decoration.x())
            .append(',')
            .append(decoration.y())
            .append(":pixel=")
            .append(pixelText(decorationPixel))
            .append(":marker=")
            .append(pixelText(markerPixel))
            .append(":uuid=")
            .append(playerUuid == null ? "null" : playerUuid.toString().substring(0, 8))
            .append(":slot=")
            .append(playerSlot == null ? "null" : playerSlot.index())
            .append(":slotName=")
            .append(playerSlot == null || playerSlot.name().isBlank() ? "<empty>" : playerSlot.name())
            .append(":slotClass=")
            .append(playerSlot == null ? "null" : playerSlot.dungeonClass())
            .append(":class=")
            .append(classInfo == null ? "null" : classInfo.dungeonClass())
            .append(":classSource=")
            .append(classInfo == null ? "null" : classInfo.source())
            .append(":classColor=")
            .append(classInfo == null ? "null" : colorText(classInfo.color()))
            .append(":loaded=")
            .append(loadedPlayer);
    }

    private static String shortUuid(UUID uuid) {
        if (uuid == null) {
            return "null";
        }
        String value = uuid.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    private static String mapDecorationTypeName(MapDecoration decoration) {
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

    private static String pixelText(DungeonMapCheckmarkReader.MapPixel pixel) {
        return pixel == null ? "null" : pixel.x() + "," + pixel.z();
    }

    private static String shortUuids(Set<UUID> uuids) {
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (UUID uuid : uuids) {
            if (count++ > 0) {
                builder.append(',');
            }
            String value = uuid.toString();
            builder.append(value, 0, Math.min(8, value.length()));
        }
        return builder.append(']').toString();
    }

    private static AbstractClientPlayer playerByUuid(Minecraft client, UUID uuid) {
        if (client.level == null || uuid == null) {
            return null;
        }
        return client.level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer player ? player : null;
    }

    private static PlayerSkin skinForMarker(Minecraft client, UUID uuid, AbstractClientPlayer loadedPlayer) {
        if (loadedPlayer != null) {
            PlayerSkin skin = loadedPlayer.getSkin();
            if (uuid != null) {
                LAST_PLAYER_SKINS.put(uuid, skin);
            }
            return skin;
        }
        if (client.getConnection() != null && uuid != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                PlayerSkin skin = info.getSkin();
                LAST_PLAYER_SKINS.put(uuid, skin);
                return skin;
            }
        }
        if (uuid != null) {
            PlayerSkin rememberedSkin = LAST_PLAYER_SKINS.get(uuid);
            if (rememberedSkin != null) {
                return rememberedSkin;
            }
        }
        return uuid == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(uuid);
    }

    private static SmoothPlayerTarget markerTarget(
        DungeonMapCheckmarkReader.MapPixel decorationPixel,
        MapDecoration decoration,
        AbstractClientPlayer loadedPlayer,
        float partialTick
    ) {
        if (loadedPlayer != null) {
            return smoothTargetFromEntity(loadedPlayer, partialTick);
        }
        return new SmoothPlayerTarget(
            decorationPixel.x(),
            decorationPixel.z(),
            decoration.rot() * 360.0F / 16.0F
        );
    }

    private static SmoothPlayerTarget smoothTargetFromEntity(AbstractClientPlayer player, float partialTick) {
        Vec3 position = player.getPosition(partialTick);
        return new SmoothPlayerTarget(
            mapPixelForWorldXPrecise(position.x),
            mapPixelForWorldZPrecise(position.z),
            player.getViewYRot(partialTick)
        );
    }

    private static DungeonMapCheckmarkReader.MapPixel overlayPixelForDecorationOrRaw(
        MapDecoration decoration,
        Minecraft client,
        DungeonMapSnapshot snapshot
    ) {
        DungeonMapCheckmarkReader.MapPixel anchored =
            DungeonMapCheckmarkReader.overlayPixelForDecoration(
                decoration,
                client,
                snapshot,
                ROOM_SIZE,
                DOOR_SIZE,
                CELL_GAP
            );
        if (anchored != null) {
            return anchored;
        }

        int mapX = (decoration.x() >> 1) + 64;
        int mapZ = (decoration.y() >> 1) + 64;
        return new DungeonMapCheckmarkReader.MapPixel(
            Math.clamp(Math.round(mapX * GRID_PIXEL_SIZE / 127.0F), 0, GRID_PIXEL_SIZE),
            Math.clamp(Math.round(mapZ * GRID_PIXEL_SIZE / 127.0F), 0, GRID_PIXEL_SIZE)
        );
    }

    private static String decorationPlayerName(MapDecoration decoration) {
        String name = decoration.name().map(component -> component.getString().trim()).orElse("");
        return name.replaceAll("\u00a7.", "").trim();
    }

    private static void drawFallbackPlayerMarker(
        GuiGraphicsExtractor graphics,
        float centerX,
        float centerY,
        PlayerSkin skin,
        float rotationDegrees,
        int classColor,
        int headSize
    ) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(rotationDegrees + 180.0F));
        drawPlayerFrame(graphics, classColor, headSize);
        PlayerFaceExtractor.extractRenderState(
            graphics,
            skin,
            -headSize / 2,
            -headSize / 2,
            headSize
        );
        graphics.pose().popMatrix();
    }

    private static void drawPlayerFrame(GuiGraphicsExtractor graphics, int classColor, int headSize) {
        int half = headSize / 2;
        fill(graphics, -half - 1, -half - 1, half + 1, -half, classColor);
        fill(graphics, -half - 1, half, half + 1, half + 1, classColor);
        fill(graphics, -half - 1, -half - 1, -half, half + 1, classColor);
        fill(graphics, half, -half - 1, half + 1, half + 1, classColor);
        fill(graphics, -1, -half - 3, 1, -half - 1, classColor);
    }

    private static MarkerPose smoothMarker(String key, float targetX, float targetY, float targetRotation, long frame) {
        long now = System.nanoTime();
        SmoothedMarker marker = SMOOTHED_PLAYER_MARKERS.get(key);
        if (marker == null) {
            marker = new SmoothedMarker(targetX, targetY, targetRotation, now, frame);
            SMOOTHED_PLAYER_MARKERS.put(key, marker);
            return new MarkerPose(marker.x, marker.y, marker.rotation);
        }

        double dt = Math.clamp((now - marker.lastUpdateNanos) / 1_000_000_000.0, 0.0, 0.25);
        double dx = targetX - marker.x;
        double dy = targetY - marker.y;
        if (dx * dx + dy * dy > PLAYER_MARKER_SNAP_DISTANCE * PLAYER_MARKER_SNAP_DISTANCE) {
            marker.x = targetX;
            marker.y = targetY;
            marker.rotation = targetRotation;
        } else {
            float alpha = (float) (1.0 - Math.exp(-PLAYER_MARKER_SMOOTHING * dt));
            marker.x += dx * alpha;
            marker.y += dy * alpha;
            marker.rotation += shortestAngleDelta(marker.rotation, targetRotation) * alpha;
        }
        marker.lastUpdateNanos = now;
        marker.lastSeenFrame = frame;
        return new MarkerPose(marker.x, marker.y, marker.rotation);
    }

    private static float renderPartialTick() {
        Minecraft client = Minecraft.getInstance();
        return client.gameRenderer.getMainCamera().getCameraEntityPartialTicks(client.getDeltaTracker());
    }

    private static float shortestAngleDelta(float from, float to) {
        float delta = (to - from) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        }
        if (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    private static void forgetOldPlayerMarkers(long frame) {
        SMOOTHED_PLAYER_MARKERS.entrySet().removeIf(entry -> frame - entry.getValue().lastSeenFrame > 40L);
    }

    private static ClassRenderInfo classRenderInfoFor(
        DungeonRunStats stats,
        java.util.UUID uuid,
        String name,
        DungeonRunStats.DungeonClass slotClass
    ) {
        if (slotClass != null && slotClass != DungeonRunStats.DungeonClass.UNKNOWN) {
            return new ClassRenderInfo(slotClass, colorForDungeonClass(slotClass), "slot");
        }

        DungeonRunStats.DungeonClass dungeonClass = stats.dungeonClass(uuid);
        if (dungeonClass != DungeonRunStats.DungeonClass.UNKNOWN) {
            return new ClassRenderInfo(dungeonClass, colorForDungeonClass(dungeonClass), "uuid");
        }

        dungeonClass = stats.dungeonClass(name);
        if (dungeonClass != DungeonRunStats.DungeonClass.UNKNOWN) {
            return new ClassRenderInfo(dungeonClass, colorForDungeonClass(dungeonClass), "name");
        }

        String trackedName = stats.trackedPlayerName(uuid);
        dungeonClass = stats.dungeonClass(trackedName);
        if (dungeonClass != DungeonRunStats.DungeonClass.UNKNOWN) {
            return new ClassRenderInfo(dungeonClass, colorForDungeonClass(dungeonClass), "tracked-name");
        }

        return new ClassRenderInfo(DungeonRunStats.DungeonClass.UNKNOWN, UNKNOWN_CLASS_BORDER, "unknown");
    }

    private static int colorForDungeonClass(DungeonRunStats.DungeonClass dungeonClass) {
        return switch (dungeonClass) {
            case ARCHER -> 0xFFFF2D32;
            case BERSERKER -> 0xFFFF9829;
            case MAGE -> 0xFF62DFFF;
            case HEALER -> 0xFFD68CFF;
            case TANK -> 0xFF34A853;
            default -> UNKNOWN_CLASS_BORDER;
        };
    }

    private static String colorText(int color) {
        return String.format(java.util.Locale.ROOT, "#%08X", color);
    }

    private static MapItemSavedData dungeonMapData(Minecraft client) {
        return DungeonMapItems.mapData(client);
    }

    private static int mapPixelForWorldX(double worldX) {
        return Math.round(mapPixelForWorldXPrecise(worldX));
    }

    private static int mapPixelForWorldZ(double worldZ) {
        return Math.round(mapPixelForWorldZPrecise(worldZ));
    }

    private static float mapPixelForWorldXPrecise(double worldX) {
        return (float) Math.clamp(rawMapPixelForWorldX(worldX), 0.0, GRID_PIXEL_SIZE);
    }

    private static float mapPixelForWorldZPrecise(double worldZ) {
        return (float) Math.clamp(rawMapPixelForWorldZ(worldZ), 0.0, GRID_PIXEL_SIZE);
    }

    private static double rawMapPixelForWorldX(double worldX) {
        double roomProgress = (worldX - DungeonScanUtils.START_X) / DungeonScanUtils.ROOM_SIZE_BLOCKS;
        return ROOM_SIZE / 2.0 + roomProgress * pixelsPerRoom();
    }

    private static double rawMapPixelForWorldZ(double worldZ) {
        double roomProgress = (worldZ - DungeonScanUtils.START_Z) / DungeonScanUtils.ROOM_SIZE_BLOCKS;
        return ROOM_SIZE / 2.0 + roomProgress * pixelsPerRoom();
    }

    private static int pixelsPerRoom() {
        return ROOM_SIZE + DOOR_SIZE + CELL_GAP * 2;
    }

    private static void drawLegend(GuiGraphicsExtractor graphics, int left, int top) {
        int x = left;
        x = drawLegendItem(graphics, x, top, RoomType.START, "start");
        x = drawLegendItem(graphics, x, top, RoomType.NORMAL, "normal");
        drawLegendItem(graphics, x, top, RoomType.PUZZLE, "puzzle");
        x = drawLegendItem(graphics, left, top + 12, RoomType.FAIRY, "fairy");
        x = drawLegendItem(graphics, x, top + 12, RoomType.TRAP, "trap");
        drawLegendItem(graphics, x, top + 12, RoomType.BLOOD, "blood");
        drawLegendItem(graphics, left, top + 24, RoomType.YELLOW, "yellow");
    }

    private static int drawLegendItem(GuiGraphicsExtractor graphics, int x, int y, RoomType roomType, String label) {
        Minecraft client = Minecraft.getInstance();
        fill(graphics, x, y, x + 7, y + 7, roomType.color());
        graphics.text(client.font, label, x + 10, y, MUTED_TEXT, true);
        return x + 10 + client.font.width(label) + 8;
    }

    private static void drawFooter(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        DungeonRunStats stats,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        fill(graphics, left - 3, top, left + GRID_PIXEL_SIZE + 3, top + FOOTER_HEIGHT - 4, PANEL);
        int estimatedSecretsAvailable = estimatedSecretTotal(renderPlan);
        int fullSecrets = bestSecretTotal(stats, estimatedSecretsAvailable);
        int foundSecrets = displayedSecretsFound(stats, fullSecrets);
        int sPlusSecrets = stats.sPlusSecretsRemaining(renderPlan, fullSecrets);
        int score = stats.score(renderPlan, fullSecrets);

        int x = left + 2;
        int firstLineY = top + 4;
        x = drawFooterText(graphics, "Secrets: ", x, firstLineY, TEXT);
        x = drawFooterText(graphics, String.valueOf(foundSecrets), x, firstLineY, SECRET_FOUND_TEXT);
        x = drawFooterText(graphics, "-", x, firstLineY, TEXT);
        x = drawFooterText(graphics, sPlusSecrets >= 0 ? String.valueOf(sPlusSecrets) : "?", x, firstLineY, SECRET_TARGET_TEXT);
        x = drawFooterText(graphics, "-", x, firstLineY, TEXT);
        x = drawFooterText(graphics, fullSecrets > 0 ? String.valueOf(fullSecrets) : "?", x, firstLineY, SECRET_FULL_TEXT);
        x = drawFooterText(graphics, "   Score: ", x, firstLineY, TEXT);
        drawFooterText(graphics, score >= 0 ? String.valueOf(score) : "?", x, firstLineY, scoreColor(score));

        x = left + 2;
        int secondLineY = top + 14;
        x = drawFooterText(graphics, "Deaths: ", x, secondLineY, TEXT);
        x = drawFooterText(graphics, String.valueOf(stats.deaths()), x, secondLineY, deathColor(stats.deaths()));
        x = drawFooterText(graphics, " | M: ", x, secondLineY, TEXT);
        x = drawFooterStatus(graphics, x, secondLineY, stats.mimicKilled());
        x = drawFooterText(graphics, " | P: ", x, secondLineY, TEXT);
        x = drawFooterStatus(graphics, x, secondLineY, stats.princeKilled());
        x = drawFooterText(graphics, " | B: ", x, secondLineY, TEXT);
        x = drawFooterStatus(graphics, x, secondLineY, stats.batScoreKilled());
        x = drawFooterText(graphics, " | Crypts: ", x, secondLineY, TEXT);
        x = drawFooterText(graphics, String.valueOf(stats.cryptsOpened()), x, secondLineY, cryptColor(stats.cryptsOpened()));
        x = drawFooterText(graphics, "/", x, secondLineY, TEXT);
        drawFooterText(graphics, cryptTotalText(stats, snapshot, renderPlan), x, secondLineY, TEXT);
    }

    private static int displayedSecretsFound(DungeonRunStats stats, int secretsAvailable) {
        if (stats.hasServerSecretsFound()) {
            if (secretsAvailable > 0) {
                return Math.clamp(stats.secretsFound(), 0, secretsAvailable);
            }
            return Math.max(0, stats.secretsFound());
        }
        if (secretsAvailable > 0 && stats.secretsPercent() >= 0.0) {
            return Math.clamp(
                (int) Math.round(secretsAvailable * stats.secretsPercent() / 100.0),
                0,
                secretsAvailable
            );
        }
        if (secretsAvailable > 0) {
            return Math.clamp(stats.secretsFound(), 0, secretsAvailable);
        }
        return Math.max(0, stats.secretsFound());
    }

    private static int bestSecretTotal(DungeonRunStats stats, int estimatedSecretsAvailable) {
        return stats.bestSecretsAvailable(estimatedSecretsAvailable);
    }

    private static int drawFooterStatus(GuiGraphicsExtractor graphics, int x, int y, boolean value) {
        return drawFooterText(graphics, value ? "\u2713" : "x", x, y, value ? GOOD_TEXT : BAD_TEXT);
    }

    private static int deathColor(int deaths) {
        if (deaths <= 0) {
            return GOOD_TEXT;
        }
        return deaths == 1 ? SECRET_TARGET_TEXT : BAD_TEXT;
    }

    private static int scoreColor(int score) {
        if (score >= 300) {
            return GOOD_TEXT;
        }
        if (score >= 270) {
            return SECRET_TARGET_TEXT;
        }
        return BAD_TEXT;
    }

    private static int cryptColor(int crypts) {
        if (crypts <= 0) {
            return BAD_TEXT;
        }
        return crypts >= 5 ? GOOD_TEXT : SECRET_TARGET_TEXT;
    }

    private static String cryptTotalText(
        DungeonRunStats stats,
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        if (stats.cryptsAvailable() >= 0) {
            return String.valueOf(stats.cryptsAvailable());
        }

        CryptEstimate estimate = estimatedCryptTotal(snapshot, renderPlan);
        if (!estimate.hasUnknownRooms()) {
            return String.valueOf(estimate.knownTotal());
        }
        if (estimate.knownTotal() > 0) {
            return estimate.knownTotal() + (estimate.hasUnknownRooms() ? "+?" : "");
        }
        return "?";
    }

    private static int drawFooterText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Minecraft client = Minecraft.getInstance();
        drawScaledText(graphics, text, x, y, color, FOOTER_TEXT_SCALE, true);
        return x + Math.round(client.font.width(text) * FOOTER_TEXT_SCALE);
    }

    public static OverlayBounds overlayBounds(DungeonConfig config) {
        float scale = effectiveScale(config);
        int height = overlayContentHeight(config);
        return new OverlayBounds(
            Math.round(config.x() - overlayLeftMargin(config) * scale),
            Math.round(config.y() - 5 * scale),
            Math.round(overlayContentWidth(config) * scale),
            Math.round((height + 5) * scale)
        );
    }

    private static float effectiveScale(DungeonConfig config) {
        // Honor the editor value exactly, independently of resolution and Minecraft GUI scale.
        return Math.clamp(config.scale(), 25, 300) / 100.0F;
    }

    private static float textScale(DungeonConfig config) {
        return Math.clamp(config.textScale(), 50, 200) / 100.0F;
    }

    private static int overlayContentWidth(DungeonConfig config) {
        return Math.round((GRID_PIXEL_SIZE + 3) * Math.max(1, textScale(config))) + overlayLeftMargin(config) + 2;
    }

    private static int overlayLeftMargin(DungeonConfig config) {
        return Math.max(5, Math.round(3 * textScale(config)));
    }

    private static int footerTop(DungeonConfig config) {
        return GRID_PIXEL_SIZE + (config.showLegend() ? Math.round(LEGEND_HEIGHT * textScale(config)) + 8 : 6);
    }

    private static int overlayContentHeight(DungeonConfig config) {
        return footerTop(config) + Math.round(FOOTER_HEIGHT * textScale(config)) + 4;
    }

    private record GridViewport(int minPixelX, int minPixelY, int maxPixelX, int maxPixelY, float scale) {
        private static final int MIN_VISIBLE_ROOMS = 5;
        private static final float MAX_AUTO_SCALE = 1.22F;

        static GridViewport from(
            DungeonMapSnapshot snapshot,
            DungeonLiveMapWriter.MatchRenderPlan renderPlan,
            DungeonRunStats stats
        ) {
            MutableRoomBounds bounds = new MutableRoomBounds();
            for (int gridZ = 0; gridZ < DungeonScanUtils.SCAN_GRID_SIZE; gridZ++) {
                for (int gridX = 0; gridX < DungeonScanUtils.SCAN_GRID_SIZE; gridX++) {
                    DungeonMapSnapshot.ObservedPoint point = snapshot.pointAt(gridX, gridZ);
                    if (point == null) {
                        continue;
                    }
                    if (point.point().kind() == DungeonScanPointKind.ROOM
                        && DungeonRoomClassifier.isEmptyCore(point.point().coreHash())) {
                        continue;
                    }
                    if (point.point().kind() == DungeonScanPointKind.SEPARATOR) {
                        continue;
                    }
                    bounds.includeScanCell(gridX, gridZ);
                }
            }
            for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
                for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                    bounds.includeScanCell(component.roomGridX() * 2, component.roomGridZ() * 2);
                }
            }
            for (DungeonLiveMapWriter.CellKey hint : renderPlan.hints().keySet()) {
                bounds.includeScanCell(hint.x() * 2, hint.z() * 2);
            }
            for (DungeonLiveMapWriter.CellKey door : renderPlan.externalDoors().keySet()) {
                bounds.includeScanCell(door.x(), door.z());
            }

            Minecraft client = Minecraft.getInstance();
            includePlayerMarkerBounds(bounds, client, snapshot, stats);
            if (client.player != null) {
                DungeonScanUtils.GridPosition playerGrid =
                    DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
                if (validRoomGrid(playerGrid.gridX(), playerGrid.gridZ())) {
                    bounds.includeScanCell(playerGrid.gridX() * 2, playerGrid.gridZ() * 2);
                }
            }

            if (!bounds.valid()) {
                return full();
            }

            int minVisibleRooms = minVisibleRooms(stats);
            bounds.expandToAtLeast(minVisibleRooms);
            int minX = scanGridToPixel(bounds.minRoomX * 2);
            int minY = scanGridToPixel(bounds.minRoomZ * 2);
            int maxX = scanGridToPixel(bounds.maxRoomX * 2) + ROOM_SIZE;
            int maxY = scanGridToPixel(bounds.maxRoomZ * 2) + ROOM_SIZE;
            int width = Math.max(1, maxX - minX);
            int height = Math.max(1, maxY - minY);
            float autoScale = Math.min(GRID_PIXEL_SIZE / (float) width, GRID_PIXEL_SIZE / (float) height);
            float scale = Math.min(maxAutoScale(stats), Math.max(1.0F, autoScale));
            if (scale < 1.06F) {
                return full();
            }
            return new GridViewport(minX, minY, maxX, maxY, scale);
        }

        private static void includePlayerMarkerBounds(
            MutableRoomBounds bounds,
            Minecraft client,
            DungeonMapSnapshot snapshot,
            DungeonRunStats stats
        ) {
            if (client.level == null || client.player == null) {
                return;
            }

            MapItemSavedData mapData = dungeonMapData(client);
            if (mapData != null) {
                List<DungeonRunStats.DungeonPlayerSlot> playerSlots = stats.dungeonPlayerSlots(client);
                Set<UUID> assignedDecorationUuids = new HashSet<>();
                int slotCursor = 1;
                for (MapDecoration decoration : mapData.getDecorations()) {
                    boolean selfMarker = decoration.type().equals(MapDecorationTypes.FRAME);
                    boolean teammateMarker = decoration.type().equals(MapDecorationTypes.BLUE_MARKER);
                    if (!selfMarker && !teammateMarker) {
                        continue;
                    }
                    DungeonRunStats.DungeonPlayerSlot playerSlot;
                    if (selfMarker) {
                        playerSlot = playerSlotAt(playerSlots, 0);
                    } else {
                        playerSlot = null;
                        while (slotCursor < MAX_PLAYER_MARKERS && playerSlot == null) {
                            playerSlot = playerSlotAt(playerSlots, slotCursor++);
                        }
                    }
                    if (playerSlot == null || playerSlot.uuid() == null || assignedDecorationUuids.contains(playerSlot.uuid())) {
                        continue;
                    }
                    DungeonMapCheckmarkReader.MapPixel pixel =
                        overlayPixelForDecorationOrRaw(decoration, client, snapshot);
                    AbstractClientPlayer loadedPlayer = playerByUuid(client, playerSlot.uuid());
                    if (pixel != null) {
                        bounds.includePixel(pixel.x(), pixel.z());
                        assignedDecorationUuids.add(playerSlot.uuid());
                    } else if (loadedPlayer != null) {
                        bounds.includePixel(mapPixelForWorldX(loadedPlayer.getX()), mapPixelForWorldZ(loadedPlayer.getZ()));
                        assignedDecorationUuids.add(playerSlot.uuid());
                    }
                }
                return;
            }

            for (DungeonRunStats.DungeonPlayerSlot slot : stats.dungeonPlayerSlots(client)) {
                AbstractClientPlayer player = playerByUuid(client, slot.uuid());
                if (player != null) {
                    bounds.includePixel(mapPixelForWorldX(player.getX()), mapPixelForWorldZ(player.getZ()));
                }
            }
        }

        private static int minVisibleRooms(DungeonRunStats stats) {
            if (stats.floor() <= 1) {
                return 4;
            }
            if (stats.floor() <= 3) {
                return 5;
            }
            return MIN_VISIBLE_ROOMS;
        }

        private static float maxAutoScale(DungeonRunStats stats) {
            if (stats.floor() <= 1) {
                return 1.52F;
            }
            if (stats.floor() <= 3) {
                return 1.32F;
            }
            return MAX_AUTO_SCALE;
        }

        static GridViewport full() {
            return new GridViewport(0, 0, GRID_PIXEL_SIZE, GRID_PIXEL_SIZE, 1.0F);
        }

        int offsetX() {
            return Math.round((GRID_PIXEL_SIZE - width() * scale) / 2.0F);
        }

        int offsetY() {
            return Math.round((GRID_PIXEL_SIZE - height() * scale) / 2.0F);
        }

        boolean containsScanCell(int gridX, int gridZ) {
            int x = scanGridToPixel(gridX);
            int y = scanGridToPixel(gridZ);
            int size = sizeFor(gridX, gridZ);
            return x + size >= minPixelX
                && y + size >= minPixelY
                && x <= maxPixelX
                && y <= maxPixelY;
        }

        boolean containsMatch(DungeonKnownRoomCatalog.MatchedRoom match) {
            for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                if (containsScanCell(component.roomGridX() * 2, component.roomGridZ() * 2)) {
                    return true;
                }
            }
            return false;
        }

        private int width() {
            return maxPixelX - minPixelX;
        }

        private int height() {
            return maxPixelY - minPixelY;
        }
    }

    private static final class MutableRoomBounds {
        private int minRoomX = Integer.MAX_VALUE;
        private int minRoomZ = Integer.MAX_VALUE;
        private int maxRoomX = Integer.MIN_VALUE;
        private int maxRoomZ = Integer.MIN_VALUE;

        private void includeScanCell(int gridX, int gridZ) {
            int firstRoomX = gridX / 2;
            int firstRoomZ = gridZ / 2;
            int secondRoomX = DungeonScanUtils.isDoorScanPoint(gridX, gridZ) && (gridX & 1) == 1
                ? Math.min(DungeonScanUtils.SCAN_GRID_SIZE / 2, firstRoomX + 1)
                : firstRoomX;
            int secondRoomZ = DungeonScanUtils.isDoorScanPoint(gridX, gridZ) && (gridZ & 1) == 1
                ? Math.min(DungeonScanUtils.SCAN_GRID_SIZE / 2, firstRoomZ + 1)
                : firstRoomZ;
            includeRoom(firstRoomX, firstRoomZ);
            includeRoom(secondRoomX, secondRoomZ);
        }

        private void includeRoom(int roomX, int roomZ) {
            if (!validRoomGrid(roomX, roomZ)) {
                return;
            }
            minRoomX = Math.min(minRoomX, roomX);
            minRoomZ = Math.min(minRoomZ, roomZ);
            maxRoomX = Math.max(maxRoomX, roomX);
            maxRoomZ = Math.max(maxRoomZ, roomZ);
        }

        private void includePixel(int x, int z) {
            includeRoom(
                Math.clamp(Math.round((x - ROOM_SIZE / 2.0F) / pixelsPerRoom()), 0, DungeonScanUtils.SCAN_GRID_SIZE / 2),
                Math.clamp(Math.round((z - ROOM_SIZE / 2.0F) / pixelsPerRoom()), 0, DungeonScanUtils.SCAN_GRID_SIZE / 2)
            );
        }

        private void expandToAtLeast(int rooms) {
            int maxRoom = DungeonScanUtils.SCAN_GRID_SIZE / 2;
            while (maxRoomX - minRoomX + 1 < rooms) {
                if (minRoomX > 0) {
                    minRoomX--;
                }
                if (maxRoomX - minRoomX + 1 >= rooms) {
                    break;
                }
                if (maxRoomX < maxRoom) {
                    maxRoomX++;
                } else {
                    break;
                }
            }
            while (maxRoomZ - minRoomZ + 1 < rooms) {
                if (minRoomZ > 0) {
                    minRoomZ--;
                }
                if (maxRoomZ - minRoomZ + 1 >= rooms) {
                    break;
                }
                if (maxRoomZ < maxRoom) {
                    maxRoomZ++;
                } else {
                    break;
                }
            }
        }

        private boolean valid() {
            return minRoomX <= maxRoomX && minRoomZ <= maxRoomZ;
        }
    }

    private static int estimatedSecretTotal(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int total = 0;
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            total += match.template().secrets();
        }
        for (DungeonKnownRoomCatalog.KnownCoreHint hint : renderPlan.hints().values()) {
            total += hint.secrets();
        }
        return total;
    }

    private static CryptEstimate estimatedCryptTotal(
        DungeonMapSnapshot snapshot,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        int knownTotal = 0;
        boolean hasUnknownRooms = renderPlan.hasUnknownRooms(snapshot);
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            if (hasUncertainWikiCrypts(match.template().name(), match.template().crypts())) {
                hasUnknownRooms = true;
            }
            knownTotal += Math.max(0, match.template().crypts());
        }
        for (DungeonKnownRoomCatalog.KnownCoreHint hint : renderPlan.hints().values()) {
            if (hasUncertainWikiCrypts(hint.name(), hint.crypts())) {
                hasUnknownRooms = true;
            }
            knownTotal += Math.max(0, hint.crypts());
        }
        return new CryptEstimate(knownTotal, hasUnknownRooms);
    }

    private static boolean hasUncertainWikiCrypts(String roomName, int crypts) {
        return crypts <= 0
            && (roomName.equalsIgnoreCase("Admin")
                || roomName.equalsIgnoreCase("Buttons"));
    }

    private static String roomCryptText(String roomName, int crypts) {
        return hasUncertainWikiCrypts(roomName, crypts)
            ? "?"
            : String.valueOf(Math.max(0, crypts));
    }

    private record CryptEstimate(int knownTotal, boolean hasUnknownRooms) {
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private static String currentPlayerGridText() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return "?,?";
        }
        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        return grid.gridX() + "," + grid.gridZ();
    }

    private static int scanGridToPixel(int gridPosition) {
        int pixel = 0;
        for (int index = 0; index < gridPosition; index++) {
            pixel += sizeFor(index) + CELL_GAP;
        }
        return pixel;
    }

    private static int sizeFor(int gridX, int gridZ) {
        if (DungeonScanUtils.isSeparatorScanPoint(gridX, gridZ)) {
            return DOOR_SIZE;
        }
        return DungeonScanUtils.isRoomScanPoint(gridX, gridZ) ? ROOM_SIZE : DOOR_SIZE;
    }

    private static int sizeFor(int gridPosition) {
        return (gridPosition & 1) == 0 ? ROOM_SIZE : DOOR_SIZE;
    }

    private static boolean isHorizontalDoor(int gridX, int gridZ) {
        return (gridX & 1) != 0 && (gridZ & 1) == 0;
    }

    private static boolean validRoomGrid(int gridX, int gridZ) {
        return gridX >= 0
            && gridZ >= 0
            && gridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && gridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    private record LabelPlacement(int centerX, int centerY, int maxWidth) {
    }

    private record RoomBounds(int minX, int maxX, int minZ, int maxZ) {
        int spanX() {
            return maxX - minX + 1;
        }

        int spanZ() {
            return maxZ - minZ + 1;
        }
    }

    private record RoomTextLine(String text, float scale) {
    }

    private record RoomTextState(int color, boolean showSecrets) {
    }

    private record LabelLineKey(String label, int maxWidth) {
    }

    private record MarkerCenter(int x, int y) {
    }

    private record MarkerPose(float x, float y, float rotation) {
    }

    private record SmoothPlayerTarget(float x, float y, float rotation) {
    }

    private record ClassRenderInfo(DungeonRunStats.DungeonClass dungeonClass, int color, String source) {
    }

    private record DecorationMarkerStats(
        boolean hasMap,
        int total,
        int playerDecorations,
        int named,
        int resolved,
        int drawn,
        int rejectedLimit,
        int rejectedNoUuid,
        int rejectedDuplicate,
        int rejectedNoPixel,
        int rejectedNearLoaded,
        int anonymousDrawn,
        String details
    ) {
        static DecorationMarkerStats noMap() {
            return new DecorationMarkerStats(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
        }
    }

    private static final class SmoothedMarker {
        private float x;
        private float y;
        private float rotation;
        private long lastUpdateNanos;
        private long lastSeenFrame;

        private SmoothedMarker(float x, float y, float rotation, long lastUpdateNanos, long lastSeenFrame) {
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.lastUpdateNanos = lastUpdateNanos;
            this.lastSeenFrame = lastSeenFrame;
        }
    }

    public record OverlayBounds(int x, int y, int width, int height) {
    }
}
