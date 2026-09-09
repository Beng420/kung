package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.KungConfig;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.github.beng420.kung.runtime.KungPaths;

public final class DungeonKnownRoomCatalog {
    private static final int MIN_SOFT_MATCHED_COMPONENTS = 2;
    private static final int MIN_DYNAMIC_PRELOAD_OBSERVATIONS = 2;
    private static final int MAX_TEMPLATE_COMPONENTS = 4;
    private static final int MAX_TEMPLATE_SPAN = 4;
    private static final String BUNDLED_KNOWN_ROOMS_JSON_RESOURCE = "/kung-dungeon-scans/known-rooms.json";
    private static final String BUNDLED_KNOWN_ROOMS_RESOURCE = "/kung-dungeon-scans/known-rooms.jsonl";
    private static final String BUNDLED_PRELOADS_RESOURCE = "/kung-dungeon-scans/known-room-preloads.jsonl";
    private static final Object TEMPLATE_CACHE_LOCK = new Object();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long LOCAL_LEARN_UPDATED_AT_THRESHOLD = 1_000_000_000_000L;
    private static final long UNDO_BURST_WINDOW_MILLIS = 10_000L;
    private static final KnownCoreHint BLAZE_PRELOAD_HINT = new KnownCoreHint("Blaze", RoomType.PUZZLE, 1);
    private static final Map<Integer, KnownCoreHint> STATIC_PRELOAD_CORE_HINTS = Map.of(
        -1783845896, BLAZE_PRELOAD_HINT,
        -1441109704, BLAZE_PRELOAD_HINT,
        -273835113, BLAZE_PRELOAD_HINT,
        -1563484598, BLAZE_PRELOAD_HINT,
        415729287, BLAZE_PRELOAD_HINT,
        -1338494642, BLAZE_PRELOAD_HINT
    );
    private static final Map<String, CanonicalRoomMetadata> CANONICAL_METADATA_OVERRIDES = Map.ofEntries(
        Map.entry(canonicalNameKey("Mini Waterfall"), new CanonicalRoomMetadata("Small Waterfall", RoomType.NORMAL, 2, 5, false)),
        Map.entry(canonicalNameKey("Withermancers"), new CanonicalRoomMetadata("Withermancer", RoomType.NORMAL, 4, 6, true)),
        Map.entry(canonicalNameKey("Dino Dig Site"), new CanonicalRoomMetadata("Dino Site", RoomType.NORMAL, 4, 4, false)),
        Map.entry(canonicalNameKey("Super Tall"), new CanonicalRoomMetadata("Supertall", RoomType.NORMAL, 6, 6, true)),
        Map.entry(canonicalNameKey("Haning Vines"), new CanonicalRoomMetadata("Hanging Vines", RoomType.NORMAL, 1, 0, false)),
        Map.entry(canonicalNameKey("Rail Track"), new CanonicalRoomMetadata("Rails", RoomType.NORMAL, 9, 1, false)),
        Map.entry(canonicalNameKey("Lots of Floors"), new CanonicalRoomMetadata("Lots Of Floors", RoomType.NORMAL, 3, 1, false)),
        Map.entry(canonicalNameKey("Midas"), new CanonicalRoomMetadata("King Midas", RoomType.YELLOW, 0, 1, false)),
        Map.entry(canonicalNameKey("Sewer"), new CanonicalRoomMetadata("Pipes", RoomType.NORMAL, 7, 9, false)),
        Map.entry(canonicalNameKey("Redstone Skull"), new CanonicalRoomMetadata("Redstone Crypt", RoomType.NORMAL, 3, 0, false)),
        Map.entry(canonicalNameKey("Double Stair"), new CanonicalRoomMetadata("Staircase", RoomType.NORMAL, 3, 2, false)),
        Map.entry(canonicalNameKey("Silver Swords"), new CanonicalRoomMetadata("Silvers Sword", RoomType.NORMAL, 1, 0, false)),
        Map.entry(canonicalNameKey("Lava Skull"), new CanonicalRoomMetadata("Lava Pit", RoomType.NORMAL, 3, 1, false)),
        Map.entry(canonicalNameKey("Lava Tomb"), new CanonicalRoomMetadata("Lava Pit", RoomType.NORMAL, 3, 1, false)),
        Map.entry(canonicalNameKey("Draw Bridge"), new CanonicalRoomMetadata("Bridges", RoomType.NORMAL, 6, 6, true)),
        Map.entry(canonicalNameKey("Four Banner"), new CanonicalRoomMetadata("Banners", RoomType.NORMAL, 1, 1, false)),
        Map.entry(canonicalNameKey("Black Flag"), new CanonicalRoomMetadata("Black Flag", RoomType.NORMAL, 3, 1, false)),
        Map.entry(canonicalNameKey("Ritual"), new CanonicalRoomMetadata("Ritual", RoomType.NORMAL, 3, 1, false)),
        Map.entry(canonicalNameKey("Three Weirdos"), new CanonicalRoomMetadata("Three Weirdos", RoomType.PUZZLE, 0, 0, false)),
        Map.entry(canonicalNameKey("Water Board"), new CanonicalRoomMetadata("Water Board", RoomType.PUZZLE, 0, 0, false)),
        Map.entry(canonicalNameKey("Ice Fill"), new CanonicalRoomMetadata("Ice Fill", RoomType.PUZZLE, 0, 0, false)),
        Map.entry(canonicalNameKey("Teleport Maze"), new CanonicalRoomMetadata("Teleport Maze", RoomType.PUZZLE, 0, 0, false)),
        Map.entry(canonicalNameKey("Boulder"), new CanonicalRoomMetadata("Boulder", RoomType.PUZZLE, 0, 0, false)),
        Map.entry(canonicalNameKey("Ice Path"), new CanonicalRoomMetadata("Ice Path", RoomType.PUZZLE, 0, 0, false))
    );
    private static final Set<String> LEGACY_PRINCE_ROOM_NAMES = canonicalNameSet(
        "Big Red Flag",
        "Bridges",
        "Draw Bridge",
        "Chambers",
        "Doors",
        "Flags",
        "Grass Ruin",
        "Leaves",
        "Market",
        "Pirate",
        "Quartz Knight",
        "Red Blue",
        "Red-Blue",
        "Skull",
        "Sloth",
        "Super Tall",
        "Supertall",
        "Waterfall",
        "Withermancer",
        "Withermancers"
    );
    private static final Set<String> LEGACY_NON_PRINCE_ROOM_NAMES = canonicalNameSet(
        "Admin",
        "Altar",
        "Andesite",
        "Arches",
        "Archway",
        "Arrow Trap",
        "Atlas",
        "Balcony",
        "Banners",
        "Basement",
        "Beams",
        "Black Flag",
        "Blaze",
        "Blood",
        "Blue Skulls",
        "Boulder",
        "Buttons",
        "Cage",
        "Cages",
        "Carpets",
        "Cathedral",
        "Catwalk",
        "Cell",
        "Chains",
        "Cobble Wall Pillar",
        "Corridor",
        "Creeper Beams",
        "Criss Cross",
        "Crypt",
        "Crypts",
        "Deathmite",
        "Diagonal",
        "Dino Site",
        "Dip",
        "Dome",
        "Double Diamond",
        "Dragon Skull",
        "Drop",
        "Dueces",
        "Duncan",
        "End",
        "Entrance",
        "Fairy",
        "Gold",
        "Golden Oasis",
        "Grand Library",
        "Granite",
        "Gravel",
        "Hall",
        "Hallway",
        "Hanging Vines",
        "Ice Fill",
        "Ice Path",
        "Jumping Skulls",
        "King Midas",
        "Knight",
        "Lava Pit",
        "Lava Ravine",
        "Layers",
        "Locked Away",
        "Logs",
        "Long Hall",
        "Lots Of Floors",
        "Mage",
        "Melon",
        "Mines",
        "Mini Rail Track",
        "Miniboss",
        "Mirror",
        "Mossy",
        "Multicolored",
        "Mural",
        "Museum",
        "Mushroom",
        "New Trap",
        "Old Trap",
        "Overgrown",
        "Overgrown Chains",
        "Painting",
        "Pedestal",
        "Perch",
        "Pillars",
        "Pipes",
        "Pit",
        "Pressure Plates",
        "Prison Cell",
        "Purple Flags",
        "Quad Lava",
        "Quiz",
        "Raccoon",
        "Rails",
        "Red Green",
        "Redstone Crypt",
        "Redstone Key",
        "Redstone Warrior",
        "Ritual",
        "Sand Dragon",
        "Sarcophagus",
        "Scaffolding",
        "Shadow Assassin",
        "Silvers Sword",
        "Slabs",
        "Slime",
        "Small Stairs",
        "Small Waterfall",
        "Spider",
        "Spikes",
        "Staircase",
        "Stairs",
        "Steps",
        "Stone Window",
        "Teleport Maze",
        "Temple",
        "Three Weirdos",
        "Tic Tac Toe",
        "Tombstone",
        "Tomioka",
        "Trinity",
        "Vinny 8 Ball",
        "Water",
        "Water Board",
        "Well",
        "Wizard",
        "Zodd"
    );

    private static TemplateCache cachedTemplateCache;
    private static long revision;

    private DungeonKnownRoomCatalog() {
    }

    public static void append(LearnedRoom room) throws IOException {
        upsertAll(List.of(room));
        invalidateTemplateCache();
    }

    public static void appendAll(List<LearnedRoom> rooms) throws IOException {
        if (rooms.isEmpty()) {
            return;
        }
        upsertAll(rooms);
        invalidateTemplateCache();
    }

