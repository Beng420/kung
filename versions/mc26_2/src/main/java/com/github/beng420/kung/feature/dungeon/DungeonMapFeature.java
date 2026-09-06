package com.github.beng420.kung.feature.dungeon;

import static com.github.beng420.kung.util.GuiDraw.fill;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
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

public final class DungeonMapFeature {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(KungMod.MOD_ID, "dungeon_map_overlay");
    private static final int ROOM_SIZE = 19;
    private static final int DOOR_SIZE = 6;
    private static final int CELL_GAP = 1;
    private static final int GRID_UNITS = DungeonScanUtils.SCAN_GRID_SIZE;
    private static final int GRID_PIXEL_SIZE = scanGridToPixel(GRID_UNITS);
    private static final int HEADER_HEIGHT = 0;
    private static final int LEGEND_HEIGHT = 14;
    private static final int PLAYER_HEAD_SIZE = 10;
    private static final int TEAMMATE_HEAD_SIZE = 8;
    private static final float ROOM_LABEL_SCALE = 0.56F;
    private static final float ROOM_SECRET_SCALE = 0.54F;
    private static final int ROOM_TEXT_LINE_STEP = 5;
    private static final int MAX_ROOM_LABEL_LINES = 3;
    private static final int LONG_WORD_SPLIT_MIN_CHARS = 12;
    private static final int MAX_PLAYER_MARKERS = 5;
    private static final double PLAYER_MARKER_SMOOTHING = 18.0;
    private static final int PLAYER_MARKER_SNAP_DISTANCE = 18;
    private static final int PLAYER_IDENTITY_MATCH_DISTANCE = 12;
    private static final int PLAYER_IDENTITY_MEMORY_DISTANCE = 18;

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
    private static final int OUTLINE_TEXT = 0xFF000000;
    private static final int UNKNOWN_CLASS_BORDER = 0xFFE9EDF2;
    private static final int MAX_UNOPENED_ALPHA = 28;
    private static final int FOOTER_HEIGHT = 26;
    private static final float FOOTER_TEXT_SCALE = 0.72F;
    private static final int MAX_CONFIG_SCALE = 150;
    private static final float MAX_SCREEN_WIDTH_FRACTION = 0.36F;
    private static final float MAX_SCREEN_HEIGHT_FRACTION = 0.46F;
    private static final Map<String, SmoothedMarker> SMOOTHED_PLAYER_MARKERS = new HashMap<>();
    private static final Map<UUID, MarkerCenter> LAST_IDENTIFIED_PLAYER_MARKERS = new HashMap<>();
    private static long playerMarkerFrame;

    private DungeonMapFeature() {
    }

    public static Feature definition() {
        return new Feature(
            "dungeon-map-overlay",
            "Dungeon Map Overlay",
            true,
            "Shows the live dungeon scan map in the HUD."
        );
    }

