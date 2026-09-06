package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;

public final class DungeonLiveMapWriter {
    private static final String LIVE_MAP_FILE = "live-dungeon-map.png";
    private static final String FINAL_MAP_FILE = "last-dungeon-map.png";
    private static final String LIVE_HTML_FILE = "live-dungeon-map.html";
    private static final int ROOM_SIZE = 54;
    private static final int DOOR_SIZE = 18;
    private static final int CELL_GAP = 3;
    private static final int ROOM_LABEL_FONT_SIZE = 12;
    private static final int ROOM_SECRET_FONT_SIZE = 11;
    private static final int MAX_ROOM_LABEL_LINES = 3;
    private static final int LONG_WORD_SPLIT_MIN_CHARS = 12;
    private static final int PADDING = 22;
    private static final int HEADER_HEIGHT = 44;
    private static final int LEGEND_HEIGHT = 42;
    private static final int GRID_UNITS = DungeonScanUtils.SCAN_GRID_SIZE;
    private static final int IMAGE_WIDTH = PADDING * 2 + scanGridToPixel(GRID_UNITS);
    private static final int IMAGE_HEIGHT = PADDING * 2 + HEADER_HEIGHT + LEGEND_HEIGHT + scanGridToPixel(GRID_UNITS);

    private static final Color BACKGROUND = new Color(0xFF14171C, true);
    private static final Color PANEL = new Color(0xFF20242B, true);
    private static final Color EMPTY = new Color(0xFF2A2E36, true);
    private static final Color UNSEEN = new Color(0xFF111318, true);
    private static final Color OPEN_DOOR = new Color(0xFF72451F, true);
    private static final Color WITHER_DOOR = new Color(0xFF050506, true);
    private static final Color UNOPENED_ROOM = new Color(0xFF4B5059, true);
    private static final Color TEXT = new Color(0xFFE9EDF2, true);
    private static final Color MUTED_TEXT = new Color(0xFF99A1AD, true);
    private static final Color PLAYER = new Color(0xFFFFFFFF, true);

    public void writeLiveSnapshot(String runTimestamp, DungeonMapSnapshot snapshot) {
        try {
            Path directory = outputDirectory();
            Files.createDirectories(directory);
            writeHtml(directory);
            writeImage(directory.resolve(LIVE_MAP_FILE), runTimestamp, snapshot, false);
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to write live dungeon map.", exception);
        }
    }

    public void writeFinalSnapshot(String runTimestamp, DungeonMapSnapshot snapshot) {
        if (runTimestamp == null || snapshot.lastScanNumber() < 0) {
            return;
        }

        try {
            Path directory = outputDirectory();
            Files.createDirectories(directory);
            writeImage(directory.resolve(FINAL_MAP_FILE), runTimestamp, snapshot, true);
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to write final dungeon map snapshot.", exception);
        }
    }

    static boolean hasUnknownRooms(DungeonMapSnapshot snapshot) {
        return MatchRenderPlan.from(snapshot).hasUnknownRooms(snapshot);
    }