    public static UndoResult undoLast() throws IOException {
        UndoResult jsonResult = undoLastJsonLearn();
        if (jsonResult.undone()) {
            invalidateTemplateCache();
            return jsonResult;
        }

        Path file = legacyKnownRoomsFile();
        if (!Files.exists(file)) {
            return jsonResult;
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        int lastEntryIndex = lastNonBlankLineIndex(lines);
        if (lastEntryIndex < 0) {
            return UndoResult.nothingToUndo("known-rooms.jsonl is empty.");
        }

        String undoneLine = lines.remove(lastEntryIndex);
        Files.write(file, lines, StandardCharsets.UTF_8);
        appendUndoneLine(undoneLine);
        invalidateTemplateCache();

        return UndoResult.undone("Removed the last learned room entry: " + shortLine(undoneLine));
    }

    private static UndoResult undoLastJsonLearn() throws IOException {
        Path file = knownRoomsFile();
        if (!Files.exists(file)) {
            return UndoResult.nothingToUndo("known-rooms.json does not exist yet.");
        }

        JsonObject root = JsonParser.parseString(stripBom(Files.readString(file, StandardCharsets.UTF_8).trim()))
            .getAsJsonObject();
        JsonArray rooms = root.getAsJsonArray("rooms");
        if (rooms == null || rooms.isEmpty()) {
            return UndoResult.nothingToUndo("known-rooms.json is empty.");
        }

        LatestJsonLearn latest = latestJsonLearn(rooms);
        if (latest == null) {
            return UndoResult.nothingToUndo("No local learned JSON room entry was found to undo.");
        }

        int removedHashes = removeJsonLearnBurst(rooms, latest);
        Files.writeString(file, PRETTY_GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        return UndoResult.undone(
            "Removed the last learned JSON room entry: "
                + latest.name()
                + " "
                + latest.type()
                + " secrets="
                + latest.secrets()
                + " hashes="
                + removedHashes
        );
    }

    private static LatestJsonLearn latestJsonLearn(JsonArray rooms) {
        LatestJsonLearn latest = null;
        for (JsonElement roomElement : rooms) {
            JsonObject room = roomElement.getAsJsonObject();
            JsonArray variants = room.getAsJsonArray("variants");
            if (variants == null) {
                continue;
            }
            for (JsonElement variantElement : variants) {
                JsonObject variant = variantElement.getAsJsonObject();
                JsonArray components = variant.getAsJsonArray("components");
                if (components == null) {
                    continue;
                }
                for (JsonElement componentElement : components) {
                    JsonObject component = componentElement.getAsJsonObject();
                    JsonArray hashes = component.getAsJsonArray("hashes");
                    if (hashes == null) {
                        continue;
                    }
                    for (JsonElement hashElement : hashes) {
                        JsonObject hash = hashElement.getAsJsonObject();
                        long updatedAt = hash.has("updatedAt") ? hash.get("updatedAt").getAsLong() : 0;
                        if (updatedAt < LOCAL_LEARN_UPDATED_AT_THRESHOLD
                            || (latest != null && updatedAt <= latest.updatedAt())) {
                            continue;
                        }
                        latest = new LatestJsonLearn(
                            room.get("name").getAsString(),
                            room.get("type").getAsString(),
                            room.get("secrets").getAsInt(),
                            updatedAt
                        );
                    }
                }
            }
        }
        return latest;
    }

    private static int removeJsonLearnBurst(JsonArray rooms, LatestJsonLearn latest) {
        int removedHashes = 0;
        long earliestUpdatedAt = Math.max(
            LOCAL_LEARN_UPDATED_AT_THRESHOLD,
            latest.updatedAt() - UNDO_BURST_WINDOW_MILLIS
        );
        for (int roomIndex = rooms.size() - 1; roomIndex >= 0; roomIndex--) {
            JsonObject room = rooms.get(roomIndex).getAsJsonObject();
            if (!latest.matches(room)) {
                continue;
            }
            JsonArray variants = room.getAsJsonArray("variants");
            if (variants == null) {
                continue;
            }
            for (int variantIndex = variants.size() - 1; variantIndex >= 0; variantIndex--) {
                JsonObject variant = variants.get(variantIndex).getAsJsonObject();
                JsonArray components = variant.getAsJsonArray("components");
                if (components == null) {
                    continue;
                }
                for (int componentIndex = components.size() - 1; componentIndex >= 0; componentIndex--) {
                    JsonObject component = components.get(componentIndex).getAsJsonObject();
                    JsonArray hashes = component.getAsJsonArray("hashes");
                    if (hashes == null) {
                        continue;
                    }
                    for (int hashIndex = hashes.size() - 1; hashIndex >= 0; hashIndex--) {
                        JsonObject hash = hashes.get(hashIndex).getAsJsonObject();
                        long updatedAt = hash.has("updatedAt") ? hash.get("updatedAt").getAsLong() : 0;
                        if (updatedAt >= earliestUpdatedAt && updatedAt <= latest.updatedAt()) {
                            hashes.remove(hashIndex);
                            removedHashes++;
                        }
                    }
                    if (hashes.isEmpty()) {
                        components.remove(componentIndex);
                    }
                }
                if (components.isEmpty()) {
                    variants.remove(variantIndex);
                }
            }
            if (variants.isEmpty()) {
                rooms.remove(roomIndex);
            }
        }
        return removedHashes;
    }

    public static List<MatchedRoom> matchKnownRooms(DungeonMapSnapshot snapshot) {
        TemplateCache templateCache = templateCache();
        return matchKnownRooms(snapshot, templateCache.templates(), templateCache.knownCoreHints());
    }

    static List<MatchedRoom> matchKnownRooms(
        DungeonMapSnapshot snapshot,
        List<RoomTemplate> templates,
        Map<Integer, KnownCoreHint> knownCoreHints
    ) {
        List<RoomTemplateVariant> variants = new ArrayList<>();
        for (RoomTemplate template : templates) {
            variants.addAll(template.variants());
        }

        variants.sort(Comparator
            .comparingInt(RoomTemplateVariant::componentCount)
            .reversed()
            .thenComparing(variant -> variant.template().name()));

        Set<CellKey> occupiedCells = new HashSet<>();
        List<MatchedRoom> matches = new ArrayList<>();
        for (RoomTemplateVariant variant : variants) {
            for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
                for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                    MatchedRoom match = tryMatch(snapshot, variant, roomGridX, roomGridZ, occupiedCells);
                    if (match == null) {
                        continue;
                    }

                    matches.add(match);
                    for (MatchedComponent component : match.components()) {
                        occupiedCells.add(new CellKey(component.roomGridX(), component.roomGridZ()));
                    }
                }
            }
        }

        addCoreHintMatches(snapshot, knownCoreHints, occupiedCells, matches);
        addSoftMatches(snapshot, variants, knownCoreHints, occupiedCells, matches);
        expandAdjacentKnownRoomHints(snapshot, knownCoreHints, variants, occupiedCells, matches);

        return matches;
    }

    public static List<RoomTemplate> loadTemplates() {
        return templateCache().templates();
    }

    public static List<String> knownRoomNames() {
        TemplateCache templateCache = templateCache();
        Set<String> names = new HashSet<>();
        for (RoomTemplate template : templateCache.templates()) {
            names.add(template.name());
        }
        for (KnownCoreHint hint : templateCache.knownCoreHints().values()) {
            names.add(hint.name());
        }
        List<String> sortedNames = new ArrayList<>(names);
        sortedNames.sort(String::compareToIgnoreCase);
        return sortedNames;
    }

    public static List<KnownRoomInfo> knownRoomInfos(String name) {
        TemplateCache templateCache = templateCache();
        Map<String, KnownRoomInfo> infos = new LinkedHashMap<>();
        for (RoomTemplate template : templateCache.templates()) {
            if (!template.name().equalsIgnoreCase(name)) {
                continue;
            }
            addKnownRoomInfo(infos, new KnownRoomInfo(
                template.name(),
                template.type(),
                template.secrets(),
                template.crypts(),
                template.prince()
            ));
        }
        for (KnownCoreHint hint : templateCache.knownCoreHints().values()) {
            if (!hint.name().equalsIgnoreCase(name)) {
                continue;
            }
            addKnownRoomInfo(infos, new KnownRoomInfo(
                hint.name(),
                hint.type(),
                hint.secrets(),
                hint.crypts(),
                hint.prince()
            ));
        }
        return List.copyOf(infos.values());
    }

    public static List<KnownRoomInfo> knownRoomInfos(String name, int coreHash, int stableCoreHash) {
        TemplateCache templateCache = templateCache();
        Map<String, KnownRoomInfo> infos = new LinkedHashMap<>();
        for (RoomTemplate template : templateCache.templates()) {
            if (!template.name().equalsIgnoreCase(name) || !templateMatchesHash(template, coreHash, stableCoreHash)) {
                continue;
            }
            addKnownRoomInfo(infos, new KnownRoomInfo(
                template.name(),
                template.type(),
                template.secrets(),
                template.crypts(),
                template.prince()
            ));
        }
        for (Map.Entry<Integer, KnownCoreHint> entry : templateCache.knownCoreHints().entrySet()) {
            KnownCoreHint hint = entry.getValue();
            if (!hint.name().equalsIgnoreCase(name)
                || (entry.getKey() != coreHash && (stableCoreHash == 0 || entry.getKey() != stableCoreHash))) {
                continue;
            }
            addKnownRoomInfo(infos, new KnownRoomInfo(
                hint.name(),
                hint.type(),
                hint.secrets(),
                hint.crypts(),
                hint.prince()
            ));
        }
        return List.copyOf(infos.values());
    }

    private static boolean templateMatchesHash(RoomTemplate template, int coreHash, int stableCoreHash) {
        for (TemplateComponent component : template.components()) {
            if (component.matches(coreHash, stableCoreHash)) {
                return true;
            }
        }
        return false;
    }

    private static void addKnownRoomInfo(Map<String, KnownRoomInfo> infos, KnownRoomInfo info) {
        infos.putIfAbsent(
            info.name() + "|" + info.type().name() + "|" + info.secrets(),
            info
        );
    }

    public static boolean isKnownCoreHash(int coreHash) {
        return templateCache().knownCoreHints().containsKey(coreHash);
    }

    public static boolean isStableKnownCoreHash(int coreHash) {
        TemplateCache templateCache = templateCache();
        return templateCache.knownCoreHints().containsKey(coreHash)
            && !templateCache.preloadCoreHints().containsKey(coreHash);
    }

    public static KnownCoreHint knownCoreHint(int coreHash) {
        return templateCache().knownCoreHints().get(coreHash);
    }

    public static long revision() {
        synchronized (TEMPLATE_CACHE_LOCK) {
            return revision;
        }
    }

    public static void reload() {
        invalidateTemplateCache();
    }

    public static AutoLearnResult autoLearnStableHashes(
        MatchedRoom match,
        DungeonMapSnapshot snapshot
    ) throws IOException {
        if (!KungConfig.get().dungeon.localRoomDataEnabled()) {
            return AutoLearnResult.none();
        }
        if (match == null || snapshot == null || match.components().isEmpty()) {
            return AutoLearnResult.none();
        }

        List<LearnedRoom> burst = new ArrayList<>();
        boolean hasNewStableHash = false;
        long order = System.currentTimeMillis();
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            DungeonMapSnapshot.ObservedPoint observedPoint =
                snapshot.pointAt(component.roomGridX() * 2, component.roomGridZ() * 2);
            if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
                return AutoLearnResult.none();
            }

            int coreHash = observedPoint.point().coreHash();
            int stableCoreHash = observedPoint.point().stableCoreHash();
            if (DungeonRoomClassifier.isEmptyCore(coreHash) || stableCoreHash == 0) {
                return AutoLearnResult.none();
            }
            if (!isTrustedHint(coreHash, match.template())) {
                return AutoLearnResult.none();
            }