    public static void initializeClient(DungeonStateTracker dungeonStateTracker) {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.PLAYER_LIST,
            HUD_ID,
            (graphics, deltaTracker) -> render(graphics, deltaTracker, dungeonStateTracker)
        );
    }

    private static void render(
        GuiGraphicsExtractor graphics,
        DeltaTracker deltaTracker,
        DungeonStateTracker dungeonStateTracker
    ) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
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
            int height = GRID_PIXEL_SIZE
                + (config.showLegend() ? LEGEND_HEIGHT : 0)
                + FOOTER_HEIGHT
                + 10;

            drawGrid(
                graphics,
                left,
                gridTop,
                snapshot,
                renderPlan,
                dungeonStateTracker.runStats(),
                dungeonStateTracker.isRecording()
            );
            if (config.showLegend()) {
                drawLegend(graphics, left, gridTop + GRID_PIXEL_SIZE + 5);
            }
            drawFooter(
                graphics,
                left,
                gridTop + GRID_PIXEL_SIZE + (config.showLegend() ? LEGEND_HEIGHT + 8 : 6),
                dungeonStateTracker.runStats(),
                snapshot,
                renderPlan
            );
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
        boolean recording
    ) {
        drawBorder(graphics, left - 3, top - 3, GRID_PIXEL_SIZE + 6, GRID_PIXEL_SIZE + 6, MAP_BORDER);

        GridViewport viewport = GridViewport.from(snapshot, renderPlan, stats);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(left + viewport.offsetX(), top + viewport.offsetY());
            graphics.pose().scale(viewport.scale(), viewport.scale());
            graphics.pose().translate(-viewport.minPixelX(), -viewport.minPixelY());
            drawGridContent(graphics, snapshot, renderPlan, stats, viewport);
            if (!recording) {
                drawPreRunStartRoom(graphics, 0, 0);
            }
            drawRoomLabels(graphics, snapshot, renderPlan, stats, viewport);
            drawPlayerMarkers(graphics, 0, 0, snapshot, stats);
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
        return Math.min(DungeonMapOverlayConfig.INSTANCE.unopenedRoomAlpha(), MAX_UNOPENED_ALPHA);
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
        int secretsFound = secretsFoundFor(match, stats);
        int secrets = match.template().secrets();
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
    }

    private static int secretsFoundFor(DungeonKnownRoomCatalog.MatchedRoom match, DungeonRunStats stats) {
        int total = 0;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            total += stats.roomSecretsFound(component.roomGridX(), component.roomGridZ());
        }
        return Math.min(total, Math.max(0, match.template().secrets()));
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
        if (door.kind() == DungeonDoorKind.OPEN) {
            color = OPEN_DOOR;
        } else if (door.colorAsSpecial()) {
            color = door.targetType().color();
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

        DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(gridX, gridZ);
        if (observedPoint == null) {
            return;
        }

        int secretsFound = renderPlan.isCompletedRoom(gridX / 2, gridZ / 2)
            ? hint.secrets()
            : stats.roomSecretsFound(gridX / 2, gridZ / 2);
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
            hint.secrets(),
            hint.crypts(),
            roomTextState(
                hint.type(),
                renderPlan.isVisitedRoom(gridX / 2, gridZ / 2),
                renderPlan.isClearedRoom(gridX / 2, gridZ / 2),
                renderPlan.isCompletedRoom(gridX / 2, gridZ / 2),
                secretsFound,
                hint.secrets()
            ),
            DungeonRoomDebugFormatter.hintOverlayLines(observedPoint.point(), gridX / 2, gridZ / 2)
        );
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

        List<String> labelLines = labelLines(label, Math.round(maxWidth / ROOM_LABEL_SCALE));
        List<RoomTextLine> textLines = new ArrayList<>();
        for (String labelLine : labelLines) {
            textLines.add(new RoomTextLine(labelLine, ROOM_LABEL_SCALE));
        }
        if (secrets > 0 && textState.showSecrets()) {
            textLines.add(new RoomTextLine(Math.min(secretsFound, secrets) + "/" + secrets, ROOM_SECRET_SCALE));
        }
        if (DungeonMapOverlayConfig.INSTANCE.debugRoomCrypts()) {
            textLines.add(new RoomTextLine("Crypts " + roomCryptText(label, crypts), ROOM_SECRET_SCALE));
        }
        if (DungeonMapOverlayConfig.INSTANCE.debugRoomMatches()) {
            for (String debugLine : debugLines) {
                textLines.add(new RoomTextLine(debugLine, 0.42F));
            }
        }

        int y = centerY - ((textLines.size() - 1) * ROOM_TEXT_LINE_STEP) / 2 - 3;
        for (int index = 0; index < textLines.size(); index++) {
            RoomTextLine line = textLines.get(index);
            drawScaledCenteredText(
                graphics,
                line.text(),
                centerX,
                y + index * ROOM_TEXT_LINE_STEP,
                textState.color(),
                line.scale(),
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
        boolean allSecretsFound = roomType != RoomType.PUZZLE && secrets > 0 && secretsFound >= secrets;
        if (completed || allSecretsFound) {
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
        Minecraft client = Minecraft.getInstance();
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] words = trimmed.split("\\s+");
        if (words.length == 1) {
            return labelSingleWord(words[0], maxWidth);
        }

        return clampLabelLines(wrapWordsOnly(words, maxWidth));
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
        Minecraft client = Minecraft.getInstance();
        graphics.text(client.font, text, centerX - client.font.width(text) / 2, y, color, shadow);
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
        if (shadow) {
            graphics.text(client.font, text, x - 1, 0, OUTLINE_TEXT, false);
            graphics.text(client.font, text, x + 1, 0, OUTLINE_TEXT, false);
            graphics.text(client.font, text, x, -1, OUTLINE_TEXT, false);
            graphics.text(client.font, text, x, 1, OUTLINE_TEXT, false);
        }
        graphics.text(client.font, text, x, 0, color, false);
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
        DungeonRunStats stats
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        long frame = ++playerMarkerFrame;
        List<MarkerCenter> drawnMarkers = new ArrayList<>();
        Set<UUID> drawnPlayerUuids = new HashSet<>();

        if (DungeonMapOverlayConfig.INSTANCE.playerTrackingEnabled()) {
            drawLoadedPlayerMarkers(graphics, left, top, client, stats, drawnMarkers, drawnPlayerUuids, frame);
        } else {
            drawPlayerMarker(graphics, left, top, client.player, classColorFor(stats, client.player.getUUID()), frame);
            int selfX = mapPixelForWorldX(client.player.getX());
            int selfY = mapPixelForWorldZ(client.player.getZ());
            drawnMarkers.add(new MarkerCenter(selfX, selfY));
            drawnPlayerUuids.add(client.player.getUUID());
            rememberIdentifiedPlayerMarker(client.player.getUUID(), selfX, selfY);
        }

        drawMapDecorationPlayerMarkers(graphics, left, top, client, snapshot, stats, drawnMarkers, drawnPlayerUuids, frame);
        if (!drawnPlayerUuids.contains(client.player.getUUID())) {
            drawPlayerMarker(graphics, left, top, client.player, classColorFor(stats, client.player.getUUID()), frame);
            int selfX = mapPixelForWorldX(client.player.getX());
            int selfY = mapPixelForWorldZ(client.player.getZ());
            drawnMarkers.add(new MarkerCenter(selfX, selfY));
            drawnPlayerUuids.add(client.player.getUUID());
            rememberIdentifiedPlayerMarker(client.player.getUUID(), selfX, selfY);
        }
        forgetOldPlayerMarkers(frame);
    }

    private static void drawLoadedPlayerMarkers(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        Minecraft client,
        DungeonRunStats stats,
        List<MarkerCenter> drawnMarkers,
        Set<UUID> drawnPlayerUuids,
        long frame
    ) {
        List<AbstractClientPlayer> players = playersForMap(client, stats);
        for (AbstractClientPlayer player : players) {
            if (drawnMarkers.size() >= MAX_PLAYER_MARKERS) {
                break;
            }
            if (drawnPlayerUuids.contains(player.getUUID())) {
                continue;
            }
            int x = mapPixelForWorldX(player.getX());
            int y = mapPixelForWorldZ(player.getZ());
            drawPlayerMarker(graphics, left, top, player, classColorFor(stats, player.getUUID()), frame);
            drawnMarkers.add(new MarkerCenter(x, y));
            drawnPlayerUuids.add(player.getUUID());
            rememberIdentifiedPlayerMarker(player.getUUID(), x, y);
        }
    }

    private static List<AbstractClientPlayer> playersForMap(Minecraft client, DungeonRunStats stats) {
        List<AbstractClientPlayer> players = new ArrayList<>();
        players.add(client.player);

        for (AbstractClientPlayer player : client.level.players()) {
            if (players.size() >= MAX_PLAYER_MARKERS) {
                break;
            }
            if (player == client.player || player.getUUID().equals(client.player.getUUID())) {
                continue;
            }
            if (!shouldDrawLoadedPlayer(client, stats, player)) {
                continue;
            }
            players.add(player);
        }
        return players;
    }

    private static boolean shouldDrawLoadedPlayer(Minecraft client, DungeonRunStats stats, AbstractClientPlayer player) {
        if (player.isInvisible()) {
            return false;
        }
        if (client.getConnection() == null || client.getConnection().getPlayerInfo(player.getUUID()) == null) {
            return false;
        }
        return isLikelyMinecraftPlayerName(player.getName().getString());
    }

    private static boolean isLikelyMinecraftPlayerName(String name) {
        if (name == null || name.length() < 3 || name.length() > 16 || isIgnoredDungeonNpcName(name)) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            boolean valid = (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIgnoredDungeonNpcName(String name) {
        return name.equalsIgnoreCase("Mort")
            || name.equalsIgnoreCase("Tomioka")
            || name.equalsIgnoreCase("Duncan")
            || name.equalsIgnoreCase("Zodd")
            || name.equalsIgnoreCase("Trinity");
    }

    private static void drawPlayerMarker(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        AbstractClientPlayer player,
        int classColor,
        long frame
    ) {
        Minecraft client = Minecraft.getInstance();
        int headSize = client.player != null && player.getUUID().equals(client.player.getUUID())
            ? PLAYER_HEAD_SIZE
            : TEAMMATE_HEAD_SIZE;
        MarkerPose pose = smoothMarker(
            "entity:" + player.getUUID(),
            mapPixelForWorldX(player.getX()),
            mapPixelForWorldZ(player.getZ()),
            player.getYRot(),
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

    private static int drawMapDecorationPlayerMarkers(
        GuiGraphicsExtractor graphics,
        int left,
        int top,
        Minecraft client,
        DungeonMapSnapshot snapshot,
        DungeonRunStats stats,
        List<MarkerCenter> drawnMarkers,
        Set<UUID> drawnPlayerUuids,
        long frame
    ) {
        MapItemSavedData mapData = dungeonMapData(client);
        if (mapData == null || client.player == null) {
            return 0;
        }

        int drawn = 0;
        int decorationIndex = 0;
        for (MapDecoration decoration : mapData.getDecorations()) {
            String markerKey = "map:" + decorationIndex++;
            if (drawnMarkers.size() >= MAX_PLAYER_MARKERS || !shouldDrawMapDecorationPlayer(client, stats, decoration)) {
                continue;
            }

            DungeonMapCheckmarkReader.MapPixel decorationPixel =
                overlayPixelForDecorationOrRaw(decoration, client, snapshot);
            UUID playerUuid = mapDecorationPlayerUuid(
                client,
                stats,
                decoration,
                decorationPixel,
                drawnPlayerUuids
            );
            boolean selfDecoration = isSelfDecoration(client, decoration, decorationPixel);
            String playerName = decorationPlayerName(decoration);
            if (playerUuid != null && drawnPlayerUuids.contains(playerUuid)) {
                continue;
            }

            AbstractClientPlayer loadedPlayer = playerByUuid(client, playerUuid);
            DungeonMapCheckmarkReader.MapPixel markerPixel = loadedPlayer == null
                ? decorationPixel
                : new DungeonMapCheckmarkReader.MapPixel(
                    mapPixelForWorldX(loadedPlayer.getX()),
                    mapPixelForWorldZ(loadedPlayer.getZ())
                );
            if (markerPixel == null) {
                continue;
            }
            int x = markerPixel.x();
            int y = markerPixel.z();
            if (nearLoadedMarker(x, y, drawnMarkers)) {
                continue;
            }
            MarkerPose pose = smoothMarker(
                playerUuid == null ? markerKey : "player:" + playerUuid,
                x,
                y,
                loadedPlayer == null ? decoration.rot() * 360.0F / 16.0F : loadedPlayer.getYRot(),
                frame
            );

            drawFallbackPlayerMarker(
                graphics,
                left + pose.x(),
                top + pose.y(),
                skinForMarker(client, playerUuid, loadedPlayer),
                pose.rotation(),
                playerUuid == null ? UNKNOWN_CLASS_BORDER : classColorFor(stats, playerUuid),
                playerUuid != null && playerUuid.equals(client.player.getUUID()) ? PLAYER_HEAD_SIZE : TEAMMATE_HEAD_SIZE
            );
            drawnMarkers.add(new MarkerCenter(x, y));
            if (playerUuid != null) {
                drawnPlayerUuids.add(playerUuid);
                if (loadedPlayer != null || selfDecoration || !playerName.isEmpty() || nearRememberedPlayerMarker(playerUuid, decorationPixel)) {
                    rememberIdentifiedPlayerMarker(playerUuid, x, y);
                }
            }
            drawn++;
        }
        return drawn;
    }

    private static boolean isPlayerDecoration(MapDecoration decoration) {
        return decoration.type().equals(MapDecorationTypes.PLAYER)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)
            || decoration.type().equals(MapDecorationTypes.BLUE_MARKER)
            || decoration.type().equals(MapDecorationTypes.FRAME);
    }

    private static boolean shouldDrawMapDecorationPlayer(
        Minecraft client,
        DungeonRunStats stats,
        MapDecoration decoration
    ) {
        if (!isPlayerDecoration(decoration) || client.player == null) {
            return false;
        }

        String name = decorationPlayerName(decoration);
        if (name.isEmpty()) {
            return true;
        }
        return !name.isEmpty()
            && (name.equalsIgnoreCase(client.player.getName().getString()) || stats.isKnownDungeonPlayer(name));
    }

    private static UUID mapDecorationPlayerUuid(
        Minecraft client,
        DungeonRunStats stats,
        MapDecoration decoration,
        DungeonMapCheckmarkReader.MapPixel markerPixel,
        Set<UUID> assignedUuids
    ) {
        String name = decorationPlayerName(decoration);
        if (!name.isEmpty()) {
            if (name.equalsIgnoreCase(client.player.getName().getString())) {
                return client.player.getUUID();
            }
            return stats.dungeonPlayerUuid(name);
        }
        if (isSelfDecoration(client, decoration, markerPixel)) {
            return client.player.getUUID();
        }
        UUID nearestLoadedPlayerUuid = nearestLoadedDungeonPlayerUuid(client, stats, markerPixel, assignedUuids);
        if (nearestLoadedPlayerUuid != null) {
            return nearestLoadedPlayerUuid;
        }
        UUID rememberedPlayerUuid = nearestRememberedDungeonPlayerUuid(stats, markerPixel, assignedUuids);
        if (rememberedPlayerUuid != null) {
            return rememberedPlayerUuid;
        }
        return null;
    }

    private static UUID nearestLoadedDungeonPlayerUuid(
        Minecraft client,
        DungeonRunStats stats,
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
            if (uuid.equals(client.player.getUUID()) || assignedUuids.contains(uuid) || !shouldDrawLoadedPlayer(client, stats, player)) {
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

    private static UUID nearestRememberedDungeonPlayerUuid(
        DungeonRunStats stats,
        DungeonMapCheckmarkReader.MapPixel markerPixel,
        Set<UUID> assignedUuids
    ) {
        if (markerPixel == null) {
            return null;
        }

        Set<UUID> dungeonUuids = stats.knownDungeonPlayerUuids();
        UUID bestUuid = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<UUID, MarkerCenter> entry : LAST_IDENTIFIED_PLAYER_MARKERS.entrySet()) {
            UUID uuid = entry.getKey();
            if (assignedUuids.contains(uuid) || !dungeonUuids.contains(uuid)) {
                continue;
            }

            MarkerCenter marker = entry.getValue();
            int dx = marker.x() - markerPixel.x();
            int dz = marker.y() - markerPixel.z();
            int distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestUuid = uuid;
            }
        }

        return bestDistance <= PLAYER_IDENTITY_MEMORY_DISTANCE * PLAYER_IDENTITY_MEMORY_DISTANCE ? bestUuid : null;
    }

    private static boolean nearRememberedPlayerMarker(UUID uuid, DungeonMapCheckmarkReader.MapPixel markerPixel) {
        if (uuid == null || markerPixel == null) {
            return false;
        }
        MarkerCenter remembered = LAST_IDENTIFIED_PLAYER_MARKERS.get(uuid);
        if (remembered == null) {
            return false;
        }
        int dx = remembered.x() - markerPixel.x();
        int dz = remembered.y() - markerPixel.z();
        return dx * dx + dz * dz <= PLAYER_IDENTITY_MEMORY_DISTANCE * PLAYER_IDENTITY_MEMORY_DISTANCE;
    }

    private static void rememberIdentifiedPlayerMarker(UUID uuid, int x, int y) {
        if (uuid != null) {
            LAST_IDENTIFIED_PLAYER_MARKERS.put(uuid, new MarkerCenter(x, y));
        }
    }

    private static boolean isSelfDecoration(
        Minecraft client,
        MapDecoration decoration,
        DungeonMapCheckmarkReader.MapPixel markerPixel
    ) {
        String name = decorationPlayerName(decoration);
        if (!name.isEmpty() && client.player != null && name.equalsIgnoreCase(client.player.getName().getString())) {
            return true;
        }
        if (client.player == null || markerPixel == null) {
            return false;
        }

        int dx = mapPixelForWorldX(client.player.getX()) - markerPixel.x();
        int dz = mapPixelForWorldZ(client.player.getZ()) - markerPixel.z();
        return dx * dx + dz * dz <= PLAYER_IDENTITY_MATCH_DISTANCE * PLAYER_IDENTITY_MATCH_DISTANCE;
    }

    private static AbstractClientPlayer playerByUuid(Minecraft client, UUID uuid) {
        if (client.level == null || uuid == null) {
            return null;
        }
        return client.level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer player ? player : null;
    }

    private static PlayerSkin skinForMarker(Minecraft client, UUID uuid, AbstractClientPlayer loadedPlayer) {
        if (loadedPlayer != null) {
            return loadedPlayer.getSkin();
        }
        if (client.getConnection() != null && uuid != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin();
            }
        }
        return uuid == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(uuid);
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

    private static boolean nearLoadedMarker(int x, int y, List<MarkerCenter> loadedMarkers) {
        for (MarkerCenter marker : loadedMarkers) {
            int dx = marker.x() - x;
            int dy = marker.y() - y;
            if (dx * dx + dy * dy <= 36) {
                return true;
            }
        }
        return false;
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

    private static MarkerPose smoothMarker(String key, int targetX, int targetY, float targetRotation, long frame) {
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

    private static int classColorFor(DungeonRunStats stats, java.util.UUID uuid) {
        DungeonRunStats.PlayerStats playerStats = stats.playerStats(uuid);
        DungeonRunStats.DungeonClass dungeonClass = playerStats == null
            ? DungeonRunStats.DungeonClass.UNKNOWN
            : playerStats.dungeonClass();
        return switch (dungeonClass) {
            case ARCHER -> 0xFFFF2D32;
            case BERSERKER -> 0xFFFF9829;
            case MAGE -> 0xFF62DFFF;
            case HEALER -> 0xFFD68CFF;
            case TANK -> 0xFF34A853;
            default -> UNKNOWN_CLASS_BORDER;
        };
    }

    private static MapItemSavedData dungeonMapData(Minecraft client) {
        return DungeonMapItems.mapData(client);
    }

    private static int mapPixelForWorldX(double worldX) {
        double roomProgress = (worldX - DungeonScanUtils.START_X) / DungeonScanUtils.ROOM_SIZE_BLOCKS;
        return Math.clamp(
            (int) Math.round(ROOM_SIZE / 2.0 + roomProgress * pixelsPerRoom()),
            0,
            GRID_PIXEL_SIZE
        );
    }

    private static int mapPixelForWorldZ(double worldZ) {
        double roomProgress = (worldZ - DungeonScanUtils.START_Z) / DungeonScanUtils.ROOM_SIZE_BLOCKS;
        return Math.clamp(
            (int) Math.round(ROOM_SIZE / 2.0 + roomProgress * pixelsPerRoom()),
            0,
            GRID_PIXEL_SIZE
        );
    }

    private static int pixelsPerRoom() {
        return ROOM_SIZE + DOOR_SIZE + CELL_GAP * 2;
    }

    private static void drawLegend(GuiGraphicsExtractor graphics, int left, int top) {
        int x = left;
        x = drawLegendItem(graphics, x, top, RoomType.START, "start");
        x = drawLegendItem(graphics, x, top, RoomType.NORMAL, "normal");
        x = drawLegendItem(graphics, x, top, RoomType.PUZZLE, "puzzle");
        x = drawLegendItem(graphics, x, top, RoomType.FAIRY, "fairy");
        x = drawLegendItem(graphics, x, top, RoomType.TRAP, "trap");
        x = drawLegendItem(graphics, x, top, RoomType.BLOOD, "blood");
        drawLegendItem(graphics, x, top, RoomType.YELLOW, "yellow");
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
        int sPlusSecrets = stats.sPlusSecretTarget(renderPlan, fullSecrets);
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
        x = drawFooterText(graphics, " | Crypts: ", x, secondLineY, TEXT);
        x = drawFooterText(graphics, String.valueOf(stats.cryptsOpened()), x, secondLineY, cryptColor(stats.cryptsOpened()));
        x = drawFooterText(graphics, "/", x, secondLineY, TEXT);
        drawFooterText(graphics, cryptTotalText(stats, snapshot, renderPlan), x, secondLineY, TEXT);
    }

    private static int displayedSecretsFound(DungeonRunStats stats, int secretsAvailable) {
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
        if (stats.secretsAvailable() > 0) {
            return stats.secretsAvailable();
        }
        if (stats.secretsTotalAvailable() > 0) {
            return stats.secretsTotalAvailable();
        }
        return Math.max(0, estimatedSecretsAvailable);
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

    public static OverlayBounds overlayBounds(DungeonMapOverlayConfig config) {
        float scale = effectiveScale(config);
        int height = overlayContentHeight(config);
        return new OverlayBounds(
            Math.round(config.x() - 5 * scale),
            Math.round(config.y() - 5 * scale),
            Math.round(overlayContentWidth() * scale),
            Math.round((height + 5) * scale)
        );
    }

    private static float effectiveScale(DungeonMapOverlayConfig config) {
        float requestedScale = Math.clamp(config.scale(), 25, MAX_CONFIG_SCALE) / 100.0F;
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        float maxWidthScale = screenWidth <= 0
            ? requestedScale
            : (screenWidth * MAX_SCREEN_WIDTH_FRACTION) / overlayContentWidth();
        float maxHeightScale = screenHeight <= 0
            ? requestedScale
            : (screenHeight * MAX_SCREEN_HEIGHT_FRACTION) / (overlayContentHeight(config) + 5);
        return Math.clamp(Math.min(requestedScale, Math.min(maxWidthScale, maxHeightScale)), 0.25F, MAX_CONFIG_SCALE / 100.0F);
    }

    private static int overlayContentWidth() {
        return GRID_PIXEL_SIZE + 10;
    }

    private static int overlayContentHeight(DungeonMapOverlayConfig config) {
        return GRID_PIXEL_SIZE
            + (config.showLegend() ? LEGEND_HEIGHT : 0)
            + FOOTER_HEIGHT
            + 10;
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
                for (MapDecoration decoration : mapData.getDecorations()) {
                    if (!isPlayerDecoration(decoration)) {
                        continue;
                    }
                    DungeonMapCheckmarkReader.MapPixel pixel =
                        overlayPixelForDecorationOrRaw(decoration, client, snapshot);
                    if (pixel != null) {
                        bounds.includePixel(pixel.x(), pixel.z());
                    }
                }
            }

            if (!DungeonMapOverlayConfig.INSTANCE.playerTrackingEnabled()) {
                return;
            }
            for (AbstractClientPlayer player : playersForMap(client, stats)) {
                bounds.includePixel(mapPixelForWorldX(player.getX()), mapPixelForWorldZ(player.getZ()));
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
            if (hasUncertainWikiCrypts(match.template().name())) {
                hasUnknownRooms = true;
            }
            knownTotal += Math.max(0, match.template().crypts());
        }
        for (DungeonKnownRoomCatalog.KnownCoreHint hint : renderPlan.hints().values()) {
            if (hasUncertainWikiCrypts(hint.name())) {
                hasUnknownRooms = true;
            }
            knownTotal += Math.max(0, hint.crypts());
        }
        return new CryptEstimate(knownTotal, hasUnknownRooms);
    }

    private static boolean hasUncertainWikiCrypts(String roomName) {
        return roomName.equalsIgnoreCase("Admin")
            || roomName.equalsIgnoreCase("Buttons");
    }

    private static String roomCryptText(String roomName, int crypts) {
        return hasUncertainWikiCrypts(roomName)
            ? "?"
            : String.valueOf(Math.max(0, crypts));
    }

    private record CryptEstimate(int knownTotal, boolean hasUnknownRooms) {
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

    private record MarkerCenter(int x, int y) {
    }

    private record MarkerPose(float x, float y, float rotation) {
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
