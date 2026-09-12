package com.github.beng420.kung.feature.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Original Stella arena metadata, loaded once; no world scanning or network work. */
final class DungeonBossMapCatalog {
    static final int VIEW_SIZE = 128;
    static final String RESOURCE_ROOT = "assets/kung/textures/dungeon/boss/";
    private final Map<Integer, List<Arena>> floors;

    private DungeonBossMapCatalog(Map<Integer, List<Arena>> floors) {
        this.floors = Map.copyOf(floors);
    }

    static DungeonBossMapCatalog loadBundled() throws IOException {
        var stream = DungeonBossMapCatalog.class.getClassLoader().getResourceAsStream(RESOURCE_ROOT + "imagedata.json");
        if (stream == null) throw new IOException("Missing bundled Stella boss map metadata");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    static DungeonBossMapCatalog read(Reader reader) {
        Map<Integer, List<Arena>> floors = new LinkedHashMap<>();
        for (var floor : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
            List<Arena> arenas = new ArrayList<>();
            for (var element : floor.getValue().getAsJsonArray()) {
                JsonObject data = element.getAsJsonObject();
                JsonArray bounds = data.getAsJsonArray("bounds");
                JsonArray min = bounds.get(0).getAsJsonArray();
                JsonArray max = bounds.get(1).getAsJsonArray();
                JsonArray origin = data.getAsJsonArray("topLeftLocation");
                arenas.add(new Arena(data.get("image").getAsString(),
                    new Bounds(min.get(0).getAsDouble(), min.get(1).getAsDouble(), min.get(2).getAsDouble(),
                        max.get(0).getAsDouble(), max.get(1).getAsDouble(), max.get(2).getAsDouble()),
                    data.get("width").getAsInt(), data.get("height").getAsInt(),
                    data.get("widthInWorld").getAsInt(), data.get("heightInWorld").getAsInt(),
                    origin.get(0).getAsInt(), origin.get(1).getAsInt(),
                    data.has("renderSize") ? data.get("renderSize").getAsInt() : 0));
            }
            // Preserve upstream priority, notably the F7 reward room before phase maps.
            floors.put(Integer.parseInt(floor.getKey()), List.copyOf(arenas));
        }
        return new DungeonBossMapCatalog(floors);
    }

    List<Arena> arenas(int floor) {
        return floors.getOrDefault(floor, List.of());
    }

    Arena find(int floor, double x, double y, double z) {
        for (Arena arena : arenas(floor)) {
            if (arena.bounds().contains(x, y, z)) return arena;
        }
        return null;
    }

    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    record Arena(String image, Bounds bounds, int width, int height, int worldWidth, int worldHeight,
                 int originX, int originZ, int renderSize) {
        Arena {
            if (!image.matches("f[1-7]_boss(?:_s[1-5]|_end)?")
                || width <= 0 || height <= 0 || worldWidth <= 0 || worldHeight <= 0 || renderSize < 0) {
                throw new IllegalArgumentException("Invalid boss map metadata: " + image);
            }
        }

        View view(double playerX, double playerZ) {
            // Match Stella's original projection, including its non-square map calibration.
            double worldSize = Math.min(worldWidth, worldHeight);
            if (renderSize > 0) worldSize = Math.min(worldSize, renderSize);
            double textureScale = VIEW_SIZE / Math.min(
                width / (double) worldWidth * (renderSize > 0 ? renderSize : worldWidth),
                height / (double) worldHeight * (renderSize > 0 ? renderSize : worldHeight));
            double renderedWidth = width * textureScale;
            double renderedHeight = height * textureScale;
            double viewX = Math.clamp((playerX - originX) / worldSize * VIEW_SIZE - VIEW_SIZE / 2.0,
                0.0, Math.max(0.0, renderedWidth - VIEW_SIZE));
            double viewZ = Math.clamp((playerZ - originZ) / worldSize * VIEW_SIZE - VIEW_SIZE / 2.0,
                0.0, Math.max(0.0, renderedHeight - VIEW_SIZE));
            return new View(this, worldSize, (int) renderedWidth, (int) renderedHeight, viewX, viewZ);
        }
    }

    record View(Arena arena, double worldSize, int textureWidth, int textureHeight, double viewX, double viewZ) {
        float playerX(double worldX) { return (float) ((worldX - arena.originX()) / worldSize * VIEW_SIZE - viewX); }
        float playerY(double worldZ) { return (float) ((worldZ - arena.originZ()) / worldSize * VIEW_SIZE - viewZ); }
    }
}