    private static void writeImage(
        Path file,
        String runTimestamp,
        DungeonMapSnapshot snapshot,
        boolean finalSnapshot
    ) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            drawHeader(graphics, runTimestamp, snapshot, finalSnapshot);
            drawGrid(graphics, snapshot);
            drawLegend(graphics);
        } finally {
            graphics.dispose();
        }

        ImageIO.write(image, "png", file.toFile());
    }

    private static void drawHeader(
        Graphics2D graphics,
        String runTimestamp,
        DungeonMapSnapshot snapshot,
        boolean finalSnapshot
    ) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        graphics.setColor(TEXT);
        graphics.drawString(finalSnapshot ? "Kung dungeon scan - final" : "Kung dungeon scan - live", PADDING, 27);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.setColor(MUTED_TEXT);
        String details = "run " + runTimestamp
            + " | scan " + snapshot.lastScanNumber()
            + " | rooms " + snapshot.observedRoomCount()
            + " | player " + snapshot.playerGridX() + "," + snapshot.playerGridZ();
        graphics.drawString(details, PADDING, 43);
    }

    private static void drawGrid(Graphics2D graphics, DungeonMapSnapshot snapshot) {
        int top = PADDING + HEADER_HEIGHT;
        int left = PADDING;
        MatchRenderPlan renderPlan = MatchRenderPlan.from(snapshot);

        graphics.setColor(PANEL);
        graphics.fillRect(left - 8, top - 8, scanGridToPixel(GRID_UNITS) + 16, scanGridToPixel(GRID_UNITS) + 16);

        for (int gridZ = 0; gridZ < DungeonScanUtils.SCAN_GRID_SIZE; gridZ++) {
            for (int gridX = 0; gridX < DungeonScanUtils.SCAN_GRID_SIZE; gridX++) {
                if (DungeonScanUtils.isRoomScanPoint(gridX, gridZ)
                    && renderPlan.isMatchedRoomCell(gridX / 2, gridZ / 2)) {
                    continue;
                }

                int x = left + scanGridToPixel(gridX);
                int y = top + scanGridToPixel(gridZ);
                int size = sizeFor(gridX, gridZ);
                if (DungeonScanUtils.isDoorScanPoint(gridX, gridZ)
                    && renderPlan.isInternalDoor(gridX, gridZ)) {
                    graphics.setColor(PANEL);
                    graphics.fillRect(x, y, size, size);
                    continue;
                }

                drawCell(graphics, snapshot, renderPlan, gridX, gridZ, x, y, size);
            }
        }

        drawMatchedRooms(graphics, left, top, renderPlan);
        drawExternalDoors(graphics, left, top, renderPlan.externalDoors());
    }

    private static void drawMatchedRooms(
        Graphics2D graphics,
        int left,
        int top,
        MatchRenderPlan renderPlan
    ) {
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            Color roomColor = new Color(match.template().type().color(), true);
            Color unopenedColor = unopenedColorFor(roomColor);

            for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                graphics.setColor(renderPlan.isVisitedRoom(component.roomGridX(), component.roomGridZ())
                    ? roomColor
                    : unopenedColor);
                int x = left + scanGridToPixel(component.roomGridX() * 2);
                int y = top + scanGridToPixel(component.roomGridZ() * 2);
                graphics.fillRect(x, y, ROOM_SIZE, ROOM_SIZE);
            }

            drawInternalRoomConnections(graphics, left, top, renderPlan, match, roomColor, unopenedColor);
            drawInternalRoomCorners(graphics, left, top, renderPlan, match, roomColor, unopenedColor);
            drawMatchedRoomLabel(graphics, left, top, match);
        }
    }

    private static void drawInternalRoomConnections(
        Graphics2D graphics,
        int left,
        int top,
        MatchRenderPlan renderPlan,
        DungeonKnownRoomCatalog.MatchedRoom match,
        Color roomColor,
        Color unopenedColor
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
                graphics.setColor(renderPlan.isVisitedRoom(first.roomGridX(), first.roomGridZ())
                    || renderPlan.isVisitedRoom(second.roomGridX(), second.roomGridZ())
                    ? roomColor
                    : unopenedColor);
                if (isHorizontalDoor(doorGridX, doorGridZ)) {
                    graphics.fillRect(x - CELL_GAP, y, DOOR_SIZE + CELL_GAP * 2, ROOM_SIZE);
                } else {
                    graphics.fillRect(x, y - CELL_GAP, ROOM_SIZE, DOOR_SIZE + CELL_GAP * 2);
                }
            }
        }
    }

    private static void drawInternalRoomCorners(
        Graphics2D graphics,
        int left,
        int top,
        MatchRenderPlan renderPlan,
        DungeonKnownRoomCatalog.MatchedRoom match,
        Color roomColor,
        Color unopenedColor
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
                graphics.setColor(
                    renderPlan.isVisitedRoom(roomGridX, roomGridZ)
                        || renderPlan.isVisitedRoom(roomGridX + 1, roomGridZ)
                        || renderPlan.isVisitedRoom(roomGridX, roomGridZ + 1)
                        || renderPlan.isVisitedRoom(roomGridX + 1, roomGridZ + 1)
                        ? roomColor
                        : unopenedColor
                );
                graphics.fillRect(x - CELL_GAP, y - CELL_GAP, DOOR_SIZE + CELL_GAP * 2, DOOR_SIZE + CELL_GAP * 2);
            }
        }
    }

    private static void drawMatchedRoomLabel(
        Graphics2D graphics,
        int left,
        int top,
        DungeonKnownRoomCatalog.MatchedRoom match
    ) {
        if (!shouldDrawRoomText(match.template().type())) {
            return;
        }

        LabelPlacement placement = labelPlacementFor(match, left, top);
        drawRoomText(
            graphics,
            placement.centerX(),
            placement.centerY(),
            placement.maxWidth(),
            match.template().name(),
            match.template().type(),
            match.template().secrets()
        );
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
        return Math.clamp(width - 8, ROOM_SIZE - 6, ROOM_SIZE * 2 + DOOR_SIZE);
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

    private static void drawOutlinedString(Graphics2D graphics, String text, int x, int y) {
        graphics.setColor(new Color(0xEE000000, true));
        graphics.drawString(text, x - 1, y);
        graphics.drawString(text, x + 1, y);
        graphics.drawString(text, x, y - 1);
        graphics.drawString(text, x, y + 1);
        graphics.drawString(text, x - 1, y - 1);
        graphics.drawString(text, x + 1, y - 1);
        graphics.drawString(text, x - 1, y + 1);
        graphics.drawString(text, x + 1, y + 1);

        graphics.setColor(new Color(0xFFF7F7F7, true));
        graphics.drawString(text, x, y);
    }

    private static String trimToWidth(Graphics2D graphics, String value, int maxWidth) {
        if (graphics.getFontMetrics().stringWidth(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        for (int end = value.length(); end > 0; end--) {
            String candidate = value.substring(0, end) + suffix;
            if (graphics.getFontMetrics().stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    private static void drawExternalDoors(Graphics2D graphics, int left, int top, Map<CellKey, DoorRenderInfo> doors) {
        for (Map.Entry<CellKey, DoorRenderInfo> entry : doors.entrySet()) {
            graphics.setColor(colorForDoor(entry.getValue()));
            CellKey door = entry.getKey();
            drawDoorConnection(graphics, left, top, door.x(), door.z());
        }
    }

    private static Color colorForDoor(DoorRenderInfo door) {
        Color color;
        if (door.kind() == DungeonDoorKind.OPEN) {
            color = OPEN_DOOR;
        } else if (door.colorAsSpecial()) {
            color = new Color(door.targetType().color(), true);
        } else {
            color = switch (door.kind()) {
                case WITHER -> WITHER_DOOR;
                case BLOOD -> new Color(RoomType.BLOOD.color(), true);
                default -> PANEL;
            };
        }

        if (door.colorAsSpecial()
            || door.kind() == DungeonDoorKind.BLOOD
            || door.kind() == DungeonDoorKind.WITHER) {
            return color;
        }
        return door.targetVisited() ? color : unopenedColorFor(color);
    }

    private static Color unopenedColorFor(Color roomColor) {
        return new Color(
            (roomColor.getRed() + UNOPENED_ROOM.getRed() * 3) / 4,
            (roomColor.getGreen() + UNOPENED_ROOM.getGreen() * 3) / 4,
            (roomColor.getBlue() + UNOPENED_ROOM.getBlue() * 3) / 4,
            0xFF
        );
    }

    private static boolean isSpecialDoorTarget(RoomType roomType) {
        return roomType != null
            && roomType != RoomType.NORMAL
            && roomType != RoomType.START
            && roomType != RoomType.UNKNOWN;
    }

    private static void drawDoorConnection(Graphics2D graphics, int left, int top, int gridX, int gridZ) {
        int x = left + scanGridToPixel(gridX);
        int y = top + scanGridToPixel(gridZ);

        if (isHorizontalDoor(gridX, gridZ)) {
            int centeredY = y + (ROOM_SIZE - DOOR_SIZE) / 2;
            graphics.fillRect(x - CELL_GAP, centeredY, DOOR_SIZE + CELL_GAP * 2, DOOR_SIZE);
            return;
        }

        int centeredX = x + (ROOM_SIZE - DOOR_SIZE) / 2;
        graphics.fillRect(centeredX, y - CELL_GAP, DOOR_SIZE, DOOR_SIZE + CELL_GAP * 2);
    }

    private static void drawCell(
        Graphics2D graphics,
        DungeonMapSnapshot snapshot,
        MatchRenderPlan renderPlan,
        int gridX,
        int gridZ,
        int x,
        int y,
        int size
    ) {
        DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(gridX, gridZ);
        if (observedPoint == null) {
            graphics.setColor(UNSEEN);
            graphics.fillRect(x, y, size, size);
            return;
        }

        DungeonScanPoint point = observedPoint.point();
        if (point.kind() == DungeonScanPointKind.SEPARATOR) {
            graphics.setColor(PANEL);
            graphics.fillRect(x, y, size, size);
            return;
        }

        if (point.kind() == DungeonScanPointKind.DOOR) {
            graphics.setColor(PANEL);
            graphics.fillRect(x, y, DOOR_SIZE, DOOR_SIZE);
            return;
        }

        if (DungeonRoomClassifier.isEmptyCore(point.coreHash())) {
            graphics.setColor(EMPTY);
            graphics.fillRect(x, y, size, size);
            return;
        }

        DungeonKnownRoomCatalog.KnownCoreHint hint = renderPlan.hintAt(gridX / 2, gridZ / 2);
        if (hint != null) {
            Color roomColor = new Color(hint.type().color(), true);
            graphics.setColor(renderPlan.isVisitedRoom(gridX / 2, gridZ / 2)
                ? roomColor
                : unopenedColorFor(roomColor));
            graphics.fillRect(x, y, size, size);
            drawCellHintLabel(graphics, x, y, size, hint);
            if (gridX == snapshot.playerGridX() * 2 && gridZ == snapshot.playerGridZ() * 2) {
                drawPlayerMarker(graphics, x, y, size);
            }
            return;
        }

        RoomType roomType = snapshot.isStartRoom(gridX, gridZ)
            ? RoomType.START
            : RoomType.UNKNOWN;
        if (roomType == RoomType.UNKNOWN) {
            graphics.setColor(EMPTY);
            graphics.fillRect(x, y, size, size);
            drawUnknownRoomMarker(graphics, x, y, size);
            return;
        }

        graphics.setColor(new Color(roomType.color(), true));
        graphics.fillRect(x, y, size, size);

        if (gridX == snapshot.playerGridX() * 2 && gridZ == snapshot.playerGridZ() * 2) {
            drawPlayerMarker(graphics, x, y, size);
        }
    }

    private static void drawCellHintLabel(
        Graphics2D graphics,
        int x,
        int y,
        int size,
        DungeonKnownRoomCatalog.KnownCoreHint hint
    ) {
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        if (shouldDrawRoomText(hint.type())) {
            drawRoomText(graphics, centerX, centerY, size - 6, hint.name(), hint.type(), hint.secrets());
        }
    }

    private static void drawRoomText(
        Graphics2D graphics,
        int centerX,
        int centerY,
        int maxWidth,
        String label,
        RoomType roomType,
        int secrets
    ) {
        if (!shouldDrawRoomText(roomType)) {
            return;
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, ROOM_LABEL_FONT_SIZE));
        List<String> labelLines = labelLines(graphics, label, maxWidth);
        int firstLabelY = secrets <= 0
            ? centerY + (labelLines.size() == 1 ? 4 : -3)
            : labelLines.size() == 1 ? centerY - 3 : centerY - 10;
        for (int index = 0; index < labelLines.size(); index++) {
            String line = labelLines.get(index);
            int labelX = centerX - graphics.getFontMetrics().stringWidth(line) / 2;
            drawOutlinedString(graphics, line, labelX, firstLabelY + index * 13);
        }

        if (secrets <= 0) {
            return;
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, ROOM_SECRET_FONT_SIZE));
        String secretText = "0/" + secrets;
        int secretsX = centerX - graphics.getFontMetrics().stringWidth(secretText) / 2;
        int secretsY = labelLines.size() == 1 ? centerY + 11 : centerY + 17;
        drawOutlinedString(graphics, secretText, secretsX, secretsY);
    }

    private static boolean shouldDrawRoomText(RoomType roomType) {
        return roomType != RoomType.BLOOD && roomType != RoomType.FAIRY;
    }

    private static List<String> labelLines(Graphics2D graphics, String value, int maxWidth) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] words = trimmed.split("\\s+");
        if (words.length == 1) {
            if (words[0].length() >= LONG_WORD_SPLIT_MIN_CHARS
                && graphics.getFontMetrics().stringWidth(words[0]) > maxWidth) {
                return List.of(trimToWidth(graphics, words[0], maxWidth));
            }
            return List.of(words[0]);
        }

        List<String> lines = new ArrayList<>();
        String currentLine = "";
        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (!currentLine.isEmpty() && graphics.getFontMetrics().stringWidth(candidate) > maxWidth) {
                lines.add(currentLine);
                currentLine = word;
            } else {
                currentLine = candidate;
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        if (lines.size() <= MAX_ROOM_LABEL_LINES) {
            return lines;
        }
        List<String> clamped = new ArrayList<>(lines.subList(0, MAX_ROOM_LABEL_LINES));
        int last = clamped.size() - 1;
        clamped.set(last, clamped.get(last) + "..");
        return clamped;
    }

    private static void drawUnknownRoomMarker(Graphics2D graphics, int x, int y, int size) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        String marker = "?";
        int markerX = x + size / 2 - graphics.getFontMetrics().stringWidth(marker) / 2;
        int markerY = y + size / 2 + graphics.getFontMetrics().getAscent() / 3;
        graphics.setColor(MUTED_TEXT);
        graphics.drawString(marker, markerX, markerY);
    }

    private static boolean isRealRoom(DungeonMapSnapshot snapshot, int gridX, int gridZ) {
        DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(gridX, gridZ);
        if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
            return false;
        }
        return !DungeonRoomClassifier.isEmptyCore(observedPoint.point().coreHash());
    }

    private static boolean isHorizontalDoor(int gridX, int gridZ) {
        return !isEven(gridX) && isEven(gridZ);
    }

    private static void drawPlayerMarker(Graphics2D graphics, int x, int y, int size) {
        graphics.setColor(PLAYER);
        graphics.setStroke(new BasicStroke(3));
        graphics.drawOval(x + size / 2 - 8, y + size / 2 - 8, 16, 16);
        graphics.setStroke(new BasicStroke(1));
    }

    private static void drawLegend(Graphics2D graphics) {
        int y = IMAGE_HEIGHT - LEGEND_HEIGHT + 12;
        int x = PADDING;
        x = drawLegendItem(graphics, x, y, RoomType.START, "start");
        x = drawLegendItem(graphics, x, y, RoomType.NORMAL, "normal");
        x = drawLegendItem(graphics, x, y, RoomType.PUZZLE, "puzzle");
        x = drawLegendItem(graphics, x, y, RoomType.FAIRY, "fairy");
        x = drawLegendItem(graphics, x, y, RoomType.TRAP, "trap");
        x = drawLegendItem(graphics, x, y, RoomType.BLOOD, "blood");
        drawLegendItem(graphics, x, y, RoomType.YELLOW, "yellow");
    }

    private static int drawLegendItem(Graphics2D graphics, int x, int y, RoomType roomType, String label) {
        graphics.setColor(new Color(roomType.color(), true));
        graphics.fillRect(x, y, 12, 12);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.setColor(MUTED_TEXT);
        graphics.drawString(label, x + 17, y + 11);
        return x + 74;
    }

    private static void writeHtml(Path directory) throws IOException {
        Path html = directory.resolve(LIVE_HTML_FILE);
        if (Files.exists(html)) {
            return;
        }

        Files.writeString(
            html,
            """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>Kung Dungeon Scan</title>
                    <style>
                        html, body {
                            margin: 0;
                            min-height: 100%;
                            background: #0f1115;
                            color: #e9edf2;
                            font-family: system-ui, Segoe UI, sans-serif;
                        }
                        body {
                            display: grid;
                            place-items: center;
                        }
                        img {
                            max-width: 100vw;
                            max-height: 100vh;
                            image-rendering: crisp-edges;
                        }
                    </style>
                </head>
                <body>
                    <img id="map" src="live-dungeon-map.png" alt="Kung dungeon scan">
                    <script>
                        const image = document.getElementById("map");
                        setInterval(() => {
                            image.src = "live-dungeon-map.png?t=" + Date.now();
                        }, 1000);
                    </script>
                </body>
                </html>
                """,
            StandardCharsets.UTF_8
        );
    }

    private static Path outputDirectory() {
        return FabricLoader.getInstance()
            .getGameDir()
            .resolve("kung-dungeon-scans")
            .resolve("tmp");
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

    private static boolean isEven(int value) {
        return (value & 1) == 0;
    }

    static record MatchRenderPlan(
        List<DungeonKnownRoomCatalog.MatchedRoom> matches,
        Set<CellKey> matchedRoomCells,
        Set<CellKey> internalDoors,
        Map<CellKey, DoorRenderInfo> externalDoors,
        Map<CellKey, RoomType> roomTypes,
        Map<CellKey, String> roomOwners,
        Map<CellKey, DungeonKnownRoomCatalog.KnownCoreHint> hints,
        Set<CellKey> visitedRooms,
        Set<CellKey> clearedRooms,
        Set<CellKey> completedRooms
    ) {
        static MatchRenderPlan from(DungeonMapSnapshot snapshot) {
            List<DungeonKnownRoomCatalog.MatchedRoom> matches = DungeonKnownRoomCatalog.matchKnownRooms(snapshot);
            Set<CellKey> matchedRoomCells = new HashSet<>();
            Set<CellKey> internalDoors = new HashSet<>();
            Map<CellKey, DoorRenderInfo> externalDoors = new HashMap<>();
            Map<CellKey, RoomType> roomTypes = new HashMap<>();
            Map<CellKey, String> roomOwners = new HashMap<>();
            Map<CellKey, DungeonKnownRoomCatalog.KnownCoreHint> hints = new HashMap<>();
            Set<CellKey> visitedRooms = new HashSet<>();
            Set<CellKey> clearedRooms = new HashSet<>();
            Set<CellKey> completedRooms = new HashSet<>();
            int matchIndex = 0;
            for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
                for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                    if (snapshot.isVisitedRoom(roomGridX, roomGridZ)) {
                        visitedRooms.add(new CellKey(roomGridX, roomGridZ));
                    }
                    if (snapshot.isClearedRoom(roomGridX, roomGridZ)) {
                        clearedRooms.add(new CellKey(roomGridX, roomGridZ));
                    }
                    if (snapshot.isCompletedRoom(roomGridX, roomGridZ)) {
                        completedRooms.add(new CellKey(roomGridX, roomGridZ));
                    }
                }
            }

            for (DungeonKnownRoomCatalog.MatchedRoom match : matches) {
                String roomOwner = "match:" + matchIndex++;
                boolean matchVisited = false;
                for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                    CellKey roomCell = new CellKey(component.roomGridX(), component.roomGridZ());
                    matchedRoomCells.add(roomCell);
                    roomTypes.put(roomCell, match.template().type());
                    roomOwners.put(roomCell, roomOwner);
                    matchVisited = matchVisited || visitedRooms.contains(roomCell);
                }

                if (matchVisited) {
                    for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                        visitedRooms.add(new CellKey(component.roomGridX(), component.roomGridZ()));
                    }
                }

                for (DungeonKnownRoomCatalog.MatchedComponent first : match.components()) {
                    for (DungeonKnownRoomCatalog.MatchedComponent second : match.components()) {
                        int distanceX = Math.abs(first.roomGridX() - second.roomGridX());
                        int distanceZ = Math.abs(first.roomGridZ() - second.roomGridZ());
                        if (distanceX + distanceZ == 1) {
                            internalDoors.add(new CellKey(
                                first.roomGridX() + second.roomGridX(),
                                first.roomGridZ() + second.roomGridZ()
                            ));
                        }
                    }
                }
            }

            for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
                for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                    CellKey roomCell = new CellKey(roomGridX, roomGridZ);
                    if (matchedRoomCells.contains(roomCell)) {
                        continue;
                    }

                    DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(roomGridX * 2, roomGridZ * 2);
                    if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
                        continue;
                    }
                    if (DungeonRoomClassifier.isEmptyCore(observedPoint.point().coreHash())) {
                        continue;
                    }

                    roomOwners.put(roomCell, "cell:" + roomGridX + "," + roomGridZ);

                    DungeonKnownRoomCatalog.KnownCoreHint hint =
                        DungeonKnownRoomCatalog.knownCoreHint(observedPoint.point().coreHash());
                    if (hint == null && observedPoint.point().stableCoreHash() != 0) {
                        hint = DungeonKnownRoomCatalog.knownCoreHint(observedPoint.point().stableCoreHash());
                    }
                    if (hint != null) {
                        hints.put(roomCell, hint);
                        roomTypes.put(roomCell, hint.type());
                        roomOwners.put(roomCell, "hint:" + roomGridX + "," + roomGridZ);
                    }

                    if (snapshot.isStartRoom(roomGridX * 2, roomGridZ * 2)
                        && isRealRoom(snapshot, roomGridX * 2, roomGridZ * 2)) {
                        roomTypes.put(roomCell, RoomType.START);
                        roomOwners.put(roomCell, "start");
                    }
                }
            }

            Map<DoorPair, CellKey> detectedDoorByRoomPair = new HashMap<>();
            Set<CellKey> openedDoorCells = new HashSet<>();
            for (int gridZ = 0; gridZ < DungeonScanUtils.SCAN_GRID_SIZE; gridZ++) {
                for (int gridX = 0; gridX < DungeonScanUtils.SCAN_GRID_SIZE; gridX++) {
                    if (!DungeonScanUtils.isDoorScanPoint(gridX, gridZ)) {
                        continue;
                    }

                    DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(gridX, gridZ);
                    if (observedPoint == null || !observedPoint.point().doorKind().visible()) {
                        continue;
                    }

                    CellKey doorCell = new CellKey(gridX, gridZ);
                    if (internalDoors.contains(doorCell)) {
                        continue;
                    }

                    String firstOwner = firstDoorSideOwner(roomOwners, gridX, gridZ);
                    String secondOwner = secondDoorSideOwner(roomOwners, gridX, gridZ);
                    if (firstOwner == null || secondOwner == null || firstOwner.equals(secondOwner)) {
                        externalDoors.put(doorCell, doorInfoFor(
                            observedPoint.point().doorKind(),
                            roomTypes,
                            visitedRooms,
                            gridX,
                            gridZ
                        ));
                        continue;
                    }

                    DoorPair doorPair = DoorPair.of(firstOwner, secondOwner);
                    detectedDoorByRoomPair.putIfAbsent(doorPair, doorCell);
                }
            }

            for (DungeonMapSnapshot.GridKey openedLockedDoor : snapshot.openedLockedDoors()) {
                int gridX = openedLockedDoor.gridX();
                int gridZ = openedLockedDoor.gridZ();
                if (!DungeonScanUtils.isDoorScanPoint(gridX, gridZ)) {
                    continue;
                }
                CellKey doorCell = new CellKey(gridX, gridZ);
                openedDoorCells.add(doorCell);
                markDoorSideRoomsVisited(visitedRooms, gridX, gridZ);
                externalDoors.put(doorCell, doorInfoFor(
                    DungeonDoorKind.OPEN,
                    roomTypes,
                    visitedRooms,
                    gridX,
                    gridZ
                ));
            }

            for (DungeonMapSnapshot.GridKey traversedDoor : snapshot.traversedDoors()) {
                int gridX = traversedDoor.gridX();
                int gridZ = traversedDoor.gridZ();
                CellKey doorCell = new CellKey(gridX, gridZ);
                if (!DungeonScanUtils.isDoorScanPoint(gridX, gridZ) || internalDoors.contains(doorCell)) {
                    continue;
                }

                openedDoorCells.add(doorCell);
                markDoorSideRoomsVisited(visitedRooms, gridX, gridZ);

                String firstOwner = firstDoorSideOwner(roomOwners, gridX, gridZ);
                String secondOwner = secondDoorSideOwner(roomOwners, gridX, gridZ);
                if (firstOwner == null || secondOwner == null || firstOwner.equals(secondOwner)) {
                    externalDoors.put(doorCell, doorInfoFor(
                        DungeonDoorKind.OPEN,
                        roomTypes,
                        visitedRooms,
                        gridX,
                        gridZ
                    ));
                    continue;
                }

                DoorPair doorPair = DoorPair.of(firstOwner, secondOwner);
                detectedDoorByRoomPair.putIfAbsent(doorPair, doorCell);
            }
            for (CellKey doorCell : detectedDoorByRoomPair.values()) {
                DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(doorCell.x(), doorCell.z());
                DungeonDoorKind doorKind = openedDoorCells.contains(doorCell)
                    || observedPoint == null || !observedPoint.point().doorKind().visible()
                    || observedPoint.point().doorKind() == DungeonDoorKind.OPEN
                    ? DungeonDoorKind.OPEN
                    : observedPoint.point().doorKind();
                externalDoors.put(doorCell, doorInfoFor(
                    doorKind,
                    roomTypes,
                    visitedRooms,
                    doorCell.x(),
                    doorCell.z()
                ));
            }

            expandVisitedRoomGroups(visitedRooms, roomOwners);
            expandVisitedRoomGroups(clearedRooms, roomOwners);
            expandVisitedRoomGroups(completedRooms, roomOwners);
            markSpecialDoorEntrances(externalDoors, roomTypes, roomOwners);

            return new MatchRenderPlan(
                matches,
                matchedRoomCells,
                internalDoors,
                externalDoors,
                roomTypes,
                roomOwners,
                hints,
                visitedRooms,
                clearedRooms,
                completedRooms
            );
        }

        private static String firstDoorSideOwner(Map<CellKey, String> roomOwners, int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return roomOwners.get(new CellKey((gridX - 1) / 2, gridZ / 2));
            }
            return roomOwners.get(new CellKey(gridX / 2, (gridZ - 1) / 2));
        }

        private static String secondDoorSideOwner(Map<CellKey, String> roomOwners, int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return roomOwners.get(new CellKey((gridX + 1) / 2, gridZ / 2));
            }
            return roomOwners.get(new CellKey(gridX / 2, (gridZ + 1) / 2));
        }

        private static void markDoorSideRoomsVisited(Set<CellKey> visitedRooms, int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                markVisitedRoomIfValid(visitedRooms, (gridX - 1) / 2, gridZ / 2);
                markVisitedRoomIfValid(visitedRooms, (gridX + 1) / 2, gridZ / 2);
                return;
            }
            markVisitedRoomIfValid(visitedRooms, gridX / 2, (gridZ - 1) / 2);
            markVisitedRoomIfValid(visitedRooms, gridX / 2, (gridZ + 1) / 2);
        }

        private static void markVisitedRoomIfValid(Set<CellKey> visitedRooms, int roomGridX, int roomGridZ) {
            if (roomGridX >= 0
                && roomGridZ >= 0
                && roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
                && roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2) {
                visitedRooms.add(new CellKey(roomGridX, roomGridZ));
            }
        }

        private static void expandVisitedRoomGroups(Set<CellKey> visitedRooms, Map<CellKey, String> roomOwners) {
            Set<String> visitedOwners = new HashSet<>();
            for (CellKey room : visitedRooms) {
                String owner = roomOwners.get(room);
                if (owner != null) {
                    visitedOwners.add(owner);
                }
            }
            for (Map.Entry<CellKey, String> entry : roomOwners.entrySet()) {
                if (visitedOwners.contains(entry.getValue())) {
                    visitedRooms.add(entry.getKey());
                }
            }
        }

        boolean isMatchedRoomCell(int roomGridX, int roomGridZ) {
            return matchedRoomCells.contains(new CellKey(roomGridX, roomGridZ));
        }

        boolean isInternalDoor(int scanGridX, int scanGridZ) {
            return internalDoors.contains(new CellKey(scanGridX, scanGridZ));
        }

        RoomType roomTypeAt(int roomGridX, int roomGridZ) {
            return roomTypes.getOrDefault(new CellKey(roomGridX, roomGridZ), RoomType.UNKNOWN);
        }

        DungeonKnownRoomCatalog.KnownCoreHint hintAt(int roomGridX, int roomGridZ) {
            return hints.get(new CellKey(roomGridX, roomGridZ));
        }

        boolean isVisitedRoom(int roomGridX, int roomGridZ) {
            return visitedRooms.contains(new CellKey(roomGridX, roomGridZ));
        }

        boolean isClearedRoom(int roomGridX, int roomGridZ) {
            return clearedRooms.contains(new CellKey(roomGridX, roomGridZ));
        }

        boolean isCompletedRoom(int roomGridX, int roomGridZ) {
            return completedRooms.contains(new CellKey(roomGridX, roomGridZ));
        }

        boolean hasUnknownRooms(DungeonMapSnapshot snapshot) {
            for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
                for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                    CellKey roomCell = new CellKey(roomGridX, roomGridZ);
                    if (matchedRoomCells.contains(roomCell) || hints.containsKey(roomCell)) {
                        continue;
                    }

                    int scanGridX = roomGridX * 2;
                    int scanGridZ = roomGridZ * 2;
                    DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(scanGridX, scanGridZ);
                    if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
                        continue;
                    }
                    if (DungeonRoomClassifier.isEmptyCore(observedPoint.point().coreHash())) {
                        continue;
                    }
                    if (snapshot.isStartRoom(scanGridX, scanGridZ)) {
                        continue;
                    }
                    return true;
                }
            }
            return false;
        }

        private static DoorRenderInfo doorInfoFor(
            DungeonDoorKind kind,
            Map<CellKey, RoomType> roomTypes,
            Set<CellKey> visitedRooms,
            int gridX,
            int gridZ
        ) {
            CellKey firstSide = firstDoorSide(gridX, gridZ);
            CellKey secondSide = secondDoorSide(gridX, gridZ);
            RoomType firstType = roomTypes.getOrDefault(firstSide, RoomType.UNKNOWN);
            RoomType secondType = roomTypes.getOrDefault(secondSide, RoomType.UNKNOWN);
            CellKey targetSide = preferredDoorTargetSide(firstSide, firstType, secondSide, secondType);
            RoomType targetType = roomTypes.getOrDefault(targetSide, preferredDoorTargetType(firstType, secondType));
            return new DoorRenderInfo(kind, targetType, false, visitedRooms.contains(targetSide));
        }

        private static RoomType firstDoorSideType(Map<CellKey, RoomType> roomTypes, int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return roomTypes.getOrDefault(new CellKey((gridX - 1) / 2, gridZ / 2), RoomType.UNKNOWN);
            }
            return roomTypes.getOrDefault(new CellKey(gridX / 2, (gridZ - 1) / 2), RoomType.UNKNOWN);
        }

        private static RoomType secondDoorSideType(Map<CellKey, RoomType> roomTypes, int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return roomTypes.getOrDefault(new CellKey((gridX + 1) / 2, gridZ / 2), RoomType.UNKNOWN);
            }
            return roomTypes.getOrDefault(new CellKey(gridX / 2, (gridZ + 1) / 2), RoomType.UNKNOWN);
        }

        private static RoomType preferredDoorTargetType(RoomType firstType, RoomType secondType) {
            if (isSpecialDoorTarget(firstType)) {
                return firstType;
            }
            if (isSpecialDoorTarget(secondType)) {
                return secondType;
            }
            if (firstType == RoomType.START || secondType == RoomType.START) {
                return RoomType.START;
            }
            return RoomType.NORMAL;
        }

        private static CellKey preferredDoorTargetSide(
            CellKey firstSide,
            RoomType firstType,
            CellKey secondSide,
            RoomType secondType
        ) {
            if (isSpecialDoorTarget(firstType)) {
                return firstSide;
            }
            if (isSpecialDoorTarget(secondType)) {
                return secondSide;
            }
            if (firstType == RoomType.UNKNOWN && secondType != RoomType.UNKNOWN) {
                return firstSide;
            }
            if (secondType == RoomType.UNKNOWN && firstType != RoomType.UNKNOWN) {
                return secondSide;
            }
            return secondSide;
        }

        private static void markSpecialDoorEntrances(
            Map<CellKey, DoorRenderInfo> externalDoors,
            Map<CellKey, RoomType> roomTypes,
            Map<CellKey, String> roomOwners
        ) {
            CellKey fairyEntranceDoor = fairyEntranceDoor(externalDoors.keySet(), roomTypes, roomOwners);
            for (Map.Entry<CellKey, DoorRenderInfo> entry : externalDoors.entrySet()) {
                DoorRenderInfo door = entry.getValue();
                boolean colorAsSpecial = isSpecialDoorTarget(door.targetType())
                    && (door.targetType() != RoomType.FAIRY || entry.getKey().equals(fairyEntranceDoor));
                entry.setValue(new DoorRenderInfo(door.kind(), door.targetType(), colorAsSpecial, door.targetVisited()));
            }
        }

        private static CellKey fairyEntranceDoor(
            Set<CellKey> doors,
            Map<CellKey, RoomType> roomTypes,
            Map<CellKey, String> roomOwners
        ) {
            List<CellKey> fairyDoors = doors.stream()
                .filter(door -> firstDoorSideType(roomTypes, door.x(), door.z()) == RoomType.FAIRY
                    || secondDoorSideType(roomTypes, door.x(), door.z()) == RoomType.FAIRY)
                .toList();
            if (fairyDoors.isEmpty()) {
                return null;
            }
            if (fairyDoors.size() == 1) {
                return fairyDoors.getFirst();
            }

            Map<CellKey, Integer> distanceFromStart = distanceFromStart(doors, roomTypes, roomOwners);
            CellKey bestDoor = fairyDoors.getFirst();
            int bestDistance = Integer.MAX_VALUE;
            for (CellKey door : fairyDoors) {
                CellKey outsideFairy = outsideFairySide(door, roomTypes);
                int distance = distanceFromStart.getOrDefault(outsideFairy, Integer.MAX_VALUE);
                if (distance < bestDistance) {
                    bestDoor = door;
                    bestDistance = distance;
                }
            }
            return bestDoor;
        }

        private static Map<CellKey, Integer> distanceFromStart(
            Set<CellKey> doors,
            Map<CellKey, RoomType> roomTypes,
            Map<CellKey, String> roomOwners
        ) {
            Map<CellKey, Integer> distances = new HashMap<>();
            ArrayDeque<CellKey> queue = new ArrayDeque<>();
            for (Map.Entry<CellKey, RoomType> entry : roomTypes.entrySet()) {
                if (entry.getValue() == RoomType.START) {
                    distances.put(entry.getKey(), 0);
                    queue.add(entry.getKey());
                }
            }

            while (!queue.isEmpty()) {
                CellKey current = queue.removeFirst();
                int nextDistance = distances.get(current) + 1;
                for (CellKey neighbor : roomNeighbors(current)) {
                    if (!roomTypes.containsKey(neighbor)
                        || roomTypes.get(neighbor) == RoomType.FAIRY
                        || distances.containsKey(neighbor)
                        || !roomsAreConnected(current, neighbor, doors, roomOwners)) {
                        continue;
                    }
                    distances.put(neighbor, nextDistance);
                    queue.add(neighbor);
                }
            }
            return distances;
        }

        private static boolean roomsAreConnected(
            CellKey first,
            CellKey second,
            Set<CellKey> doors,
            Map<CellKey, String> roomOwners
        ) {
            String firstOwner = roomOwners.get(first);
            String secondOwner = roomOwners.get(second);
            if (firstOwner != null && firstOwner.equals(secondOwner)) {
                return true;
            }
            return doors.contains(new CellKey(first.x() + second.x(), first.z() + second.z()));
        }

        private static List<CellKey> roomNeighbors(CellKey room) {
            return List.of(
                new CellKey(room.x() + 1, room.z()),
                new CellKey(room.x() - 1, room.z()),
                new CellKey(room.x(), room.z() + 1),
                new CellKey(room.x(), room.z() - 1)
            );
        }

        private static CellKey outsideFairySide(CellKey door, Map<CellKey, RoomType> roomTypes) {
            CellKey firstSide = firstDoorSide(door.x(), door.z());
            if (roomTypes.get(firstSide) != RoomType.FAIRY) {
                return firstSide;
            }
            return secondDoorSide(door.x(), door.z());
        }

        private static CellKey firstDoorSide(int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return new CellKey((gridX - 1) / 2, gridZ / 2);
            }
            return new CellKey(gridX / 2, (gridZ - 1) / 2);
        }

        private static CellKey secondDoorSide(int gridX, int gridZ) {
            if (isHorizontalDoor(gridX, gridZ)) {
                return new CellKey((gridX + 1) / 2, gridZ / 2);
            }
            return new CellKey(gridX / 2, (gridZ + 1) / 2);
        }
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

    static record CellKey(int x, int z) {
    }

    static record DoorRenderInfo(DungeonDoorKind kind, RoomType targetType, boolean colorAsSpecial, boolean targetVisited) {
    }

    private record DoorPair(String firstOwner, String secondOwner) {
        static DoorPair of(String firstOwner, String secondOwner) {
            if (firstOwner.compareTo(secondOwner) <= 0) {
                return new DoorPair(firstOwner, secondOwner);
            }
            return new DoorPair(secondOwner, firstOwner);
        }
    }
}