            hasNewStableHash = hasNewStableHash || !hasKnownStableHash(coreHash, stableCoreHash);
            burst.add(new LearnedRoom(
                order++,
                match.template().name(),
                match.template().type(),
                match.template().secrets(),
                match.template().crypts(),
                coreHash,
                stableCoreHash,
                component.roomGridX(),
                component.roomGridZ()
            ));
        }

        if (!hasNewStableHash) {
            return AutoLearnResult.none();
        }

        appendAll(burst);
        return new AutoLearnResult(true, burst.size(), match.template().name());
    }

    public static void recordObservedCoreTransition(int previousCoreHash, int knownCoreHash, int knownStableCoreHash) {
        if (!KungConfig.get().dungeon.localRoomDataEnabled()) {
            return;
        }
        int trustedKnownHash = knownCoreHash;
        if (!isStableKnownCoreHash(trustedKnownHash)
            && knownStableCoreHash != 0
            && isStableKnownCoreHash(knownStableCoreHash)) {
            trustedKnownHash = knownStableCoreHash;
        }
        if (DungeonRoomClassifier.isEmptyCore(previousCoreHash)
            || previousCoreHash == trustedKnownHash
            || isKnownCoreHash(previousCoreHash)
            || !isStableKnownCoreHash(trustedKnownHash)) {
            return;
        }

        KnownCoreHint hint = knownCoreHint(trustedKnownHash);
        if (hint == null) {
            return;
        }

        try {
            appendPreloadHint(previousCoreHash, hint);
            invalidateTemplateCache();
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to record dungeon room preload hint.", exception);
        }
    }

    public static void updateRoomCrypts(String name, RoomType type, int secrets, int crypts) throws IOException {
        Path file = knownRoomsFile();
        RoomDatabase database = loadRoomDatabase(file);
        if (!database.updateCrypts(new TemplateKey(name, type, secrets), crypts)) {
            throw new IllegalArgumentException("Unknown room: " + name);
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, database.toJson(), StandardCharsets.UTF_8);
        invalidateTemplateCache();
    }

    public static DeleteRoomResult deleteRoom(String name) throws IOException {
        return deleteRoom(name, RoomType.UNKNOWN, 0, false);
    }

    public static DeleteRoomResult deleteRoom(String name, RoomType type, int secrets) throws IOException {
        return deleteRoom(name, type, secrets, true);
    }

    private static DeleteRoomResult deleteRoom(String name, RoomType type, int secrets, boolean explicitMetadata)
        throws IOException {
        Path file = knownRoomsFile();
        RoomDatabase database = loadRoomDatabase(file);
        DeleteRoomResult result = database.deleteRoom(name, type, secrets, explicitMetadata);
        if (!result.deleted()) {
            return result;
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, database.toJson(), StandardCharsets.UTF_8);
        DungeonRoomClassifier.removeRoomTypes(result.coreHashes());
        invalidateTemplateCache();
        return result;
    }

    private static TemplateCache templateCache() {
        Path file = knownRoomsFile();
        long modifiedMillis = -1;
        long size = -1;
        if (Files.exists(file)) {
            try {
                modifiedMillis = Files.getLastModifiedTime(file).toMillis();
                size = Files.size(file);
            } catch (IOException exception) {
                modifiedMillis = -2;
                size = -2;
            }
        }

        synchronized (TEMPLATE_CACHE_LOCK) {
            if (cachedTemplateCache != null
                && cachedTemplateCache.file().equals(file)
                && cachedTemplateCache.modifiedMillis() == modifiedMillis
                && cachedTemplateCache.size() == size) {
                return cachedTemplateCache;
            }

            List<RoomTemplate> templates = loadTemplatesUncached(file);
            Map<Integer, KnownCoreHint> preloadCoreHints = preloadCoreHints();
            Map<Integer, KnownCoreHint> knownCoreHints = knownHintsByCoreHash(templates);
            knownCoreHints.putAll(preloadCoreHints);
            cachedTemplateCache = new TemplateCache(
                file,
                modifiedMillis,
                size,
                templates,
                knownCoreHints,
                preloadCoreHints
            );
            return cachedTemplateCache;
        }
    }

    private static void invalidateTemplateCache() {
        synchronized (TEMPLATE_CACHE_LOCK) {
            cachedTemplateCache = null;
            revision++;
        }
    }

    private static List<RoomTemplate> loadTemplatesUncached(Path file) {
        RoomDatabase database = loadRoomDatabase(file);
        return database.templates();
    }

    private static RoomDatabase loadRoomDatabase(Path file) {
        RoomDatabase database = new RoomDatabase();
        List<LearnedRoom> learnedRooms = new ArrayList<>();
        long order = 0;
        String bundledJson = bundledText(BUNDLED_KNOWN_ROOMS_JSON_RESOURCE);
        if (bundledJson.isBlank()) {
            order = readLearnedRooms(bundledLines(BUNDLED_KNOWN_ROOMS_RESOURCE), learnedRooms, order);
        } else {
            readRoomDatabase(bundledJson, database);
        }

        if (KungConfig.get().dungeon.localRoomDataEnabled()) {
            try {
                Path legacyFile = legacyKnownRoomsFile();
                if (Files.exists(legacyFile)) {
                    order = readLearnedRooms(Files.readAllLines(legacyFile, StandardCharsets.UTF_8), learnedRooms, order);
                }
            } catch (IOException | RuntimeException exception) {
                KungMod.LOGGER.warn("Failed to load legacy dungeon room data.", exception);
            }

            learnedRooms = deduplicateByLatestCoreHash(learnedRooms);
            for (RoomTemplate template : templatesFromLearnBursts(learnedRooms)) {
                database.merge(template);
            }
        }

        Path remoteFile = remoteKnownRoomsFile();
        if (KungConfig.get().dungeon.roomSyncEnabled() && Files.exists(remoteFile)) {
            try {
                readRoomDatabase(Files.readString(remoteFile, StandardCharsets.UTF_8), database);
            } catch (IOException | RuntimeException exception) {
                KungMod.LOGGER.warn("Failed to load remote dungeon room JSON data.", exception);
            }
        }

        if (KungConfig.get().dungeon.localRoomDataEnabled() && Files.exists(file)) {
            try {
                readRoomDatabase(Files.readString(file, StandardCharsets.UTF_8), database);
            } catch (IOException | RuntimeException exception) {
                KungMod.LOGGER.warn("Failed to load dungeon room JSON data.", exception);
            }
        }

        return database;
    }

    private static long readLearnedRooms(List<String> lines, List<LearnedRoom> learnedRooms, long order) {
        try {
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                LearnedRoom learnedRoom = parseLearnedRoom(line, order++);
                if (learnedRoom != null) {
                    learnedRooms.add(learnedRoom);
                }
            }
        } catch (RuntimeException exception) {
            KungMod.LOGGER.warn("Failed to load bundled dungeon room data.", exception);
        }
        return order;
    }

    private static void upsertAll(List<LearnedRoom> rooms) throws IOException {
        Path file = knownRoomsFile();
        RoomDatabase database = loadRoomDatabase(file);
        Map<TemplateKey, List<LearnedRoom>> roomsByKey = new LinkedHashMap<>();
        for (LearnedRoom room : rooms) {
            LearnedRoom normalizedRoom = canonicalLearnedRoom(room);
            roomsByKey.computeIfAbsent(keyFor(normalizedRoom), ignored -> new ArrayList<>()).add(normalizedRoom);
        }
        for (Map.Entry<TemplateKey, List<LearnedRoom>> entry : roomsByKey.entrySet()) {
            for (RoomTemplate template : templatesFor(entry.getKey(), entry.getValue())) {
                database.merge(template);
            }
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, database.toJson(), StandardCharsets.UTF_8);
    }

    public static RemoteCacheResult updateRemoteCache(String json) throws IOException {
        RoomDatabase database = new RoomDatabase();
        readRoomDatabase(json, database);
        List<RoomTemplate> templates = database.templates();

        Path file = remoteKnownRoomsFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, database.toJson(), StandardCharsets.UTF_8);
        invalidateTemplateCache();

        int variantCount = 0;
        int componentCount = 0;
        for (RoomTemplate template : templates) {
            variantCount++;
            componentCount += template.components().size();
        }
        return new RemoteCacheResult(true, templates.size(), variantCount, componentCount);
    }

    private static void readRoomDatabase(String json, RoomDatabase database) {
        if (json == null || json.isBlank()) {
            return;
        }

        JsonObject root = JsonParser.parseString(stripBom(json.trim())).getAsJsonObject();
        JsonArray deletedRooms = root.getAsJsonArray("deletedRooms");
        if (deletedRooms != null) {
            for (JsonElement deletedElement : deletedRooms) {
                JsonObject deletedObject = deletedElement.getAsJsonObject();
                String name = deletedObject.get("name").getAsString();
                boolean explicitMetadata = deletedObject.has("type") && deletedObject.has("secrets");
                RoomType type = explicitMetadata
                    ? RoomType.valueOf(deletedObject.get("type").getAsString())
                    : RoomType.UNKNOWN;
                int secrets = explicitMetadata ? deletedObject.get("secrets").getAsInt() : 0;
                database.deleteRoom(name, type, secrets, explicitMetadata);
            }
        }

        JsonArray rooms = root.getAsJsonArray("rooms");
        if (rooms == null) {
            return;
        }

        for (JsonElement roomElement : rooms) {
            JsonObject roomObject = roomElement.getAsJsonObject();
            TemplateKey rawKey = new TemplateKey(
                roomObject.get("name").getAsString(),
                RoomType.valueOf(roomObject.get("type").getAsString()),
                roomObject.get("secrets").getAsInt()
            );
            int crypts = roomObject.has("crypts") ? roomObject.get("crypts").getAsInt() : 0;
            boolean prince = roomObject.has("prince")
                ? roomObject.get("prince").getAsBoolean()
                : legacyHasPrince(rawKey.name());
            CanonicalRoomMetadata metadata = canonicalMetadata(rawKey.name(), rawKey.type(), rawKey.secrets(), crypts, prince);
            TemplateKey key = new TemplateKey(metadata.name(), metadata.type(), metadata.secrets());
            crypts = metadata.crypts();
            prince = metadata.prince();
            database.ensureRoom(key, crypts, prince);
            JsonArray variants = roomObject.getAsJsonArray("variants");
            if (variants == null) {
                continue;
            }

            int variantNumber = 0;
            for (JsonElement variantElement : variants) {
                JsonObject variantObject = variantElement.getAsJsonObject();
                JsonArray components = variantObject.getAsJsonArray("components");
                if (components == null) {
                    continue;
                }

                List<TemplateComponent> templateComponents = new ArrayList<>();
                for (JsonElement componentElement : components) {
                    TemplateComponent component = parseTemplateComponent(componentElement.getAsJsonObject());
                    if (component != null) {
                        templateComponents.add(component);
                    }
                }
                if (!templateComponents.isEmpty() && isValidTemplateShape(templateComponents)) {
                    database.merge(new RoomTemplate(
                        key.name(),
                        key.type(),
                        key.secrets(),
                        crypts,
                        prince,
                        variantNumber++,
                        List.copyOf(templateComponents)
                    ));
                }
            }
        }
    }

    private static TemplateComponent parseTemplateComponent(JsonObject object) {
        int dx = object.get("dx").getAsInt();
        int dz = object.get("dz").getAsInt();
        List<HashObservation> hashes = new ArrayList<>();
        JsonArray hashObjects = object.getAsJsonArray("hashes");
        if (hashObjects != null) {
            for (JsonElement hashElement : hashObjects) {
                JsonObject hashObject = hashElement.getAsJsonObject();
                hashes.add(new HashObservation(
                    hashObject.get("core").getAsInt(),
                    hashObject.has("stable") ? hashObject.get("stable").getAsInt() : 0,
                    hashObject.has("seen") ? hashObject.get("seen").getAsInt() : 1,
                    hashObject.has("updatedAt") ? hashObject.get("updatedAt").getAsLong() : 0
                ));
            }
        }

        readIntArray(object.getAsJsonArray("coreHashes")).forEach(coreHash ->
            hashes.add(new HashObservation(coreHash, 0, 1, 0)));
        readIntArray(object.getAsJsonArray("stableCoreHashes")).forEach(stableCoreHash ->
            hashes.add(new HashObservation(0, stableCoreHash, 1, 0)));

        if (hashes.isEmpty() && object.has("coreHash")) {
            hashes.add(new HashObservation(
                object.get("coreHash").getAsInt(),
                object.has("stableCoreHash") ? object.get("stableCoreHash").getAsInt() : 0,
                1,
                0
            ));
        }
        if (hashes.isEmpty()) {
            return null;
        }
        return new TemplateComponent(dx, dz, hashes);
    }

    private static List<Integer> readIntArray(JsonArray array) {
        if (array == null) {
            return List.of();
        }

        List<Integer> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsInt());
        }
        return values;
    }

    private static List<LearnedRoom> deduplicateByLatestCoreHash(List<LearnedRoom> learnedRooms) {
        Map<Integer, LearnedRoom> latestByCoreHash = new LinkedHashMap<>();
        for (LearnedRoom learnedRoom : learnedRooms) {
            LearnedRoom previous = latestByCoreHash.get(learnedRoom.coreHash());
            if (previous == null || learnedRoom.order() >= previous.order()) {
                latestByCoreHash.put(learnedRoom.coreHash(), learnedRoom);
            }
        }

        List<LearnedRoom> deduplicated = new ArrayList<>();
        for (LearnedRoom learnedRoom : learnedRooms) {
            LearnedRoom latest = latestByCoreHash.get(learnedRoom.coreHash());
            if (latest == null || keyFor(learnedRoom).equals(keyFor(latest))) {
                deduplicated.add(learnedRoom);
            }
        }

        deduplicated.sort(Comparator.comparingLong(LearnedRoom::order));
        return deduplicated;
    }

    private static TemplateKey keyFor(LearnedRoom learnedRoom) {
        CanonicalRoomMetadata metadata = canonicalMetadata(
            learnedRoom.name(),
            learnedRoom.type(),
            learnedRoom.secrets(),
            learnedRoom.crypts()
        );
        return new TemplateKey(metadata.name(), metadata.type(), metadata.secrets());
    }

    private static int maxCrypts(List<LearnedRoom> learnedRooms) {
        return learnedRooms.stream().mapToInt(LearnedRoom::crypts).max().orElse(0);
    }

    private static List<RoomTemplate> templatesFromLearnBursts(List<LearnedRoom> learnedRooms) {
        List<RoomTemplate> templates = new ArrayList<>();
        TemplateKey currentKey = null;
        List<LearnedRoom> currentBurst = new ArrayList<>();

        for (LearnedRoom learnedRoom : learnedRooms) {
            TemplateKey key = keyFor(learnedRoom);
            if (currentKey != null && !currentKey.equals(key)) {
                addTemplateFromBurst(templates, currentKey, currentBurst);
                currentBurst.clear();
            }

            currentKey = key;
            currentBurst.add(learnedRoom);
        }

        if (currentKey != null) {
            addTemplateFromBurst(templates, currentKey, currentBurst);
        }

        return templates;
    }

    private static void addTemplateFromBurst(
        List<RoomTemplate> templates,
        TemplateKey key,
        List<LearnedRoom> burst
    ) {
        if (burst.isEmpty()) {
            return;
        }

        int minX = burst.stream().mapToInt(LearnedRoom::roomGridX).min().orElse(0);
        int minZ = burst.stream().mapToInt(LearnedRoom::roomGridZ).min().orElse(0);

        Map<CellKey, ComponentBuilder> componentsByCell = new LinkedHashMap<>();
        for (LearnedRoom room : burst) {
            CellKey cell = new CellKey(room.roomGridX() - minX, room.roomGridZ() - minZ);
            componentsByCell
                .computeIfAbsent(cell, ignored -> new ComponentBuilder(cell.x(), cell.z()))
                .add(room.coreHash(), room.stableCoreHash(), room.order());
        }

        templates.add(new RoomTemplate(
            key.name(),
            key.type(),
            key.secrets(),
            maxCrypts(burst),
            legacyHasPrince(key.name()),
            templates.size(),
            componentsByCell.values().stream().map(ComponentBuilder::build).toList()
        ));
    }

    private static MatchedRoom tryMatch(
        DungeonMapSnapshot snapshot,
        RoomTemplateVariant variant,
        int anchorRoomGridX,
        int anchorRoomGridZ,
        Set<CellKey> occupiedCells
    ) {
        List<MatchedComponent> components = new ArrayList<>();
        for (TemplateComponent component : variant.components()) {
            int roomGridX = anchorRoomGridX + component.dx();
            int roomGridZ = anchorRoomGridZ + component.dz();
            if (!isValidRoomGrid(roomGridX, roomGridZ)) {
                return null;
            }

            CellKey cell = new CellKey(roomGridX, roomGridZ);
            if (occupiedCells.contains(cell)) {
                return null;
            }

            DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(roomGridX * 2, roomGridZ * 2);
            if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
                return null;
            }

            if (!matchesComponent(observedPoint.point(), component)) {
                return null;
            }

            components.add(new MatchedComponent(roomGridX, roomGridZ, observedPoint.point().coreHash()));
        }

        return new MatchedRoom(variant.template(), components);
    }

    private static void addSoftMatches(
        DungeonMapSnapshot snapshot,
        List<RoomTemplateVariant> variants,
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        Set<CellKey> occupiedCells,
        List<MatchedRoom> matches
    ) {
        List<SoftMatchedRoom> softMatches = new ArrayList<>();
        for (RoomTemplateVariant variant : variants) {
            if (variant.componentCount() < 3) {
                continue;
            }

            for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
                for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                    SoftMatchedRoom match = trySoftMatch(
                        snapshot,
                        variant,
                        knownHintsByCoreHash,
                        roomGridX,
                        roomGridZ,
                        occupiedCells
                    );
                    if (match != null) {
                        softMatches.add(match);
                    }
                }
            }
        }

        softMatches.sort((first, second) -> {
            int comparison = Integer.compare(second.exactComponentCount(), first.exactComponentCount());
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(second.componentCount(), first.componentCount());
            if (comparison != 0) {
                return comparison;
            }

            return first.matchedRoom().template().name().compareTo(second.matchedRoom().template().name());
        });

        for (SoftMatchedRoom softMatch : softMatches) {
            if (overlapsOccupiedCells(softMatch.matchedRoom(), occupiedCells)) {
                continue;
            }

            matches.add(softMatch.matchedRoom());
            for (MatchedComponent component : softMatch.matchedRoom().components()) {
                occupiedCells.add(new CellKey(component.roomGridX(), component.roomGridZ()));
            }
        }
    }

    private static SoftMatchedRoom trySoftMatch(
        DungeonMapSnapshot snapshot,
        RoomTemplateVariant variant,
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        int anchorRoomGridX,
        int anchorRoomGridZ,
        Set<CellKey> occupiedCells
    ) {
        List<MatchedComponent> components = new ArrayList<>();
        int exactComponentCount = 0;

        for (TemplateComponent component : variant.components()) {
            int roomGridX = anchorRoomGridX + component.dx();
            int roomGridZ = anchorRoomGridZ + component.dz();
            if (!isValidRoomGrid(roomGridX, roomGridZ)) {
                return null;
            }

            CellKey cell = new CellKey(roomGridX, roomGridZ);
            if (occupiedCells.contains(cell)) {
                return null;
            }

            DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(roomGridX * 2, roomGridZ * 2);
            if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
                return null;
            }
            if (DungeonRoomClassifier.isEmptyCore(observedPoint.point().coreHash())) {
                return null;
            }

            int observedCoreHash = observedPoint.point().coreHash();
            if (matchesComponent(observedPoint.point(), component)) {
                exactComponentCount++;
            } else {
                KnownCoreHint hint = knownHintForPoint(knownHintsByCoreHash, observedPoint.point());
                if (!matchesTemplateHint(hint, variant.template())) {
                    return null;
                }
            }

            components.add(new MatchedComponent(roomGridX, roomGridZ, observedCoreHash));
        }

        int requiredExactComponents = Math.max(
            MIN_SOFT_MATCHED_COMPONENTS,
            variant.componentCount() - 1
        );
        if (exactComponentCount < requiredExactComponents) {
            return null;
        }

        return new SoftMatchedRoom(new MatchedRoom(variant.template(), components), exactComponentCount);
    }

    private static void addCoreHintMatches(
        DungeonMapSnapshot snapshot,
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        Set<CellKey> occupiedCells,
        List<MatchedRoom> matches
    ) {
        Set<CellKey> visitedCells = new HashSet<>();
        for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                CellKey cell = new CellKey(roomGridX, roomGridZ);
                if (occupiedCells.contains(cell) || visitedCells.contains(cell)) {
                    continue;
                }

                DungeonMapSnapshot.ObservedPoint observedPoint = knownRoomPointAt(snapshot, cell);
                if (observedPoint == null) {
                    continue;
                }

                KnownCoreHint hint = knownHintForPoint(knownHintsByCoreHash, observedPoint.point());
                if (hint == null) {
                    continue;
                }

                List<MatchedComponent> group = connectedCoreHintGroup(
                    snapshot,
                    knownHintsByCoreHash,
                    occupiedCells,
                    visitedCells,
                    cell,
                    hint
                );
                if (group.isEmpty()) {
                    continue;
                }

                if (group.size() > MAX_TEMPLATE_COMPONENTS) {
                    KungMod.LOGGER.warn(
                        "Splitting suspicious core-only dungeon room hint for {}: {} connected cells.",
                        hint.name(),
                        group.size()
                    );
                    for (MatchedComponent component : group) {
                        MatchedRoom match = new MatchedRoom(templateFromHint(hint), List.of(component));
                        matches.add(match);
                        occupiedCells.add(new CellKey(component.roomGridX(), component.roomGridZ()));
                    }
                    continue;
                }

                MatchedRoom match = new MatchedRoom(templateFromHint(hint), List.copyOf(group));
                matches.add(match);
                for (MatchedComponent component : group) {
                    occupiedCells.add(new CellKey(component.roomGridX(), component.roomGridZ()));
                }
            }
        }
    }

    private static List<MatchedComponent> connectedCoreHintGroup(
        DungeonMapSnapshot snapshot,
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        Set<CellKey> occupiedCells,
        Set<CellKey> visitedCells,
        CellKey start,
        KnownCoreHint expectedHint
    ) {
        List<MatchedComponent> components = new ArrayList<>();
        List<CellKey> queue = new ArrayList<>();
        queue.add(start);
        visitedCells.add(start);

        for (int index = 0; index < queue.size(); index++) {
            CellKey cell = queue.get(index);
            DungeonMapSnapshot.ObservedPoint observedPoint = knownRoomPointAt(snapshot, cell);
            if (observedPoint == null) {
                continue;
            }

            KnownCoreHint hint = knownHintForPoint(knownHintsByCoreHash, observedPoint.point());
            if (!expectedHint.equals(hint)) {
                continue;
            }

            components.add(new MatchedComponent(cell.x(), cell.z(), observedPoint.point().coreHash()));

            for (CellKey neighbor : cell.neighbors()) {
                if (visitedCells.contains(neighbor)
                    || occupiedCells.contains(neighbor)
                    || !isValidRoomGrid(neighbor.x(), neighbor.z())
                    || hasVisibleDoorBetween(snapshot, cell, neighbor)) {
                    continue;
                }

                DungeonMapSnapshot.ObservedPoint neighborPoint = knownRoomPointAt(snapshot, neighbor);
                if (neighborPoint == null) {
                    continue;
                }
                KnownCoreHint neighborHint = knownHintForPoint(knownHintsByCoreHash, neighborPoint.point());
                if (!expectedHint.equals(neighborHint)) {
                    continue;
                }

                visitedCells.add(neighbor);
                queue.add(neighbor);
            }
        }

        return components;
    }

    private static DungeonMapSnapshot.ObservedPoint knownRoomPointAt(DungeonMapSnapshot snapshot, CellKey cell) {
        DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(cell.x() * 2, cell.z() * 2);
        if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
            return null;
        }
        if (DungeonRoomClassifier.isEmptyCore(observedPoint.point().coreHash())) {
            return null;
        }
        return observedPoint;
    }

    private static boolean hasVisibleDoorBetween(DungeonMapSnapshot snapshot, CellKey first, CellKey second) {
        int dx = Integer.compare(second.x(), first.x());
        int dz = Integer.compare(second.z(), first.z());
        DungeonMapSnapshot.ObservedPoint doorPoint = snapshot.pointAt(first.x() * 2 + dx, first.z() * 2 + dz);
        return doorPoint != null
            && doorPoint.point().kind() == DungeonScanPointKind.DOOR
            && doorPoint.point().doorKind().visible();
    }

    private static RoomTemplate templateFromHint(KnownCoreHint hint) {
        return new RoomTemplate(
            hint.name(),
            hint.type(),
            hint.secrets(),
            hint.crypts(),
            hint.prince(),
            -1,
            List.of()
        );
    }

    private static KnownCoreHint knownHintForPoint(
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        DungeonScanPoint point
    ) {
        KnownCoreHint hint = knownHintsByCoreHash.get(point.coreHash());
        if (hint == null && point.stableCoreHash() != 0) {
            hint = knownHintsByCoreHash.get(point.stableCoreHash());
        }
        return hint;
    }

    private static boolean matchesTemplateHint(KnownCoreHint hint, RoomTemplate template) {
        return hint != null
            && hint.name().equals(template.name())
            && hint.type() == template.type()
            && hint.secrets() == template.secrets();
    }

    private static Map<Integer, KnownCoreHint> knownHintsByCoreHash(List<RoomTemplate> templates) {
        Map<Integer, KnownCoreHint> hints = new HashMap<>();
        for (RoomTemplate template : templates) {
            for (TemplateComponent component : template.components()) {
                KnownCoreHint hint = new KnownCoreHint(
                    template.name(),
                    template.type(),
                    template.secrets(),
                    template.crypts(),
                    template.prince()
                );
                for (int coreHash : component.coreHashes()) {
                    hints.putIfAbsent(
                        coreHash,
                        hint
                    );
                }
                for (int stableCoreHash : component.stableCoreHashes()) {
                    hints.putIfAbsent(stableCoreHash, hint);
                }
            }
        }
        return hints;
    }

    private static void expandAdjacentKnownRoomHints(
        DungeonMapSnapshot snapshot,
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        List<RoomTemplateVariant> variants,
        Set<CellKey> occupiedCells,
        List<MatchedRoom> matches
    ) {
        Map<TemplateKey, Integer> maxComponentsByKey = maxComponentsByKey(variants);
        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < matches.size(); index++) {
                MatchedRoom match = matches.get(index);
                TemplateKey key = new TemplateKey(
                    match.template().name(),
                    match.template().type(),
                    match.template().secrets()
                );
                int maxComponents = maxComponentsByKey.getOrDefault(key, match.components().size());
                if (match.components().size() < 2 || match.components().size() >= maxComponents) {
                    continue;
                }

                MatchedComponent adjacent = adjacentKnownHint(snapshot, knownHintsByCoreHash, occupiedCells, match, key);
                if (adjacent == null) {
                    continue;
                }

                List<MatchedComponent> components = new ArrayList<>(match.components());
                components.add(adjacent);
                matches.set(index, new MatchedRoom(match.template(), List.copyOf(components)));
                occupiedCells.add(new CellKey(adjacent.roomGridX(), adjacent.roomGridZ()));
                changed = true;
            }
        } while (changed);
    }

    private static Map<TemplateKey, Integer> maxComponentsByKey(List<RoomTemplateVariant> variants) {
        Map<TemplateKey, Integer> maxComponentsByKey = new HashMap<>();
        for (RoomTemplateVariant variant : variants) {
            TemplateKey key = new TemplateKey(
                variant.template().name(),
                variant.template().type(),
                variant.template().secrets()
            );
            maxComponentsByKey.merge(key, variant.componentCount(), Math::max);
        }
        return maxComponentsByKey;
    }

    private static MatchedComponent adjacentKnownHint(
        DungeonMapSnapshot snapshot,
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        Set<CellKey> occupiedCells,
        MatchedRoom match,
        TemplateKey key
    ) {
        for (MatchedComponent component : match.components()) {
            for (CellKey neighbor : new CellKey(component.roomGridX(), component.roomGridZ()).neighbors()) {
                if (occupiedCells.contains(neighbor) || !isValidRoomGrid(neighbor.x(), neighbor.z())) {
                    continue;
                }

                DungeonMapSnapshot.ObservedPoint observedPoint = snapshot.pointAt(neighbor.x() * 2, neighbor.z() * 2);
                if (observedPoint == null || observedPoint.point().kind() != DungeonScanPointKind.ROOM) {
                    continue;
                }

                KnownCoreHint hint = knownHintsByCoreHash.get(observedPoint.point().coreHash());
                if (hint == null && observedPoint.point().stableCoreHash() != 0) {
                    hint = knownHintsByCoreHash.get(observedPoint.point().stableCoreHash());
                }
                if (hint == null
                    || !hint.name().equals(key.name())
                    || hint.type() != key.type()
                    || hint.secrets() != key.secrets()) {
                    continue;
                }

                return new MatchedComponent(neighbor.x(), neighbor.z(), observedPoint.point().coreHash());
            }
        }
        return null;
    }

    private static Map<Integer, KnownCoreHint> preloadCoreHints() {
        Map<Integer, KnownCoreHint> hints = new HashMap<>(STATIC_PRELOAD_CORE_HINTS);
        Map<Integer, Map<KnownCoreHint, Integer>> observations = new HashMap<>();
        readPreloadObservations(
            bundledLines(BUNDLED_PRELOADS_RESOURCE),
            observations,
            MIN_DYNAMIC_PRELOAD_OBSERVATIONS
        );
        Path file = knownRoomPreloadsFile();
        if (KungConfig.get().dungeon.localRoomDataEnabled() && Files.exists(file)) {
            try {
                readPreloadObservations(Files.readAllLines(file, StandardCharsets.UTF_8), observations, 1);
            } catch (IOException | RuntimeException exception) {
                KungMod.LOGGER.warn("Failed to load dungeon room preload hints.", exception);
            }
        }

        for (Map.Entry<Integer, Map<KnownCoreHint, Integer>> entry : observations.entrySet()) {
            if (hints.containsKey(entry.getKey()) || entry.getValue().size() != 1) {
                continue;
            }

            Map.Entry<KnownCoreHint, Integer> hint = entry.getValue().entrySet().iterator().next();
            if (hint.getValue() >= MIN_DYNAMIC_PRELOAD_OBSERVATIONS) {
                hints.put(entry.getKey(), hint.getKey());
            }
        }
        return hints;
    }

    private static void readPreloadObservations(
        List<String> lines,
        Map<Integer, Map<KnownCoreHint, Integer>> observations,
        int weight
    ) {
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            JsonObject object = JsonParser.parseString(stripBom(line)).getAsJsonObject();
            int coreHash = object.get("coreHash").getAsInt();
            KnownCoreHint hint = new KnownCoreHint(
                object.get("name").getAsString(),
                RoomType.valueOf(object.get("type").getAsString()),
                object.get("secrets").getAsInt()
            );
            observations
                .computeIfAbsent(coreHash, ignored -> new HashMap<>())
                .merge(hint, weight, Integer::sum);
        }
    }

    private static List<String> bundledLines(String resourcePath) {
        try (InputStream stream = DungeonKnownRoomCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return List.of();
            }

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException | RuntimeException exception) {
            KungMod.LOGGER.warn("Failed to load bundled dungeon room data from {}.", resourcePath, exception);
            return List.of();
        }
    }

    private static String bundledText(String resourcePath) {
        try (InputStream stream = DungeonKnownRoomCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            KungMod.LOGGER.warn("Failed to load bundled dungeon room JSON data from {}.", resourcePath, exception);
            return "";
        }
    }

    private static void appendPreloadHint(int coreHash, KnownCoreHint hint) throws IOException {
        Path file = knownRoomPreloadsFile();
        Files.createDirectories(file.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )) {
            writer.write("{"
                + "\"coreHash\":" + coreHash + ","
                + "\"name\":" + jsonString(hint.name()) + ","
                + "\"type\":\"" + hint.type().name() + "\","
                + "\"secrets\":" + hint.secrets()
                + "}");
            writer.newLine();
        }
    }

    private static boolean hasConflictingKnownHint(
        Map<Integer, KnownCoreHint> knownHintsByCoreHash,
        int coreHash,
        int stableCoreHash,
        RoomTemplate template
    ) {
        KnownCoreHint hint = knownHintsByCoreHash.get(coreHash);
        if (hint == null && stableCoreHash != 0) {
            hint = knownHintsByCoreHash.get(stableCoreHash);
        }
        return hint != null
            && (!hint.name().equals(template.name())
                || hint.type() != template.type()
                || hint.secrets() != template.secrets());
    }

    private static boolean matchesComponent(DungeonScanPoint point, TemplateComponent component) {
        return component.matches(point.coreHash(), point.stableCoreHash());
    }

    private static boolean isTrustedHint(int coreHash, RoomTemplate template) {
        KnownCoreHint hint = knownCoreHint(coreHash);
        return hint != null
            && hint.name().equals(template.name())
            && hint.type() == template.type()
            && hint.secrets() == template.secrets();
    }

    private static boolean hasKnownStableHash(int coreHash, int stableCoreHash) {
        for (RoomTemplate template : templateCache().templates()) {
            for (TemplateComponent component : template.components()) {
                if (component.hasHash(coreHash, stableCoreHash)
                    || (stableCoreHash != 0 && component.stableCoreHashes().contains(stableCoreHash))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlapsOccupiedCells(MatchedRoom match, Set<CellKey> occupiedCells) {
        for (MatchedComponent component : match.components()) {
            if (occupiedCells.contains(new CellKey(component.roomGridX(), component.roomGridZ()))) {
                return true;
            }
        }
        return false;
    }

    private static List<RoomTemplate> templatesFor(TemplateKey key, List<LearnedRoom> learnedRooms) {
        List<List<LearnedRoom>> connectedGroups = connectedGroups(learnedRooms);
        List<RoomTemplate> templates = new ArrayList<>();
        int variantNumber = 0;
        for (List<LearnedRoom> group : connectedGroups) {
            if (group.size() > MAX_TEMPLATE_COMPONENTS) {
                KungMod.LOGGER.warn(
                    "Skipping suspicious dungeon room variant for {}: {} connected cells.",
                    key.name(),
                    group.size()
                );
                continue;
            }
            int minX = group.stream().mapToInt(LearnedRoom::roomGridX).min().orElse(0);
            int minZ = group.stream().mapToInt(LearnedRoom::roomGridZ).min().orElse(0);

        Map<CellKey, ComponentBuilder> componentsByCell = new LinkedHashMap<>();
        for (LearnedRoom room : group) {
            CellKey cell = new CellKey(room.roomGridX() - minX, room.roomGridZ() - minZ);
            componentsByCell
                .computeIfAbsent(cell, ignored -> new ComponentBuilder(cell.x(), cell.z()))
                .add(room.coreHash(), room.stableCoreHash(), room.order());
        }

        templates.add(new RoomTemplate(
            key.name(),
            key.type(),
            key.secrets(),
            maxCrypts(group),
            legacyHasPrince(key.name()),
            variantNumber++,
            componentsByCell.values().stream().map(ComponentBuilder::build).toList()
        ));
        }
        return templates;
    }

    private static List<List<LearnedRoom>> connectedGroups(List<LearnedRoom> learnedRooms) {
        Map<CellKey, LearnedRoom> roomsByCell = new LinkedHashMap<>();
        for (LearnedRoom learnedRoom : learnedRooms) {
            roomsByCell.put(new CellKey(learnedRoom.roomGridX(), learnedRoom.roomGridZ()), learnedRoom);
        }

        List<List<LearnedRoom>> groups = new ArrayList<>();
        Set<CellKey> visited = new HashSet<>();
        for (CellKey start : roomsByCell.keySet()) {
            if (visited.contains(start)) {
                continue;
            }

            List<LearnedRoom> group = new ArrayList<>();
            List<CellKey> queue = new ArrayList<>();
            queue.add(start);
            visited.add(start);
            for (int index = 0; index < queue.size(); index++) {
                CellKey cell = queue.get(index);
                group.add(roomsByCell.get(cell));
                for (CellKey neighbor : cell.neighbors()) {
                    if (roomsByCell.containsKey(neighbor) && visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            groups.add(group);
        }

        groups.sort(Comparator.comparingInt(List<LearnedRoom>::size).reversed());
        return groups;
    }

    private static boolean isValidTemplateShape(List<TemplateComponent> components) {
        if (components.isEmpty() || components.size() > MAX_TEMPLATE_COMPONENTS) {
            return false;
        }

        int minX = components.stream().mapToInt(TemplateComponent::dx).min().orElse(0);
        int maxX = components.stream().mapToInt(TemplateComponent::dx).max().orElse(0);
        int minZ = components.stream().mapToInt(TemplateComponent::dz).min().orElse(0);
        int maxZ = components.stream().mapToInt(TemplateComponent::dz).max().orElse(0);
        if (maxX - minX + 1 > MAX_TEMPLATE_SPAN || maxZ - minZ + 1 > MAX_TEMPLATE_SPAN) {
            return false;
        }

        Set<CellKey> cells = new HashSet<>();
        for (TemplateComponent component : components) {
            cells.add(new CellKey(component.dx(), component.dz()));
        }
        Set<CellKey> visited = new HashSet<>();
        List<CellKey> queue = new ArrayList<>();
        CellKey start = cells.iterator().next();
        queue.add(start);
        visited.add(start);
        for (int index = 0; index < queue.size(); index++) {
            for (CellKey neighbor : queue.get(index).neighbors()) {
                if (cells.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited.size() == cells.size();
    }

    private static LearnedRoom parseLearnedRoom(String jsonLine, long order) {
        JsonObject object = JsonParser.parseString(stripBom(jsonLine)).getAsJsonObject();
        String name = object.get("name").getAsString();
        RoomType type = RoomType.valueOf(object.get("type").getAsString());
        int secrets = object.get("secrets").getAsInt();
        int crypts = object.has("crypts") ? object.get("crypts").getAsInt() : 0;
        boolean prince = object.has("prince")
            ? object.get("prince").getAsBoolean()
            : legacyHasPrince(name);
        CanonicalRoomMetadata metadata = canonicalMetadata(name, type, secrets, crypts, prince);
        return new LearnedRoom(
            order,
            metadata.name(),
            metadata.type(),
            metadata.secrets(),
            metadata.crypts(),
            object.get("coreHash").getAsInt(),
            object.has("stableCoreHash") ? object.get("stableCoreHash").getAsInt() : 0,
            object.get("roomGridX").getAsInt(),
            object.get("roomGridZ").getAsInt()
        );
    }

    private static CanonicalRoomMetadata canonicalMetadata(String name, RoomType type, int secrets, int crypts) {
        return canonicalMetadata(name, type, secrets, crypts, legacyHasPrince(name));
    }

    private static CanonicalRoomMetadata canonicalMetadata(
        String name,
        RoomType type,
        int secrets,
        int crypts,
        boolean prince
    ) {
        CanonicalRoomMetadata override = CANONICAL_METADATA_OVERRIDES.get(canonicalNameKey(name));
        return override == null
            ? new CanonicalRoomMetadata(name, type, secrets, Math.max(0, crypts), prince)
            : override;
    }

    private static LearnedRoom canonicalLearnedRoom(LearnedRoom room) {
        CanonicalRoomMetadata metadata = canonicalMetadata(room.name(), room.type(), room.secrets(), room.crypts());
        return new LearnedRoom(
            room.order(),
            metadata.name(),
            metadata.type(),
            metadata.secrets(),
            metadata.crypts(),
            room.coreHash(),
            room.stableCoreHash(),
            room.roomGridX(),
            room.roomGridZ()
        );
    }

    private static RoomTemplate canonicalTemplate(RoomTemplate template) {
        CanonicalRoomMetadata metadata = canonicalMetadata(
            template.name(),
            template.type(),
            template.secrets(),
            template.crypts(),
            template.prince()
        );
        return new RoomTemplate(
            metadata.name(),
            metadata.type(),
            metadata.secrets(),
            metadata.crypts(),
            metadata.prince(),
            template.variantNumber(),
            template.components()
        );
    }

    public static boolean hasPrince(String name) {
        String key = canonicalNameKey(name);
        TemplateCache templateCache = templateCache();
        boolean found = false;
        boolean prince = false;
        for (RoomTemplate template : templateCache.templates()) {
            if (canonicalNameKey(template.name()).equals(key)) {
                found = true;
                prince |= template.prince();
            }
        }
        for (KnownCoreHint hint : templateCache.knownCoreHints().values()) {
            if (canonicalNameKey(hint.name()).equals(key)) {
                found = true;
                prince |= hint.prince();
            }
        }
        return found ? prince : legacyHasPrince(name);
    }

    private static boolean legacyHasPrince(String name) {
        String key = canonicalNameKey(name);
        return LEGACY_PRINCE_ROOM_NAMES.contains(key) && !LEGACY_NON_PRINCE_ROOM_NAMES.contains(key);
    }

    private static Set<String> canonicalNameSet(String... names) {
        Set<String> keys = new HashSet<>();
        for (String name : names) {
            keys.add(canonicalNameKey(name));
        }
        return Set.copyOf(keys);
    }

    private static String canonicalNameKey(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String stripBom(String line) {
        if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
            return line.substring(1);
        }
        return line;
    }

    private static String toJsonLine(LearnedRoom room) {
        return "{"
            + "\"name\":" + jsonString(room.name()) + ","
            + "\"type\":\"" + room.type().name() + "\","
            + "\"secrets\":" + room.secrets() + ","
            + (room.crypts() <= 0 ? "" : "\"crypts\":" + room.crypts() + ",")
            + "\"coreHash\":" + room.coreHash() + ","
            + (room.stableCoreHash() == 0 ? "" : "\"stableCoreHash\":" + room.stableCoreHash() + ",")
            + "\"roomGridX\":" + room.roomGridX() + ","
            + "\"roomGridZ\":" + room.roomGridZ()
            + "}";
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    private static int lastNonBlankLineIndex(List<String> lines) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (!lines.get(index).isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private static void appendUndoneLine(String undoneLine) throws IOException {
        Path file = undoneRoomsFile();
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )) {
            writer.write(undoneLine);
            writer.newLine();
        }
    }

    private static String shortLine(String line) {
        if (line.length() <= 140) {
            return line;
        }
        return line.substring(0, 137) + "...";
    }

    private static boolean isValidRoomGrid(int roomGridX, int roomGridZ) {
        return roomGridX >= 0
            && roomGridZ >= 0
            && roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    private static Path knownRoomsFile() {
        return KungPaths.dungeonDataDirectory().resolve("known-rooms.json");
    }

    static Path remoteKnownRoomsFile() {
        return KungPaths.dungeonDataDirectory().resolve("known-rooms-remote.json");
    }

    private static Path legacyKnownRoomsFile() {
        return KungPaths.dungeonDataDirectory().resolve("known-rooms.jsonl");
    }

    private static Path knownRoomPreloadsFile() {
        return KungPaths.dungeonDataDirectory().resolve("known-room-preloads.jsonl");
    }

    private static Path undoneRoomsFile() {
        return KungPaths.dungeonDataDirectory().resolve("known-rooms-undone.jsonl");
    }

    private static final class RoomDatabase {
        private final Map<TemplateKey, RoomData> rooms = new LinkedHashMap<>();
        private final List<DeletedRoom> deletedRooms = new ArrayList<>();

        void merge(RoomTemplate template) {
            template = canonicalTemplate(template);
            if (!isValidTemplateShape(template.components())) {
                KungMod.LOGGER.warn(
                    "Skipping suspicious dungeon room variant for {}: {} cells.",
                    template.name(),
                    template.components().size()
                );
                return;
            }
            TemplateKey key = new TemplateKey(template.name(), template.type(), template.secrets());
            rooms.computeIfAbsent(key, RoomData::new).merge(template);
        }

        void ensureRoom(TemplateKey key, int crypts, boolean prince) {
            CanonicalRoomMetadata metadata = canonicalMetadata(key.name(), key.type(), key.secrets(), crypts, prince);
            key = new TemplateKey(metadata.name(), metadata.type(), metadata.secrets());
            crypts = metadata.crypts();
            prince = metadata.prince();
            RoomData room = rooms.computeIfAbsent(key, RoomData::new);
            room.updateCrypts(crypts);
            room.updatePrince(prince);
        }

        void upsert(LearnedRoom room) {
            room = canonicalLearnedRoom(room);
            TemplateKey key = keyFor(room);
            rooms.computeIfAbsent(key, RoomData::new).upsert(room);
        }

        boolean updateCrypts(TemplateKey key, int crypts) {
            CanonicalRoomMetadata metadata = canonicalMetadata(key.name(), key.type(), key.secrets(), crypts);
            key = new TemplateKey(metadata.name(), metadata.type(), metadata.secrets());
            crypts = metadata.crypts();
            RoomData room = rooms.get(key);
            if (room == null) {
                return false;
            }
            room.updateCrypts(crypts);
            return true;
        }

        DeleteRoomResult deleteRoom(String name, RoomType type, int secrets, boolean explicitMetadata) {
            List<Integer> coreHashes = new ArrayList<>();
            int removedRooms = 0;
            int removedVariants = 0;
            for (Map.Entry<TemplateKey, RoomData> entry : new ArrayList<>(rooms.entrySet())) {
                TemplateKey key = entry.getKey();
                if (!matchesDelete(key, name, type, secrets, explicitMetadata)) {
                    continue;
                }
                RoomData room = entry.getValue();
                coreHashes.addAll(room.coreHashes());
                removedVariants += room.variantCount();
                rooms.remove(key);
                removedRooms++;
            }

            if (removedRooms <= 0) {
                return DeleteRoomResult.nothingDeleted(name, explicitMetadata ? type : null, explicitMetadata ? secrets : null);
            }

            addDeletedRoom(new DeletedRoom(name, explicitMetadata ? type : null, explicitMetadata ? secrets : null));
            return DeleteRoomResult.deleted(
                name,
                explicitMetadata ? type : null,
                explicitMetadata ? secrets : null,
                removedRooms,
                removedVariants,
                List.copyOf(coreHashes)
            );
        }

        private void addDeletedRoom(DeletedRoom deletedRoom) {
            deletedRooms.removeIf(existing -> existing.overlaps(deletedRoom));
            deletedRooms.add(deletedRoom);
        }

        List<RoomTemplate> templates() {
            List<RoomTemplate> templates = new ArrayList<>();
            for (RoomData room : rooms.values()) {
                templates.addAll(room.templates());
            }
            return templates;
        }

        String toJson() {
            StringBuilder json = new StringBuilder(4096);
            json.append("{\n  \"schema\": 1,\n");
            if (!deletedRooms.isEmpty()) {
                json.append("  \"deletedRooms\": [\n");
                for (int index = 0; index < deletedRooms.size(); index++) {
                    if (index > 0) {
                        json.append(",\n");
                    }
                    deletedRooms.get(index).writeJson(json, "    ");
                }
                json.append("\n  ],\n");
            }
            json.append("  \"rooms\": [\n");
            int roomIndex = 0;
            for (RoomData room : rooms.values()) {
                if (roomIndex++ > 0) {
                    json.append(",\n");
                }
                room.writeJson(json, "    ");
            }
            json.append("\n  ]\n}\n");
            return json.toString();
        }
    }

    private static boolean matchesDelete(
        TemplateKey key,
        String name,
        RoomType type,
        int secrets,
        boolean explicitMetadata
    ) {
        if (!explicitMetadata) {
            return canonicalNameKey(key.name()).equals(canonicalNameKey(name));
        }

        CanonicalRoomMetadata metadata = canonicalMetadata(name, type, secrets, 0);
        return canonicalNameKey(key.name()).equals(canonicalNameKey(metadata.name()))
            && key.type() == metadata.type()
            && key.secrets() == metadata.secrets();
    }

    private record DeletedRoom(String name, RoomType type, Integer secrets) {
        boolean overlaps(DeletedRoom other) {
            if (!canonicalNameKey(name).equals(canonicalNameKey(other.name()))) {
                return false;
            }
            if (type == null || other.type() == null) {
                return true;
            }
            CanonicalRoomMetadata metadata = canonicalMetadata(name, type, secrets, 0);
            CanonicalRoomMetadata otherMetadata = canonicalMetadata(other.name(), other.type(), other.secrets(), 0);
            return metadata.type() == otherMetadata.type() && metadata.secrets() == otherMetadata.secrets();
        }

        void writeJson(StringBuilder json, String indent) {
            json.append(indent).append("{\"name\":").append(jsonString(name));
            if (type != null && secrets != null) {
                json.append(",\"type\":\"")
                    .append(type.name())
                    .append("\",\"secrets\":")
                    .append(secrets);
            }
            json.append("}");
        }
    }

    private static final class RoomData {
        private final TemplateKey key;
        private final List<VariantData> variants = new ArrayList<>();
        private int crypts;
        private boolean prince;

        RoomData(TemplateKey key) {
            this.key = key;
        }

        void merge(RoomTemplate template) {
            crypts = Math.max(crypts, template.crypts());
            prince |= template.prince();
            VariantData incoming = VariantData.from(template.components());
            for (VariantData variant : variants) {
                if (variant.sameShape(incoming)) {
                    variant.merge(incoming);
                    return;
                }
            }
            variants.add(incoming);
        }

        void upsert(LearnedRoom room) {
            crypts = Math.max(crypts, room.crypts());
            for (VariantData variant : variants) {
                if (variant.upsertMatchingHash(room)) {
                    return;
                }
            }

            ComponentBuilder builder = new ComponentBuilder(0, 0);
            builder.add(room.coreHash(), room.stableCoreHash(), room.order());
            variants.add(VariantData.from(List.of(builder.build())));
        }

        void updateCrypts(int crypts) {
            this.crypts = Math.max(0, crypts);
        }

        void updatePrince(boolean prince) {
            this.prince = prince;
        }

        int variantCount() {
            return variants.size();
        }

        List<Integer> coreHashes() {
            List<Integer> hashes = new ArrayList<>();
            for (VariantData variant : variants) {
                hashes.addAll(variant.coreHashes());
            }
            return hashes;
        }

        List<RoomTemplate> templates() {
            List<RoomTemplate> templates = new ArrayList<>();
            int variantNumber = 0;
            for (VariantData variant : variants) {
                templates.add(new RoomTemplate(
                    key.name(),
                    key.type(),
                    key.secrets(),
                    crypts,
                    prince,
                    variantNumber++,
                    variant.components()
                ));
            }
            return templates;
        }

        void writeJson(StringBuilder json, String indent) {
            json.append(indent)
                .append("{\"name\":")
                .append(jsonString(key.name()))
                .append(",\"type\":\"")
                .append(key.type().name())
                .append("\",\"secrets\":")
                .append(key.secrets())
                .append(",\"crypts\":")
                .append(crypts)
                .append(",\"prince\":")
                .append(prince)
                .append(",\"variants\":[");
            for (int index = 0; index < variants.size(); index++) {
                if (index > 0) {
                    json.append(",");
                }
                json.append("\n");
                variants.get(index).writeJson(json, indent + "  ");
            }
            if (!variants.isEmpty()) {
                json.append("\n").append(indent);
            }
            json.append("]}");
        }
    }

    private static final class VariantData {
        private final Map<CellKey, ComponentBuilder> components = new LinkedHashMap<>();

        static VariantData from(List<TemplateComponent> templateComponents) {
            VariantData variant = new VariantData();
            for (TemplateComponent component : templateComponents) {
                ComponentBuilder builder = variant.components.computeIfAbsent(
                    new CellKey(component.dx(), component.dz()),
                    ignored -> new ComponentBuilder(component.dx(), component.dz())
                );
                for (HashObservation hash : component.hashes()) {
                    builder.add(hash.coreHash(), hash.stableCoreHash(), hash.updatedAt(), hash.seen());
                }
            }
            return variant;
        }

        boolean sameShape(VariantData other) {
            return components.keySet().equals(other.components.keySet());
        }

        boolean overlapsHashes(VariantData other) {
            for (ComponentBuilder component : components.values()) {
                for (ComponentBuilder otherComponent : other.components.values()) {
                    if (component.overlapsHashes(otherComponent)) {
                        return true;
                    }
                }
            }
            return false;
        }

        void merge(VariantData other) {
            if (sameShape(other)) {
                for (Map.Entry<CellKey, ComponentBuilder> entry : other.components.entrySet()) {
                    components.get(entry.getKey()).merge(entry.getValue());
                }
                return;
            }

            for (Map.Entry<CellKey, ComponentBuilder> entry : other.components.entrySet()) {
                components
                    .computeIfAbsent(entry.getKey(), ignored -> new ComponentBuilder(entry.getValue().dx, entry.getValue().dz))
                    .merge(entry.getValue());
            }
        }

        List<Integer> coreHashes() {
            List<Integer> hashes = new ArrayList<>();
            for (ComponentBuilder component : components.values()) {
                hashes.addAll(component.coreHashes());
            }
            return hashes;
        }

        boolean upsertMatchingHash(LearnedRoom room) {
            for (ComponentBuilder component : components.values()) {
                if (component.matches(room.coreHash(), room.stableCoreHash())) {
                    component.add(room.coreHash(), room.stableCoreHash(), room.order());
                    return true;
                }
            }
            return false;
        }

        List<TemplateComponent> components() {
            return components.values().stream().map(ComponentBuilder::build).toList();
        }

        void writeJson(StringBuilder json, String indent) {
            json.append(indent).append("{\"components\":[");
            int index = 0;
            for (ComponentBuilder component : components.values()) {
                if (index++ > 0) {
                    json.append(",");
                }
                json.append("\n");
                component.writeJson(json, indent + "  ");
            }
            if (!components.isEmpty()) {
                json.append("\n").append(indent);
            }
            json.append("]}");
        }
    }

    private static final class ComponentBuilder {
        private final int dx;
        private final int dz;
        private final List<HashObservation> hashes = new ArrayList<>();

        ComponentBuilder(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        void add(int coreHash, int stableCoreHash, long updatedAt) {
            add(coreHash, stableCoreHash, updatedAt, 1);
        }

        void add(int coreHash, int stableCoreHash, long updatedAt, int seen) {
            if (coreHash == 0 && stableCoreHash == 0) {
                return;
            }
            for (int index = 0; index < hashes.size(); index++) {
                HashObservation hash = hashes.get(index);
                if (hash.coreHash() == coreHash && hash.stableCoreHash() == stableCoreHash) {
                    hashes.set(index, new HashObservation(
                        coreHash,
                        stableCoreHash,
                        hash.seen() + Math.max(1, seen),
                        Math.max(hash.updatedAt(), updatedAt)
                    ));
                    return;
                }
            }
            hashes.add(new HashObservation(coreHash, stableCoreHash, Math.max(1, seen), updatedAt));
        }

        boolean matches(int coreHash, int stableCoreHash) {
            for (HashObservation hash : hashes) {
                if (stableCoreHash != 0 && hash.stableCoreHash() == stableCoreHash) {
                    return true;
                }
            }
            for (HashObservation hash : hashes) {
                if (hash.coreHash() != 0 && hash.coreHash() == coreHash) {
                    return true;
                }
            }
            return false;
        }

        boolean overlapsHashes(ComponentBuilder other) {
            for (HashObservation hash : hashes) {
                if (other.matches(hash.coreHash(), hash.stableCoreHash())) {
                    return true;
                }
            }
            return false;
        }

        void merge(ComponentBuilder other) {
            for (HashObservation hash : other.hashes) {
                add(hash.coreHash(), hash.stableCoreHash(), hash.updatedAt(), hash.seen());
            }
        }

        List<Integer> coreHashes() {
            List<Integer> values = new ArrayList<>();
            for (HashObservation hash : hashes) {
                if (hash.coreHash() != 0 && !values.contains(hash.coreHash())) {
                    values.add(hash.coreHash());
                }
            }
            return values;
        }

        TemplateComponent build() {
            return new TemplateComponent(dx, dz, List.copyOf(hashes));
        }

        void writeJson(StringBuilder json, String indent) {
            json.append(indent)
                .append("{\"dx\":")
                .append(dx)
                .append(",\"dz\":")
                .append(dz)
                .append(",\"hashes\":[");
            for (int index = 0; index < hashes.size(); index++) {
                HashObservation hash = hashes.get(index);
                if (index > 0) {
                    json.append(",");
                }
                json.append("{\"core\":")
                    .append(hash.coreHash())
                    .append(",\"stable\":")
                    .append(hash.stableCoreHash())
                    .append(",\"seen\":")
                    .append(hash.seen())
                    .append(",\"updatedAt\":")
                    .append(hash.updatedAt())
                    .append("}");
            }
            json.append("]}");
        }
    }

    public record LearnedRoom(
        long order,
        String name,
        RoomType type,
        int secrets,
        int crypts,
        int coreHash,
        int stableCoreHash,
        int roomGridX,
        int roomGridZ
    ) {
    }

    public record UndoResult(boolean undone, String message) {
        static UndoResult undone(String message) {
            return new UndoResult(true, message);
        }

        static UndoResult nothingToUndo(String message) {
            return new UndoResult(false, message);
        }
    }

    public record DeleteRoomResult(
        boolean deleted,
        String message,
        String name,
        RoomType type,
        Integer secrets,
        int roomCount,
        int variantCount,
        List<Integer> coreHashes
    ) {
        static DeleteRoomResult deleted(
            String name,
            RoomType type,
            Integer secrets,
            int roomCount,
            int variantCount,
            List<Integer> coreHashes
        ) {
            return new DeleteRoomResult(
                true,
                "Room data deleted: "
                    + name
                    + (type == null ? "" : " " + type.name() + " secrets=" + secrets)
                    + " rooms=" + roomCount
                    + " variants=" + variantCount
                    + " hashes=" + coreHashes.size(),
                name,
                type,
                secrets,
                roomCount,
                variantCount,
                coreHashes
            );
        }

        static DeleteRoomResult nothingDeleted(String name, RoomType type, Integer secrets) {
            return new DeleteRoomResult(
                false,
                "No room data found for: "
                    + name
                    + (type == null ? "" : " " + type.name() + " secrets=" + secrets),
                name,
                type,
                secrets,
                0,
                0,
                List.of()
            );
        }
    }

    private record LatestJsonLearn(String name, String type, int secrets, long updatedAt) {
        boolean matches(JsonObject room) {
            return name.equals(room.get("name").getAsString())
                && type.equals(room.get("type").getAsString())
                && secrets == room.get("secrets").getAsInt();
        }
    }

    public record RoomTemplate(
        String name,
        RoomType type,
        int secrets,
        int crypts,
        boolean prince,
        int variantNumber,
        List<TemplateComponent> components
    ) {
        List<RoomTemplateVariant> variants() {
            Map<String, RoomTemplateVariant> variantsBySignature = new LinkedHashMap<>();
            for (boolean mirrored : List.of(false, true)) {
                for (int rotation = 0; rotation < 4; rotation++) {
                    List<TemplateComponent> transformedComponents = new ArrayList<>();
                    for (TemplateComponent component : components) {
                        transformedComponents.add(rotate(mirror(component, mirrored), rotation));
                    }
                    List<TemplateComponent> normalized = normalize(transformedComponents);
                    variantsBySignature.putIfAbsent(signature(normalized), new RoomTemplateVariant(this, normalized));
                }
            }
            return List.copyOf(variantsBySignature.values());
        }

        private static TemplateComponent mirror(TemplateComponent component, boolean mirrored) {
            if (!mirrored) {
                return component;
            }
            return new TemplateComponent(-component.dx(), component.dz(), component.hashes());
        }

        private static TemplateComponent rotate(TemplateComponent component, int rotation) {
            return switch (rotation) {
                case 1 -> new TemplateComponent(component.dz(), -component.dx(), component.hashes());
                case 2 -> new TemplateComponent(-component.dx(), -component.dz(), component.hashes());
                case 3 -> new TemplateComponent(-component.dz(), component.dx(), component.hashes());
                default -> component;
            };
        }

        private static List<TemplateComponent> normalize(List<TemplateComponent> components) {
            int minX = components.stream().mapToInt(TemplateComponent::dx).min().orElse(0);
            int minZ = components.stream().mapToInt(TemplateComponent::dz).min().orElse(0);
            List<TemplateComponent> normalized = new ArrayList<>();
            for (TemplateComponent component : components) {
                normalized.add(new TemplateComponent(
                    component.dx() - minX,
                    component.dz() - minZ,
                    component.hashes()
                ));
            }
            normalized.sort(Comparator
                .comparingInt(TemplateComponent::dz)
                .thenComparingInt(TemplateComponent::dx)
                .thenComparing(TemplateComponent::hashSignature));
            return normalized;
        }

        private static String signature(List<TemplateComponent> components) {
            StringBuilder signature = new StringBuilder();
            for (TemplateComponent component : components) {
                signature.append(component.dx())
                    .append(',')
                    .append(component.dz())
                    .append('=')
                    .append(component.hashSignature())
                    .append(';');
            }
            return signature.toString();
        }
    }

    public record RoomTemplateVariant(RoomTemplate template, List<TemplateComponent> components) {
        int componentCount() {
            return components.size();
        }
    }

    public record TemplateComponent(int dx, int dz, List<HashObservation> hashes) {
        public TemplateComponent(int dx, int dz, int coreHash, int stableCoreHash) {
            this(dx, dz, List.of(new HashObservation(coreHash, stableCoreHash, 1, 0)));
        }

        int coreHash() {
            return hashes.isEmpty() ? 0 : hashes.get(0).coreHash();
        }

        int stableCoreHash() {
            return hashes.isEmpty() ? 0 : hashes.get(0).stableCoreHash();
        }

        List<Integer> coreHashes() {
            List<Integer> values = new ArrayList<>();
            for (HashObservation hash : hashes) {
                if (hash.coreHash() != 0 && !values.contains(hash.coreHash())) {
                    values.add(hash.coreHash());
                }
            }
            return values;
        }

        List<Integer> stableCoreHashes() {
            List<Integer> values = new ArrayList<>();
            for (HashObservation hash : hashes) {
                if (hash.stableCoreHash() != 0 && !values.contains(hash.stableCoreHash())) {
                    values.add(hash.stableCoreHash());
                }
            }
            return values;
        }

        boolean matches(int coreHash, int stableCoreHash) {
            for (HashObservation hash : hashes) {
                if (stableCoreHash != 0 && hash.stableCoreHash() == stableCoreHash) {
                    return true;
                }
            }
            for (HashObservation hash : hashes) {
                if (hash.coreHash() != 0 && hash.coreHash() == coreHash) {
                    return true;
                }
            }
            return false;
        }

        boolean hasHash(int coreHash, int stableCoreHash) {
            for (HashObservation hash : hashes) {
                if (hash.coreHash() == coreHash && hash.stableCoreHash() == stableCoreHash) {
                    return true;
                }
            }
            return false;
        }

        String hashSignature() {
            List<String> values = new ArrayList<>();
            for (HashObservation hash : hashes) {
                values.add(hash.coreHash() + "/" + hash.stableCoreHash());
            }
            values.sort(String::compareTo);
            return String.join(",", values);
        }
    }

    private record HashObservation(int coreHash, int stableCoreHash, int seen, long updatedAt) {
        HashObservation merge(int coreHash, int stableCoreHash, long updatedAt) {
            if (this.coreHash != coreHash || this.stableCoreHash != stableCoreHash) {
                return this;
            }
            return new HashObservation(coreHash, stableCoreHash, seen + 1, Math.max(this.updatedAt, updatedAt));
        }
    }

    public record AutoLearnResult(boolean learned, int componentCount, String roomName) {
        static AutoLearnResult none() {
            return new AutoLearnResult(false, 0, "");
        }
    }

    public record RemoteCacheResult(boolean updated, int roomCount, int variantCount, int componentCount) {
    }

    public record MatchedRoom(RoomTemplate template, List<MatchedComponent> components) {
        public boolean contains(int roomGridX, int roomGridZ) {
            for (MatchedComponent component : components) {
                if (component.roomGridX() == roomGridX && component.roomGridZ() == roomGridZ) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasAdjacentComponents(int firstRoomGridX, int firstRoomGridZ, int secondRoomGridX, int secondRoomGridZ) {
            return contains(firstRoomGridX, firstRoomGridZ) && contains(secondRoomGridX, secondRoomGridZ);
        }
    }

    public record MatchedComponent(int roomGridX, int roomGridZ, int coreHash) {
    }

    private record SoftMatchedRoom(MatchedRoom matchedRoom, int exactComponentCount) {
        int componentCount() {
            return matchedRoom.components().size();
        }
    }

    public record KnownCoreHint(String name, RoomType type, int secrets, int crypts, boolean prince) {
        public KnownCoreHint(String name, RoomType type, int secrets) {
            this(name, type, secrets, 0, false);
        }

        public KnownCoreHint(String name, RoomType type, int secrets, int crypts) {
            this(name, type, secrets, crypts, false);
        }
    }

    public record KnownRoomInfo(String name, RoomType type, int secrets, int crypts, boolean prince) {
    }

    private record TemplateCache(
        Path file,
        long modifiedMillis,
        long size,
        List<RoomTemplate> templates,
        Map<Integer, KnownCoreHint> knownCoreHints,
        Map<Integer, KnownCoreHint> preloadCoreHints
    ) {
    }

    private record TemplateKey(String name, RoomType type, int secrets) {
    }

    private record CanonicalRoomMetadata(String name, RoomType type, int secrets, int crypts, boolean prince) {
    }

    private record CellKey(int x, int z) {
        List<CellKey> neighbors() {
            return List.of(
                new CellKey(x + 1, z),
                new CellKey(x - 1, z),
                new CellKey(x, z + 1),
                new CellKey(x, z - 1)
            );
        }
    }
}
