package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class DungeonRoomClassifier {
    public static final int EMPTY_CORE_HASH = -318865360;
    private static final String BUNDLED_ROOM_TYPES_RESOURCE = "/kung-dungeon-scans/known-room-types.properties";

    private static final Map<Integer, RoomType> DEFAULT_ROOM_TYPES = Map.ofEntries(
        Map.entry(400135283, RoomType.BLOOD),
        Map.entry(-706847847, RoomType.BLOOD),
        Map.entry(1547681127, RoomType.START),
        Map.entry(-1957101940, RoomType.START),
        Map.entry(-871624328, RoomType.FAIRY),
        Map.entry(-2129401661, RoomType.PUZZLE),
        Map.entry(1313676499, RoomType.PUZZLE),
        Map.entry(-918031760, RoomType.PUZZLE),
        Map.entry(-1577181988, RoomType.PUZZLE),
        Map.entry(931625578, RoomType.PUZZLE),
        Map.entry(1351916417, RoomType.PUZZLE),
        Map.entry(494549040, RoomType.TRAP),
        Map.entry(-272406096, RoomType.TRAP),
        Map.entry(-1956926881, RoomType.TRAP),
        Map.entry(2028709181, RoomType.YELLOW)
    );

    private static Map<Integer, RoomType> knownRoomTypes;

    private DungeonRoomClassifier() {
    }

    public static RoomType classifyRoom(int coreHash) {
        if (isEmptyCore(coreHash)) {
            return RoomType.UNKNOWN;
        }
        return knownRoomTypes().getOrDefault(coreHash, RoomType.NORMAL);
    }

    public static boolean isEmptyCore(int coreHash) {
        return coreHash == 0 || coreHash == EMPTY_CORE_HASH;
    }

    public static void reload() {
        knownRoomTypes = null;
    }

    public static void learnRoomType(int coreHash, RoomType roomType) throws IOException {
        if (isEmptyCore(coreHash)) {
            throw new IllegalArgumentException("Cannot learn an empty dungeon room core.");
        }

        knownRoomTypes().put(coreHash, roomType);
        Path file = knownRoomTypesFile();
        Files.createDirectories(file.getParent());

        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        properties.setProperty(Integer.toString(coreHash), roomType.name());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(
                writer,
                "Kung dungeon scan room type hints. Format: coreHash=ROOM_TYPE. Types: START, NORMAL, YELLOW, PUZZLE, BLOOD, FAIRY, TRAP, UNKNOWN."
            );
        }
    }

    public static void removeRoomTypes(Collection<Integer> coreHashes) throws IOException {
        if (coreHashes == null || coreHashes.isEmpty()) {
            return;
        }

        for (int coreHash : coreHashes) {
            knownRoomTypes().remove(coreHash);
        }

        Path file = knownRoomTypesFile();
        if (!Files.exists(file)) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        boolean changed = false;
        for (int coreHash : coreHashes) {
            changed = properties.remove(Integer.toString(coreHash)) != null || changed;
        }
        if (!changed) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(
                writer,
                "Kung dungeon scan room type hints. Format: coreHash=ROOM_TYPE. Types: START, NORMAL, YELLOW, PUZZLE, BLOOD, FAIRY, TRAP, UNKNOWN."
            );
        }
    }

    private static Map<Integer, RoomType> knownRoomTypes() {
        if (knownRoomTypes == null) {
            knownRoomTypes = loadKnownRoomTypes();
        }
        return knownRoomTypes;
    }

    private static Map<Integer, RoomType> loadKnownRoomTypes() {
        Map<Integer, RoomType> roomTypes = new HashMap<>(DEFAULT_ROOM_TYPES);
        loadPropertiesInto(roomTypes, bundledProperties());
        Path file = knownRoomTypesFile();

        try {
            Files.createDirectories(file.getParent());
            if (!DungeonMapOverlayConfig.INSTANCE.localRoomDataEnabled()) {
                return roomTypes;
            }
            if (!Files.exists(file)) {
                writeDefaultKnownRoomTypes(file);
                return roomTypes;
            }

            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            loadPropertiesInto(roomTypes, properties);
        } catch (IOException | IllegalArgumentException exception) {
            KungMod.LOGGER.warn("Failed to load known dungeon room types from {}.", file.toAbsolutePath(), exception);
        }

        return roomTypes;
    }

    private static Properties bundledProperties() {
        Properties properties = new Properties();
        try (InputStream stream = DungeonRoomClassifier.class.getResourceAsStream(BUNDLED_ROOM_TYPES_RESOURCE)) {
            if (stream == null) {
                return properties;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException | RuntimeException exception) {
            KungMod.LOGGER.warn("Failed to load bundled dungeon room types.", exception);
        }
        return properties;
    }

    private static void loadPropertiesInto(Map<Integer, RoomType> roomTypes, Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            int coreHash = Integer.parseInt(key.trim());
            RoomType roomType = RoomType.valueOf(properties.getProperty(key).trim().toUpperCase());
            roomTypes.put(coreHash, roomType);
        }
    }

    private static void writeDefaultKnownRoomTypes(Path file) throws IOException {
        Properties properties = new Properties();
        for (Map.Entry<Integer, RoomType> entry : DEFAULT_ROOM_TYPES.entrySet()) {
            properties.setProperty(Integer.toString(entry.getKey()), entry.getValue().name());
        }

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(
                writer,
                "Kung dungeon scan room type hints. Format: coreHash=ROOM_TYPE. Types: START, NORMAL, YELLOW, PUZZLE, BLOOD, FAIRY, TRAP, UNKNOWN."
            );
        }
    }

    private static Path knownRoomTypesFile() {
        return FabricLoader.getInstance()
            .getGameDir()
            .resolve("kung-dungeon-scans")
            .resolve("known-room-types.properties");
    }
}
