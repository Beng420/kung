package com.github.beng420.kung.feature.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.github.beng420.kung.util.KungChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class DungeonRunStats {
    private static final int ROOM_SIZE = 19;
    private static final int DOOR_SIZE = 6;
    private static final Pattern FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+|\\?)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("(-?\\d+)");
    private static final Pattern CLEARED_PATTERN = Pattern.compile("Cleared:\\s*(\\d+(?:\\.\\d+)?)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOOR_PATTERN = Pattern.compile("\\b(M)?F([1-7])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOOR_NAME_PATTERN = Pattern.compile(
        "\\b(The Professor|Bonzo|Scarf|Thorn|Livid|Sadan|Maxor|Storm|Goldor|Necron)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:(\\d+)m)?\\s*(\\d+)s");
    private static final Pattern SCORE_TEXT_PATTERN = Pattern.compile("\\bScore\\s*:?\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_SECRETS_PATTERN =
        Pattern.compile("\\bSecrets\\s*:?\\s*(\\d+)\\s*/\\s*(\\d+)(?:\\s*\\(Total:\\s*(\\d+)\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRETS_PERCENT_PATTERN = Pattern.compile("\\s*Secrets Found:\\s*(\\d+(?:\\.\\d+)?)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRETS_FRACTION_PATTERN = Pattern.compile("\\bSecrets?:\\s*(\\d+)\\s*/\\s*(\\d+|\\?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_SECRETS_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s+Secrets\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile("\\s*Completed Rooms:\\s*(\\d+)(?:\\s*/\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENED_ROOMS_PATTERN = Pattern.compile("\\s*Opened Rooms:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUZZLE_STATE_PATTERN = Pattern.compile(".+: \\[([✖✦xX])]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRYPTS_TAB_PATTERN = Pattern.compile("\\s*Crypts:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_TAB_PATTERN = Pattern.compile("\\[\\d+] (?:\\[[A-Za-z]+] )?(?<name>[A-Za-z0-9_]{3,16})\\b.*\\((?:Archer|Berserk(?:er)?|Mage|Healer|Tank)\\b.*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("\\b[A-Za-z0-9_]{3,16}\\b");
    private static final Pattern TOTAL_DEATHS_PATTERN = Pattern.compile("\\bDeaths?\\s*:?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_SECRETS_PATTERN =
        Pattern.compile("\\b(?:Secrets?|Secrets Found)\\s*:?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_DEATHS_PATTERN =
        Pattern.compile("\\bDeaths?\\s*:?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELF_DEATH_EVENT_PATTERN = Pattern.compile(
        "^[^A-Za-z0-9_]*(?:You\\s+(?:died|were killed|became a ghost)\\b|You fell\\b|You burned\\b|You drowned\\b)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PLAYER_DEATH_EVENT_PATTERN = Pattern.compile(
        "^[^A-Za-z0-9_]*(?<name>[A-Za-z0-9_]{3,16})\\s+"
            + "(?:died\\b|was killed\\b|was slain\\b|was shot\\b|fell\\b|burned\\b|drowned\\b|blew up\\b|"
            + "suffocated\\b|hit the ground\\b|disconnected\\b|became a ghost\\b)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PLAYER_CLASS_PATTERN =
        Pattern.compile("\\b(Archer|Berserk(?:er)?|Mage|Healer|Tank)\\b", Pattern.CASE_INSENSITIVE);
    private static Field tabHeaderField;
    private static Field tabFooterField;

    private final Map<UUID, PlayerStats> players = new HashMap<>();
    private final Map<String, UUID> playerNames = new HashMap<>();
    private final Set<String> dungeonPlayerNames = new HashSet<>();
    private final List<UUID> dungeonPlayerOrder = new ArrayList<>();
    private final Map<UUID, RoomKey> playerRooms = new HashMap<>();
    private final Map<RoomKey, UUID> lastPlayerInRoom = new HashMap<>();
    private final Set<String> countedClearedRooms = new HashSet<>();
    private final Map<RoomKey, Integer> roomSecretsFound = new HashMap<>();
    private final Map<RoomKey, Long> lastRoomPresenceTick = new HashMap<>();
    private final Set<String> debugClearedRooms = new HashSet<>();
    private final Set<String> debugCompletedRooms = new HashSet<>();
    private int secretsFound;
    private int secretsAvailable = -1;
    private int secretsTotalAvailable = -1;
    private double secretsPercent = -1.0;
    private double clearedPercent = -1.0;
    private int completedRooms;
    private int openedRooms;
    private int totalRoomsFromTab;
    private int deaths;
    private int failedPuzzles;
    private int cryptsOpened;
    private int cryptsAvailable = -1;
    private int serverScore = -1;
    private int estimatedScore = -1;
    private int floor;
    private boolean masterMode;
    private long runStartTick;
    private long elapsedSeconds = -1L;
    private boolean mimicKilled;
    private boolean princeKilled;
    private boolean bloodRoomCompleted;
    private boolean sendingRunSummary;
    private String lastCountedSecretMessage = "";
    private long lastCountedSecretTick = Long.MIN_VALUE;
    private String lastCountedDeathMessage = "";
    private long lastCountedDeathTick = Long.MIN_VALUE;
    private long currentObserveTick;

    public void reset() {
        players.clear();
        playerNames.clear();
        dungeonPlayerNames.clear();
        dungeonPlayerOrder.clear();
        playerRooms.clear();
        lastPlayerInRoom.clear();
        countedClearedRooms.clear();
        roomSecretsFound.clear();
        lastRoomPresenceTick.clear();
        debugClearedRooms.clear();
        debugCompletedRooms.clear();
        secretsFound = 0;
        secretsAvailable = -1;
        secretsTotalAvailable = -1;
        secretsPercent = -1.0;
        clearedPercent = -1.0;
        completedRooms = 0;
        openedRooms = 0;
        totalRoomsFromTab = 0;
        deaths = 0;
        failedPuzzles = 0;
        cryptsOpened = 0;
        cryptsAvailable = -1;
        serverScore = -1;
        estimatedScore = -1;
        floor = 0;
        masterMode = false;
        runStartTick = 0L;
        elapsedSeconds = -1L;
        mimicKilled = false;
        princeKilled = false;
        bloodRoomCompleted = false;
        sendingRunSummary = false;
        lastCountedSecretMessage = "";
        lastCountedSecretTick = Long.MIN_VALUE;
        lastCountedDeathMessage = "";
        lastCountedDeathTick = Long.MIN_VALUE;
    }

    public void startRun(long nowTick) {
        runStartTick = nowTick;
        elapsedSeconds = 0L;
    }

    public void stopRun(long nowTick) {
        if (runStartTick > 0L) {
            elapsedSeconds = Math.max(elapsedSeconds, ticksToSeconds(nowTick - runStartTick));
        }
    }

    public void observePlayers(Minecraft client, long nowTick) {
        if (client.level == null || client.player == null) {
            return;
        }

        observeScoreboard(client);
        observeTabList(client);
        registerPlayerName(client.player.getName().getString(), client.player.getUUID());
        currentObserveTick = nowTick;
        observePlayerRoom(client.player.getUUID(), client.player.blockPosition(), nowTick);
        for (UUID uuid : knownDungeonPlayerUuids()) {
            if (uuid.equals(client.player.getUUID())) {
                continue;
            }
            AbstractClientPlayer player = playerByUuid(client, uuid);
            if (player != null) {
                observePlayer(client, player, nowTick);
            }
        }
        if (runStartTick > 0L) {
            elapsedSeconds = Math.max(0L, ticksToSeconds(nowTick - runStartTick));
        }
        updateEstimatedScore();
    }

    public int secretsFound() {
        return secretsFound;
    }

    public int secretsAvailable() {
        return secretsAvailable;
    }

    public int secretsTotalAvailable() {
        return secretsTotalAvailable;
    }

    public double secretsPercent() {
        return secretsPercent;
    }

    public double clearedPercent() {
        return clearedPercent;
    }

    public int deaths() {
        return deaths;
    }

    public int cryptsOpened() {
        return cryptsOpened;
    }

    public int cryptsAvailable() {
        return cryptsAvailable;
    }

    public int score() {
        return serverScore >= 0 ? serverScore : estimatedScore;
    }

    public int score(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        int scoreEstimate = estimatedScore(renderPlan, estimatedSecretsAvailable);
        if (serverScore < 0) {
            return scoreEstimate;
        }
        return serverScore;
    }

    public int estimatedScore() {
        return estimatedScore;
    }

    public int sPlusSecretTarget(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        int totalSecrets = bestSecretsAvailable(estimatedSecretsAvailable);
        if (totalSecrets <= 0) {
            return -1;
        }

        int scoreWithoutSecrets = projectedSPlusSkillScore() + 60 + speedScore() + projectedSPlusBonusScore();
        int neededSecretScore = Math.clamp(300 - scoreWithoutSecrets, 0, 40);
        if (neededSecretScore <= 0) {
            return displayedSecretsFound(totalSecrets);
        }

        int requiredSecrets = (int) Math.ceil(
            neededSecretScore * requiredSecretsPercent() * totalSecrets / 4000.0
        );
        return Math.clamp(requiredSecrets, 0, totalSecrets);
    }

    public int sPlusSecretsRemaining(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        int target = sPlusSecretTarget(renderPlan, estimatedSecretsAvailable);
        if (target < 0) {
            return -1;
        }
        return Math.max(0, target - displayedSecretsFound(bestSecretsAvailable(estimatedSecretsAvailable)));
    }

    public boolean mimicKilled() {
        return mimicKilled;
    }

    public boolean princeKilled() {
        return princeKilled;
    }

    public int floor() {
        return floor;
    }

    public boolean masterMode() {
        return masterMode;
    }

    public Map<UUID, PlayerStats> players() {
        return Map.copyOf(players);
    }

    public PlayerStats playerStats(UUID uuid) {
        return players.get(uuid);
    }

    public void observeClearStates(Minecraft client, DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        if (renderPlan == null) {
            return;
        }

        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            boolean cleared = false;
            boolean completed = false;
            for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                cleared = cleared || renderPlan.isClearedRoom(component.roomGridX(), component.roomGridZ());
                completed = completed || renderPlan.isCompletedRoom(component.roomGridX(), component.roomGridZ());
            }
            if (cleared) {
                String roomKey = roomKeyFor(match);
                recordClearedRoom(roomKey, playerForClearedRoom(match.components()));
                sendRoomDebugOnce(client, debugClearedRooms, roomKey, "Raum gecleart: " + match.template().name());
            }
            if (completed) {
                String roomKey = roomKeyFor(match);
                recordMatchedRoomSecretCount(client, match, match.template().secrets(), match.template().secrets(), false);
                sendRoomDebugOnce(client, debugCompletedRooms, roomKey, "Raum completed: " + match.template().name());
            }
        }

        for (int roomGridZ = 0; roomGridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridZ++) {
            for (int roomGridX = 0; roomGridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2; roomGridX++) {
                if (!renderPlan.isClearedRoom(roomGridX, roomGridZ)
                    || renderPlan.isMatchedRoomCell(roomGridX, roomGridZ)) {
                    continue;
                }

                RoomKey room = new RoomKey(roomGridX, roomGridZ);
                String roomKey = "cell:" + roomGridX + "," + roomGridZ;
                recordClearedRoom(roomKey, lastPlayerInRoom.get(room));
                sendRoomDebugOnce(client, debugClearedRooms, roomKey, "Raum gecleart: " + roomNameFor(room, null));
            }
        }
    }

    public void observeRoomSecretOverlay(
        Minecraft client,
        String rawMessage,
        DungeonLiveMapWriter.MatchRenderPlan renderPlan
    ) {
        if (client == null || client.player == null || rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String plainMessage = rawMessage.replaceAll("§.", "");
        Matcher matcher = ROOM_SECRETS_PATTERN.matcher(plainMessage);
        if (!matcher.find()) {
            return;
        }

        int found = Integer.parseInt(matcher.group(1));
        int max = Integer.parseInt(matcher.group(2));
        if (max <= 0 || found < 0 || found > max) {
            return;
        }

        RoomKey currentRoom = currentPlayerRoom(client);
        if (currentRoom == null) {
            return;
        }

        DungeonKnownRoomCatalog.MatchedRoom match = matchedRoomAt(renderPlan, currentRoom);
        if (match != null) {
            recordMatchedRoomSecretCount(client, match, found, max, true);
            return;
        }

        int previous = roomSecretsFound.getOrDefault(currentRoom, 0);
        int next = Math.max(previous, found);
        roomSecretsFound.put(currentRoom, next);
        if (next > previous) {
            debug(client, "Secret in " + roomNameFor(currentRoom, renderPlan) + ": " + next + "/" + max);
        }
    }

    public void sendRunSummary(Minecraft client, DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        if (client.player == null) {
            return;
        }

        UUID selfUuid = client.player.getUUID();
        List<PlayerStats> sortedPlayers = new ArrayList<>(players.values().stream()
            .filter(stats -> isSummaryPlayer(stats, selfUuid))
            .toList());
        if (sortedPlayers.isEmpty()) {
            return;
        }
        sortedPlayers.sort(Comparator
            .comparing((PlayerStats stats) -> !stats.uuid().equals(selfUuid))
            .thenComparing(PlayerStats::name, String.CASE_INSENSITIVE_ORDER));

        sendingRunSummary = true;
        try {
            client.player.sendSystemMessage(KungChat.message("Run Stats"));
            client.player.sendSystemMessage(Component.literal(roomProgressSummary(renderPlan)));
            client.player.sendSystemMessage(Component.literal(
                "Score " + score(renderPlan, 0)
                    + " | Secrets " + displayedSecretsFound(bestSecretsAvailable(0)) + "/" + unknownPositive(bestSecretsAvailable(0))
                    + " | Crypts " + cryptsOpened + "/" + unknownDash(cryptsAvailable)
            ));
            if (sortedPlayers.isEmpty()) {
                return;
            }
            for (PlayerStats stats : sortedPlayers) {
                client.player.sendSystemMessage(Component.literal(
                    stats.name()
                        + ": Attributed Rooms " + stats.roomsCleared()
                        + " | Secrets " + stats.secretsFound()
                        + " | Deaths " + stats.deaths()
                ));
            }
        } finally {
            sendingRunSummary = false;
        }
    }

    public int roomSecretsFound(int roomGridX, int roomGridZ) {
        return roomSecretsFound.getOrDefault(new RoomKey(roomGridX, roomGridZ), 0);
    }

    public void observeMapPlayerRoom(int roomGridX, int roomGridZ, long nowTick) {
        if (!isValidRoomGrid(roomGridX, roomGridZ)) {
            return;
        }
        lastRoomPresenceTick.put(new RoomKey(roomGridX, roomGridZ), nowTick);
    }

    public void observeMapPlayerRoom(String name, int roomGridX, int roomGridZ, long nowTick) {
        observeMapPlayerRoom(roomGridX, roomGridZ, nowTick);
        if (name == null || name.isBlank() || !isKnownDungeonPlayer(name)) {
            return;
        }

        UUID uuid = registerDungeonPlayerName(name);
        if (uuid == null) {
            return;
        }

        RoomKey room = new RoomKey(roomGridX, roomGridZ);
        playerRooms.put(uuid, room);
        lastPlayerInRoom.put(room, uuid);
        PlayerStats stats = playerStats(uuid, name);
        stats.roomGridX = roomGridX;
        stats.roomGridZ = roomGridZ;
        stats.lastSeenTick = nowTick;
    }

    public boolean isKnownDungeonPlayer(String name) {
        return dungeonPlayerNames.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    public int dungeonPlayerCount() {
        return dungeonPlayerNames.size();
    }

    public Set<UUID> knownDungeonPlayerUuids() {
        Set<UUID> uuids = new HashSet<>();
        for (String name : dungeonPlayerNames) {
            UUID uuid = playerNames.get(name);
            if (uuid != null) {
                uuids.add(uuid);
            }
        }
        return Set.copyOf(uuids);
    }

    public UUID dungeonPlayerUuid(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return playerNames.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public List<PlayerStats> dungeonPlayersInOrder() {
        return dungeonPlayerOrder.stream()
            .map(players::get)
            .filter(stats -> stats != null && isKnownDungeonPlayer(stats.name()))
            .toList();
    }

    public void observeMessage(Minecraft client, String rawMessage) {
        observeMessage(client, rawMessage, Long.MIN_VALUE);
    }

    public void observeMessage(Minecraft client, String rawMessage, long nowTick) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        if (sendingRunSummary) {
            return;
        }

        String message = rawMessage.replaceAll("\\s+", " ").trim();
        if (isGeneratedRunSummaryLine(message)) {
            return;
        }

        String lower = message.toLowerCase(java.util.Locale.ROOT);
        observeStatLine(client, message);
        if (lower.contains("secret")) {
            int increment = secretIncrement(lower);
            if (increment > 0 && countSecretMessageOnce(message, nowTick)) {
                recordSecret(client, playerFromMessage(client, message), increment);
            }
        }
        if (lower.contains("crypt")) {
            if (lower.contains("opened") || lower.contains("destroyed") || lower.contains("blown") || lower.contains("crypts")) {
                cryptsOpened++;
            }
        }
        UUID deathUuid = deathEventPlayer(client, message);
        if (deathUuid != null && countDeathMessageOnce(message, nowTick)) {
            deaths++;
            PlayerStats stats = playerStats(deathUuid, playerName(deathUuid));
            stats.incrementDeaths(1);
            sendDeathMessage(client, stats);
        }
        if (lower.contains("failed") && lower.contains("puzzle")) {
            failedPuzzles++;
        }
        if (lower.contains("mimic") && (lower.contains("dead") || lower.contains("killed") || lower.contains("defeated"))) {
            mimicKilled = true;
        }
        if (lower.contains("prince") && (lower.contains("dead") || lower.contains("killed") || lower.contains("defeated"))) {
            princeKilled = true;
        }
        if (lower.contains("[boss] the watcher: you have proven yourself")
            || lower.contains("blood clear")
            || lower.contains("blood room completed")) {
            bloodRoomCompleted = true;
        }
        updateEstimatedScore();
    }

    private void observePlayer(Minecraft client, AbstractClientPlayer player, long nowTick) {
        boolean self = player == client.player || player.getUUID().equals(client.player.getUUID());
        if (!self) {
            if (client.getConnection() == null || client.getConnection().getPlayerInfo(player.getUUID()) == null) {
                return;
            }
            if (!isPlayerName(player.getName().getString())) {
                return;
            }
            if (!isKnownDungeonPlayer(player.getName().getString())) {
                return;
            }
        }

        registerPlayerName(player.getName().getString(), player.getUUID());
        observePlayerRoom(player.getUUID(), player.blockPosition(), nowTick);
    }

    private void observeMapPlayerRooms(Minecraft client, long nowTick) {
        MapItemSavedData mapData = DungeonMapItems.mapData(client);
        if (mapData == null) {
            return;
        }

        for (MapDecoration decoration : mapData.getDecorations()) {
            if (!isKnownDungeonPlayerDecoration(client, decoration)) {
                continue;
            }
            RoomKey room = roomFromMapDecoration(decoration);
            if (room != null) {
                lastRoomPresenceTick.put(room, nowTick);
            }
        }
    }

    private static boolean isPlayerDecoration(MapDecoration decoration) {
        return decoration.type().equals(MapDecorationTypes.PLAYER)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_MAP)
            || decoration.type().equals(MapDecorationTypes.PLAYER_OFF_LIMITS)
            || decoration.type().equals(MapDecorationTypes.BLUE_MARKER)
            || decoration.type().equals(MapDecorationTypes.FRAME);
    }

    private boolean isKnownDungeonPlayerDecoration(Minecraft client, MapDecoration decoration) {
        if (!isPlayerDecoration(decoration) || client.player == null) {
            return false;
        }
        String name = decorationName(decoration);
        if (name.isEmpty()) {
            return true;
        }
        return !name.isEmpty()
            && (name.equalsIgnoreCase(client.player.getName().getString()) || isKnownDungeonPlayer(name));
    }

    private static String decorationName(MapDecoration decoration) {
        return decoration.name()
            .map(component -> component.getString().replaceAll("\u00a7.", "").trim())
            .orElse("");
    }

    private static RoomKey roomFromMapDecoration(MapDecoration decoration) {
        int mapPixelX = Math.round((((decoration.x() >> 1) + 64) / 128.0F) * mapGridPixelSize());
        int mapPixelZ = Math.round((((decoration.y() >> 1) + 64) / 128.0F) * mapGridPixelSize());
        int roomGridX = Math.round((mapPixelX - ROOM_SIZE / 2.0F) / pixelsPerRoom());
        int roomGridZ = Math.round((mapPixelZ - ROOM_SIZE / 2.0F) / pixelsPerRoom());
        if (!isValidRoomGrid(roomGridX, roomGridZ)) {
            return null;
        }
        return new RoomKey(roomGridX, roomGridZ);
    }

    private static int mapGridPixelSize() {
        int pixel = 0;
        for (int index = 0; index < DungeonScanUtils.SCAN_GRID_SIZE; index++) {
            pixel += (index & 1) == 0 ? ROOM_SIZE : DOOR_SIZE;
            pixel += 1;
        }
        return pixel;
    }

    private static int pixelsPerRoom() {
        return ROOM_SIZE + DOOR_SIZE + 2;
    }

    private void clearPositionTrackingData() {
        playerRooms.clear();
        lastPlayerInRoom.clear();
        countedClearedRooms.clear();
        lastRoomPresenceTick.clear();
    }

    private void observeScoreboard(Minecraft client) {
        for (String line : DungeonSidebarReader.lines(client)) {
            observeScoreboardLine(client, line);
        }
    }

    private void observeScoreboardLine(Minecraft client, String line) {
        if (line == null || line.isBlank()) {
            return;
        }

        observeStatLine(client, line);
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("secret")) {
            Matcher matcher = FRACTION_PATTERN.matcher(line);
            if (matcher.find()) {
                observeSecretsTotal(client, Integer.parseInt(matcher.group(1)));
                if (!matcher.group(2).equals("?")) {
                    observeServerSecretsAvailable(Integer.parseInt(matcher.group(2)));
                }
            }
        } else if (lower.contains("death")) {
            int value = firstInteger(line);
            if (value >= 0) {
                deaths = Math.max(deaths, value);
            }
        } else if (lower.contains("crypt")) {
            Matcher matcher = FRACTION_PATTERN.matcher(line);
            if (matcher.find()) {
                cryptsOpened = Math.max(cryptsOpened, Integer.parseInt(matcher.group(1)));
                if (!matcher.group(2).equals("?")) {
                    cryptsAvailable = Math.max(cryptsAvailable, Integer.parseInt(matcher.group(2)));
                }
            } else {
                int value = firstInteger(line);
                if (value >= 0) {
                    cryptsOpened = Math.max(cryptsOpened, value);
                }
            }
        } else if (lower.contains("score")) {
            int value = firstInteger(line);
            if (value >= 0) {
                serverScore = value;
            }
        } else if (lower.contains("clear")) {
            Matcher matcher = CLEARED_PATTERN.matcher(line);
            if (matcher.find()) {
                clearedPercent = Math.max(clearedPercent, Double.parseDouble(matcher.group(1)));
            }
        } else if (lower.contains("mimic")) {
            mimicKilled = lower.contains("yes") || lower.contains("dead") || lower.contains("done") || lower.contains("killed");
        }

        Matcher floorMatcher = FLOOR_PATTERN.matcher(line);
        if (floorMatcher.find()) {
            masterMode = floorMatcher.group(1) != null;
            floor = Integer.parseInt(floorMatcher.group(2));
        }
        Matcher floorNameMatcher = FLOOR_NAME_PATTERN.matcher(line);
        if (floorNameMatcher.find()) {
            floor = floorForBossName(floorNameMatcher.group(1));
        }
        if (lower.contains("time")) {
            long parsedElapsedSeconds = parseElapsedSeconds(line);
            if (parsedElapsedSeconds >= 0L) {
                elapsedSeconds = parsedElapsedSeconds;
            }
        }
    }

    private void observeTabList(Minecraft client) {
        if (client.getConnection() == null) {
            return;
        }

        failedPuzzles = 0;
        dungeonPlayerNames.clear();
        dungeonPlayerOrder.clear();
        if (client.player != null) {
            registerDungeonPlayerName(client.player.getName().getString(), client.player.getUUID());
        }
        observeTabOverlayText(client);
        for (net.minecraft.client.multiplayer.PlayerInfo info : client.getConnection().getOnlinePlayers()) {
            String profileName = info.getProfile().name();
            String line = info.getTabListDisplayName() != null
                ? info.getTabListDisplayName().getString()
                : profileName;
            observeTabLine(client, line, info.getProfile().id());
        }
    }

    private void observeTabLine(String line) {
        observeTabLine(null, line, null);
    }

    private void observeTabLine(Minecraft client, String line, UUID uuid) {
        observeStatLine(client, line);
        Matcher secretsMatcher = SECRETS_PERCENT_PATTERN.matcher(line);
        if (secretsMatcher.find()) {
            secretsPercent = Double.parseDouble(secretsMatcher.group(1));
            return;
        }

        Matcher completedMatcher = COMPLETED_ROOMS_PATTERN.matcher(line);
        if (completedMatcher.find()) {
            completedRooms = Integer.parseInt(completedMatcher.group(1));
            if (completedMatcher.group(2) != null) {
                totalRoomsFromTab = Integer.parseInt(completedMatcher.group(2));
            }
            return;
        }

        Matcher openedMatcher = OPENED_ROOMS_PATTERN.matcher(line);
        if (openedMatcher.find()) {
            openedRooms = Integer.parseInt(openedMatcher.group(1));
            return;
        }

        Matcher puzzleMatcher = PUZZLE_STATE_PATTERN.matcher(line);
        if (puzzleMatcher.find()) {
            failedPuzzles++;
            return;
        }

        Matcher cryptsMatcher = CRYPTS_TAB_PATTERN.matcher(line);
        if (cryptsMatcher.find()) {
            cryptsOpened = Integer.parseInt(cryptsMatcher.group(1));
            return;
        }

        Matcher playerMatcher = PLAYER_TAB_PATTERN.matcher(line);
        if (playerMatcher.find()) {
            UUID playerUuid = registerDungeonPlayerName(playerMatcher.group("name"), uuid);
            observePlayerStatLine(line, playerUuid);
            return;
        }

        observeKnownPlayerStatLine(line);
    }

    private void observeTabOverlayText(Minecraft client) {
        PlayerTabOverlay tabList = client.gui.hud.getTabList();
        observeComponentLines(client, readTabComponent(tabList, true));
        observeComponentLines(client, readTabComponent(tabList, false));
    }

    private void observeComponentLines(Minecraft client, Component component) {
        if (component == null) {
            return;
        }

        for (String line : component.getString().split("\\R")) {
            if (!line.isBlank()) {
                observeTabLine(client, line.replaceAll("\\s+", " ").trim(), null);
            }
        }
    }

    private static Component readTabComponent(PlayerTabOverlay tabList, boolean header) {
        try {
            Field field = header ? tabHeaderField : tabFooterField;
            if (field == null) {
                field = PlayerTabOverlay.class.getDeclaredField(header ? "header" : "footer");
                field.setAccessible(true);
                if (header) {
                    tabHeaderField = field;
                } else {
                    tabFooterField = field;
                }
            }
            Object value = field.get(tabList);
            return value instanceof Component component ? component : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private void observeStatLine(Minecraft client, String line) {
        Matcher scoreMatcher = SCORE_TEXT_PATTERN.matcher(line);
        if (scoreMatcher.find()) {
            serverScore = Integer.parseInt(scoreMatcher.group(1));
        }

        Matcher serverSecretsMatcher = SERVER_SECRETS_PATTERN.matcher(line);
        if (serverSecretsMatcher.find()) {
            observeSecretsTotal(client, Integer.parseInt(serverSecretsMatcher.group(1)));
            observeServerSecretsAvailable(Integer.parseInt(serverSecretsMatcher.group(2)));
            if (serverSecretsMatcher.group(3) != null) {
                secretsTotalAvailable = Integer.parseInt(serverSecretsMatcher.group(3));
            }
            return;
        }

        Matcher secretsFractionMatcher = SECRETS_FRACTION_PATTERN.matcher(line);
        if (secretsFractionMatcher.find()) {
            observeSecretsTotal(client, Integer.parseInt(secretsFractionMatcher.group(1)));
            if (!secretsFractionMatcher.group(2).equals("?")) {
                observeServerSecretsAvailable(Integer.parseInt(secretsFractionMatcher.group(2)));
            }
        }
    }

    private void observeServerSecretsAvailable(int observedAvailable) {
        if (observedAvailable < 0) {
            return;
        }
        secretsAvailable = observedAvailable;
        if (secretsTotalAvailable > 0 && secretsTotalAvailable != observedAvailable) {
            secretsTotalAvailable = -1;
        }
    }

    private void observeSecretsTotal(Minecraft client, int observedTotal) {
        if (observedTotal < 0) {
            return;
        }

        int previousTotal = secretsFound;
        if (observedTotal <= previousTotal) {
            secretsFound = Math.max(secretsFound, observedTotal);
            return;
        }

        secretsFound = observedTotal;
        int delta = observedTotal - previousTotal;
        if (delta <= 5) {
            recordUnattributedSecrets(client, delta);
        }
    }

    private void recordSecret(Minecraft client, UUID playerUuid, int count) {
        secretsFound += count;
        if (playerUuid != null) {
            playerStats(playerUuid, playerName(playerUuid)).incrementSecrets(count);
        }

        if (!DungeonMapOverlayConfig.INSTANCE.playerTrackingEnabled()) {
            if (client.player != null && client.player.getUUID().equals(playerUuid)) {
                DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
                roomSecretsFound.merge(new RoomKey(grid.gridX(), grid.gridZ()), count, Integer::sum);
            }
            return;
        }

        AbstractClientPlayer player = playerByUuid(client, playerUuid);
        if (player == null && client.player != null && client.player.getUUID().equals(playerUuid)) {
            player = client.player;
        }
        if (player == null) {
            return;
        }

        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(player.blockPosition());
        RoomKey roomKey = new RoomKey(grid.gridX(), grid.gridZ());
        roomSecretsFound.merge(roomKey, count, Integer::sum);
    }

    private void recordUnattributedSecrets(Minecraft client, int count) {
        if (count <= 0) {
            return;
        }

        SecretTarget target = bestSecretTarget(client);
        if (target == null) {
            return;
        }

        roomSecretsFound.merge(target.room(), count, Integer::sum);
    }

    private void recordMatchedRoomSecretCount(
        Minecraft client,
        DungeonKnownRoomCatalog.MatchedRoom match,
        int found,
        int fallbackMax,
        boolean debugSecret
    ) {
        int max = match.template().secrets() > 0 ? match.template().secrets() : fallbackMax;
        int next = Math.clamp(found, 0, Math.max(0, max));
        int previous = secretsFoundFor(match);
        RoomKey targetRoom = firstComponentRoom(match);
        if (targetRoom == null || next < previous) {
            return;
        }

        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            roomSecretsFound.remove(new RoomKey(component.roomGridX(), component.roomGridZ()));
        }
        roomSecretsFound.put(targetRoom, next);

        if (debugSecret && next > previous) {
            debug(client, "Secret in " + match.template().name() + ": " + next + "/" + max);
        }
    }

    private int secretsFoundFor(DungeonKnownRoomCatalog.MatchedRoom match) {
        int total = 0;
        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            total += roomSecretsFound.getOrDefault(new RoomKey(component.roomGridX(), component.roomGridZ()), 0);
        }
        return Math.min(total, Math.max(0, match.template().secrets()));
    }

    private static RoomKey firstComponentRoom(DungeonKnownRoomCatalog.MatchedRoom match) {
        if (match.components().isEmpty()) {
            return null;
        }

        DungeonKnownRoomCatalog.MatchedComponent component = match.components().get(0);
        return new RoomKey(component.roomGridX(), component.roomGridZ());
    }

    private static DungeonKnownRoomCatalog.MatchedRoom matchedRoomAt(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        RoomKey room
    ) {
        if (renderPlan == null) {
            return null;
        }

        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                if (component.roomGridX() == room.roomGridX() && component.roomGridZ() == room.roomGridZ()) {
                    return match;
                }
            }
        }
        return null;
    }

    private static RoomKey currentPlayerRoom(Minecraft client) {
        if (client == null || client.player == null) {
            return null;
        }

        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        if (!isValidRoomGrid(grid.gridX(), grid.gridZ())) {
            return null;
        }
        return new RoomKey(grid.gridX(), grid.gridZ());
    }

    private static void sendRoomDebugOnce(
        Minecraft client,
        Set<String> seenRooms,
        String roomKey,
        String message
    ) {
        if (seenRooms.add(roomKey)) {
            debug(client, message);
        }
    }

    private static String roomNameFor(RoomKey room, DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        DungeonKnownRoomCatalog.MatchedRoom match = matchedRoomAt(renderPlan, room);
        if (match != null) {
            return match.template().name();
        }
        return "room " + room.roomGridX() + "," + room.roomGridZ();
    }

    private static void debug(Minecraft client, String message) {
        if (DungeonMapOverlayConfig.INSTANCE.debugMessages() && client != null && client.player != null) {
            client.player.sendSystemMessage(KungChat.message("Debug", message));
        }
    }

    private SecretTarget bestSecretTarget(Minecraft client) {
        UUID selfUuid = client != null && client.player != null ? client.player.getUUID() : null;
        if (selfUuid != null) {
            RoomKey selfRoom = playerRooms.get(selfUuid);
            if (selfRoom != null) {
                return new SecretTarget(selfRoom, selfUuid);
            }
        }

        SecretTarget newest = null;
        long newestTick = Long.MIN_VALUE;
        for (Map.Entry<UUID, RoomKey> entry : playerRooms.entrySet()) {
            PlayerStats stats = players.get(entry.getKey());
            long tick = stats == null ? Long.MIN_VALUE : stats.lastSeenTick();
            if (tick > newestTick) {
                newest = new SecretTarget(entry.getValue(), entry.getKey());
                newestTick = tick;
            }
        }
        for (Map.Entry<RoomKey, Long> entry : lastRoomPresenceTick.entrySet()) {
            if (entry.getValue() > newestTick) {
                newest = new SecretTarget(entry.getKey(), null);
                newestTick = entry.getValue();
            }
        }
        return newest;
    }

    private void observeKnownPlayerStatLine(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        for (Map.Entry<String, UUID> entry : playerNames.entrySet()) {
            if (containsPlayerName(lower, entry.getKey())) {
                observePlayerStatLine(line, entry.getValue());
                return;
            }
        }
    }

    private void observePlayerStatLine(String line, UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        Matcher secretsMatcher = PLAYER_SECRETS_PATTERN.matcher(line);
        if (secretsMatcher.find()) {
            playerStats(playerUuid, playerName(playerUuid)).setSecretsFound(Integer.parseInt(secretsMatcher.group(1)));
        }

        Matcher deathsMatcher = PLAYER_DEATHS_PATTERN.matcher(line);
        if (deathsMatcher.find()) {
            playerStats(playerUuid, playerName(playerUuid)).setDeaths(Integer.parseInt(deathsMatcher.group(1)));
        }

        DungeonClass dungeonClass = dungeonClassFromLine(line);
        if (dungeonClass != DungeonClass.UNKNOWN) {
            playerStats(playerUuid, playerName(playerUuid)).setDungeonClass(dungeonClass);
        }
    }

    private static DungeonClass dungeonClassFromLine(String line) {
        Matcher matcher = PLAYER_CLASS_PATTERN.matcher(line);
        if (!matcher.find()) {
            return DungeonClass.UNKNOWN;
        }
        return switch (matcher.group(1).toLowerCase(java.util.Locale.ROOT)) {
            case "archer" -> DungeonClass.ARCHER;
            case "berserk", "berserker" -> DungeonClass.BERSERKER;
            case "mage" -> DungeonClass.MAGE;
            case "healer" -> DungeonClass.HEALER;
            case "tank" -> DungeonClass.TANK;
            default -> DungeonClass.UNKNOWN;
        };
    }

    private UUID playerFromMessage(Minecraft client, String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (client.player != null && isSelfMessage(lower)) {
            return client.player.getUUID();
        }

        for (Map.Entry<String, UUID> entry : playerNames.entrySet()) {
            if (containsPlayerName(lower, entry.getKey())) {
                return entry.getValue();
            }
        }

        Matcher matcher = PLAYER_NAME_PATTERN.matcher(message);
        while (matcher.find()) {
            String name = matcher.group();
            UUID uuid = playerNames.get(name.toLowerCase(java.util.Locale.ROOT));
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private static boolean isSelfMessage(String lowerMessage) {
        return lowerMessage.startsWith("you ")
            || lowerMessage.contains(" you ")
            || lowerMessage.startsWith("your ")
            || lowerMessage.contains(" your ");
    }

    private UUID registerDungeonPlayerName(String name) {
        return registerDungeonPlayerName(name, null);
    }

    private UUID registerDungeonPlayerName(String name, UUID uuid) {
        if (!isPlayerName(name)) {
            return null;
        }

        dungeonPlayerNames.add(name.toLowerCase(java.util.Locale.ROOT));
        UUID playerUuid = uuid;
        if (playerUuid == null) {
            playerUuid = playerNames.get(name.toLowerCase(java.util.Locale.ROOT));
        }
        if (playerUuid == null) {
            playerUuid = syntheticUuid(name);
        }
        registerPlayerName(name, playerUuid);
        rememberDungeonPlayerOrder(playerUuid);
        return playerUuid;
    }

    private void registerPlayerName(String name, UUID uuid) {
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        UUID previousUuid = playerNames.put(lowerName, uuid);
        PlayerStats stats = playerStats(uuid, name);
        stats.setName(name);
        if (previousUuid != null && !previousUuid.equals(uuid)) {
            PlayerStats previousStats = players.remove(previousUuid);
            if (previousStats != null) {
                stats.merge(previousStats);
            }
            replaceDungeonPlayerOrder(previousUuid, uuid);
            RoomKey previousRoom = playerRooms.remove(previousUuid);
            if (previousRoom != null) {
                playerRooms.put(uuid, previousRoom);
                lastPlayerInRoom.put(previousRoom, uuid);
            }
        }
    }

    private void rememberDungeonPlayerOrder(UUID uuid) {
        if (uuid != null && !dungeonPlayerOrder.contains(uuid)) {
            dungeonPlayerOrder.add(uuid);
        }
    }

    private void replaceDungeonPlayerOrder(UUID previousUuid, UUID uuid) {
        for (int index = 0; index < dungeonPlayerOrder.size(); index++) {
            if (dungeonPlayerOrder.get(index).equals(previousUuid)) {
                dungeonPlayerOrder.set(index, uuid);
            }
        }
        for (int index = dungeonPlayerOrder.size() - 1; index >= 0; index--) {
            if (dungeonPlayerOrder.get(index).equals(uuid) && dungeonPlayerOrder.indexOf(uuid) != index) {
                dungeonPlayerOrder.remove(index);
            }
        }
    }

    private void observePlayerRoom(UUID uuid, net.minecraft.core.BlockPos position, long nowTick) {
        DungeonScanUtils.GridPosition grid = DungeonScanUtils.getRoomGridPosition(position);
        if (!isValidRoomGrid(grid.gridX(), grid.gridZ())) {
            return;
        }

        RoomKey room = new RoomKey(grid.gridX(), grid.gridZ());
        playerRooms.put(uuid, room);
        lastPlayerInRoom.put(room, uuid);
        PlayerStats stats = playerStats(uuid, playerName(uuid));
        stats.roomGridX = grid.gridX();
        stats.roomGridZ = grid.gridZ();
        stats.lastSeenTick = nowTick;
    }

    private UUID playerForClearedRoom(List<DungeonKnownRoomCatalog.MatchedComponent> components) {
        for (DungeonKnownRoomCatalog.MatchedComponent component : components) {
            UUID uuid = lastPlayerInRoom.get(new RoomKey(component.roomGridX(), component.roomGridZ()));
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private void recordClearedRoom(String clearKey, UUID playerUuid) {
        if (playerUuid == null || !countedClearedRooms.add(clearKey)) {
            return;
        }
        if (!isKnownStatsUuid(playerUuid)) {
            return;
        }

        playerStats(playerUuid, playerName(playerUuid)).roomsCleared++;
    }

    private static String roomKeyFor(DungeonKnownRoomCatalog.MatchedRoom match) {
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
        return "match:" + minX + "," + minZ + ":" + maxX + "," + maxZ + ":" + match.components().size();
    }

    private PlayerStats playerStats(UUID uuid, String fallbackName) {
        return players.computeIfAbsent(uuid, ignored -> new PlayerStats(uuid, fallbackName));
    }

    private boolean isSummaryPlayer(PlayerStats stats, UUID selfUuid) {
        return stats != null
            && stats.uuid() != null
            && isPlayerName(stats.name())
            && (stats.uuid().equals(selfUuid) || isKnownDungeonPlayer(stats.name()));
    }

    private boolean isKnownStatsUuid(UUID uuid) {
        PlayerStats stats = players.get(uuid);
        return stats != null && isPlayerName(stats.name()) && isKnownDungeonPlayer(stats.name());
    }

    private String playerName(UUID uuid) {
        PlayerStats stats = players.get(uuid);
        return stats == null ? "Unknown" : stats.name();
    }

    private static UUID syntheticUuid(String name) {
        return UUID.nameUUIDFromBytes(("kung:dungeon-player:" + name.toLowerCase(java.util.Locale.ROOT))
            .getBytes(StandardCharsets.UTF_8));
    }

    private static boolean containsPlayerName(String lowerMessage, String lowerName) {
        int index = lowerMessage.indexOf(lowerName);
        while (index >= 0) {
            int before = index - 1;
            int after = index + lowerName.length();
            boolean leftBoundary = before < 0 || !isNameCharacter(lowerMessage.charAt(before));
            boolean rightBoundary = after >= lowerMessage.length() || !isNameCharacter(lowerMessage.charAt(after));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            index = lowerMessage.indexOf(lowerName, index + 1);
        }
        return false;
    }

    private static boolean isNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
            || (character >= '0' && character <= '9')
            || character == '_';
    }

    private static boolean isPlayerName(String name) {
        return name != null && PLAYER_NAME_PATTERN.matcher(name).matches() && !isIgnoredNpcName(name);
    }

    private static boolean isIgnoredNpcName(String name) {
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        return lowerName.equals("mort")
            || lowerName.equals("tomioka")
            || lowerName.equals("duncan")
            || lowerName.equals("zodd")
            || lowerName.equals("trinity");
    }

    private AbstractClientPlayer playerByUuid(Minecraft client, UUID uuid) {
        if (client.level == null || uuid == null) {
            return null;
        }
        return client.level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer player ? player : null;
    }

    private void updateEstimatedScore() {
        estimatedScore = estimatedScore(null, bestSecretsAvailable(0));
    }

    private int estimatedScore(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        return skillScore(renderPlan)
            + exploreScore(renderPlan, estimatedSecretsAvailable)
            + speedScore()
            + bonusScore();
    }

    private int skillScore() {
        return skillScore(null);
    }

    private int skillScore(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int completedRoomScore = roomProgressScore(renderPlan, 80.0);
        int deathPenalty = deaths * 2;
        int puzzlePenalty = failedPuzzles * 14;
        return 20 + Math.clamp(completedRoomScore - deathPenalty - puzzlePenalty, 0, 80);
    }

    private int projectedSPlusSkillScore() {
        int deathPenalty = deaths * 2;
        int puzzlePenalty = failedPuzzles * 14;
        return 20 + Math.clamp(80 - deathPenalty - puzzlePenalty, 0, 80);
    }

    private int exploreScore() {
        return exploreScore(null, bestSecretsAvailable(0));
    }

    private int exploreScore(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        return Math.clamp(exploreRoomScore(renderPlan) + secretScore(estimatedSecretsAvailable), 0, 100);
    }

    private int exploreRoomScore(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        return roomProgressScore(renderPlan, 60.0);
    }

    private int roomProgressScore(DungeonLiveMapWriter.MatchRenderPlan renderPlan, double maxScore) {
        if (clearedPercent >= 0.0) {
            return Math.clamp((int) Math.floor(maxScore * Math.min(100.0, clearedPercent) / 100.0), 0, (int) maxScore);
        }

        int totalRooms = totalRoomsEstimate(renderPlan);
        if (totalRooms <= 0) {
            return 0;
        }
        return Math.clamp((int) (maxScore * completedRoomsWithBloodOffset(renderPlan) / totalRooms), 0, (int) maxScore);
    }

    private int secretScore() {
        return secretScore(bestSecretsAvailable(0));
    }

    private int secretScore(int estimatedSecretsAvailable) {
        int totalSecrets = bestSecretsAvailable(estimatedSecretsAvailable);
        if (secretsPercent >= 0.0) {
            double required = requiredSecretsPercent();
            return Math.clamp((int) (40.0 * Math.min(required, secretsPercent) / required), 0, 40);
        }
        if (totalSecrets > 0) {
            double requiredSecrets = requiredSecretsPercent() * totalSecrets / 100.0;
            return Math.clamp((int) Math.floor(40.0 * displayedSecretsFound(totalSecrets) / requiredSecrets), 0, 40);
        }
        return 0;
    }

    private int bonusScore() {
        return Math.min(5, cryptsOpened)
            + (mimicKilled || (hasMimic() && secretsPercent >= 100.0) ? 2 : 0)
            + (princeKilled ? 1 : 0);
    }

    private int projectedSPlusBonusScore() {
        int cryptBonus = cryptsAvailable >= 0
            ? Math.min(5, Math.max(cryptsOpened, cryptsAvailable))
            : 5;
        int mimicBonus = hasMimic() || mimicKilled ? 2 : 0;
        return cryptBonus + mimicBonus + (princeKilled ? 1 : 0);
    }

    private int totalRoomsEstimate(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        if (totalRoomsFromTab > 0) {
            return totalRoomsFromTab;
        }
        int completed = bestCompletedRooms(renderPlan);
        if (completed <= 0 || clearedPercent <= 0.0) {
            return renderPlan == null ? 0 : observedRoomCount(renderPlan);
        }
        return (int) Math.floor(completed / (clearedPercent / 100.0) + 0.4);
    }

    private String roomProgressSummary(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int completed = bestCompletedRooms(renderPlan);
        int opened = bestOpenedRooms(renderPlan);
        int total = totalRoomsEstimate(renderPlan);
        String roomRange = opened > completed
            ? completed + "-" + opened
            : Integer.toString(completed);
        String totalText = total > 0 ? Integer.toString(total) : "?";
        String clearText = clearedPercent >= 0.0 ? formatPercent(clearedPercent) + "%" : "?";
        return "Rooms cleared: " + roomRange
            + "/" + totalText
            + " | Opened " + unknownDash(opened)
            + " | Clear " + clearText;
    }

    private int bestCompletedRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int mapCompletedRooms = renderPlan == null ? 0 : renderPlan.completedRooms().size();
        return Math.max(completedRooms, mapCompletedRooms);
    }

    private int bestOpenedRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int mapVisitedRooms = renderPlan == null ? 0 : renderPlan.visitedRooms().size();
        return Math.max(Math.max(openedRooms, mapVisitedRooms), bestCompletedRooms(renderPlan));
    }

    private static String unknownDash(int value) {
        return value >= 0 ? Integer.toString(value) : "?";
    }

    private static String unknownPositive(int value) {
        return value > 0 ? Integer.toString(value) : "?";
    }

    private static String formatPercent(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private int extraCompletedRooms() {
        return bloodRoomCompleted ? 1 : 2;
    }

    private int completedRoomsWithBloodOffset(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int completed = bestCompletedRooms(renderPlan);
        return completed + extraCompletedRooms();
    }

    private double requiredSecretsPercent() {
        if (masterMode) {
            return 100.0;
        }
        return switch (floor) {
            case 1 -> 30.0;
            case 2 -> 40.0;
            case 3 -> 50.0;
            case 4 -> 60.0;
            case 5 -> 70.0;
            case 6 -> 85.0;
            default -> 100.0;
        };
    }

    private int speedScore() {
        long seconds = elapsedSeconds >= 0L
            ? elapsedSeconds
            : runStartTick > 0L ? ticksToSeconds(0L) : 0L;
        long grace = speedGraceSeconds();
        if (grace <= 0L || seconds < grace) {
            return 100;
        }

        double timePastRequirement = ((double) (seconds - grace) / grace) * 100.0;
        if (timePastRequirement < 20.0) {
            return 100 - (int) timePastRequirement / 2;
        }
        if (timePastRequirement < 40.0) {
            return 100 - (int) (10.0 + (timePastRequirement - 20.0) / 4.0);
        }
        if (timePastRequirement < 50.0) {
            return 100 - (int) (15.0 + (timePastRequirement - 40.0) / 5.0);
        }
        if (timePastRequirement < 60.0) {
            return 100 - (int) (17.0 + (timePastRequirement - 50.0) / 6.0);
        }
        return Math.clamp(100 - (int) (18.0 + (2.0 / 3.0) + (timePastRequirement - 60.0) / 7.0), 0, 100);
    }

    private long speedGraceSeconds() {
        if (masterMode) {
            return switch (floor) {
                case 6 -> 10L * 60L;
                case 7 -> 14L * 60L;
                default -> 8L * 60L;
            };
        }
        return switch (floor) {
            case 4, 6 -> 12L * 60L;
            case 7 -> 14L * 60L;
            default -> 10L * 60L;
        };
    }

    private boolean hasMimic() {
        return floor == 6 || floor == 7;
    }

    private int bestSecretsAvailable(int estimatedSecretsAvailable) {
        if (secretsAvailable > 0) {
            return secretsAvailable;
        }
        if (secretsTotalAvailable > 0) {
            return secretsTotalAvailable;
        }
        return Math.max(0, estimatedSecretsAvailable);
    }

    private int displayedSecretsFound(int secretsAvailable) {
        if (secretsAvailable > 0 && secretsPercent >= 0.0) {
            return Math.clamp(
                (int) Math.round(secretsAvailable * secretsPercent / 100.0),
                0,
                secretsAvailable
            );
        }
        if (secretsAvailable > 0) {
            return Math.clamp(secretsFound, 0, secretsAvailable);
        }
        return Math.max(0, secretsFound);
    }

    private static int observedRoomCount(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        Set<RoomKey> rooms = new HashSet<>();
        for (DungeonKnownRoomCatalog.MatchedRoom match : renderPlan.matches()) {
            for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
                rooms.add(new RoomKey(component.roomGridX(), component.roomGridZ()));
            }
        }
        for (DungeonLiveMapWriter.CellKey room : renderPlan.hints().keySet()) {
            rooms.add(new RoomKey(room.x(), room.z()));
        }
        return rooms.size();
    }

    private int secretIncrement(String lowerMessage) {
        if (lowerMessage.contains("secrets found:")
            || lowerMessage.contains("%")
            || lowerMessage.contains("/")) {
            return 0;
        }
        return lowerMessage.contains("found") && lowerMessage.contains("secret") ? 1 : 0;
    }

    private boolean countSecretMessageOnce(String message, long nowTick) {
        if (nowTick != Long.MIN_VALUE
            && nowTick == lastCountedSecretTick
            && message.equals(lastCountedSecretMessage)) {
            return false;
        }
        lastCountedSecretMessage = message;
        lastCountedSecretTick = nowTick;
        return true;
    }

    private boolean countDeathMessageOnce(String message, long nowTick) {
        if (nowTick != Long.MIN_VALUE
            && nowTick == lastCountedDeathTick
            && message.equals(lastCountedDeathMessage)) {
            return false;
        }
        lastCountedDeathMessage = message;
        lastCountedDeathTick = nowTick;
        return true;
    }

    private UUID deathEventPlayer(Minecraft client, String message) {
        if (client.player != null && SELF_DEATH_EVENT_PATTERN.matcher(message).find()) {
            return client.player.getUUID();
        }

        Matcher playerMatcher = PLAYER_DEATH_EVENT_PATTERN.matcher(message);
        if (!playerMatcher.find()) {
            return null;
        }

        String name = playerMatcher.group("name");
        UUID uuid = playerNames.get(name.toLowerCase(java.util.Locale.ROOT));
        if (uuid == null || !isKnownDungeonPlayer(name)) {
            return null;
        }
        return uuid;
    }

    private static boolean isGeneratedRunSummaryLine(String message) {
        return message.startsWith("[Kung")
            || message.contains("Run Stats")
            || message.startsWith("Rooms cleared:")
            || (message.startsWith("Score ") && message.contains(" | Secrets ") && message.contains(" | Crypts "))
            || message.contains(": Attributed Rooms ");
    }

    private static void sendDeathMessage(Minecraft client, PlayerStats stats) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(KungChat.message("Dungeon", stats.name() + " died " + stats.deaths() + " times"));
        }
    }

    private static boolean isValidRoomGrid(int gridX, int gridZ) {
        return gridX >= 0
            && gridZ >= 0
            && gridX <= DungeonScanUtils.SCAN_GRID_SIZE / 2
            && gridZ <= DungeonScanUtils.SCAN_GRID_SIZE / 2;
    }

    private static int firstInteger(String value) {
        Matcher matcher = INTEGER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static int floorForBossName(String bossName) {
        String lower = bossName.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("bonzo")) {
            return 1;
        }
        if (lower.contains("scarf")) {
            return 2;
        }
        if (lower.contains("professor")) {
            return 3;
        }
        if (lower.contains("thorn")) {
            return 4;
        }
        if (lower.contains("livid")) {
            return 5;
        }
        if (lower.contains("sadan")) {
            return 6;
        }
        return 7;
    }

    private static long parseElapsedSeconds(String value) {
        Matcher matcher = TIME_PATTERN.matcher(value.toLowerCase(java.util.Locale.ROOT));
        if (!matcher.find()) {
            return -1L;
        }

        long minutes = matcher.group(1) == null ? 0L : Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        return minutes * 60L + seconds;
    }

    private static long ticksToSeconds(long ticks) {
        return Math.max(0L, ticks / 20L);
    }

    public static final class PlayerStats {
        private final UUID uuid;
        private String name;
        private DungeonClass dungeonClass = DungeonClass.UNKNOWN;
        private int deaths;
        private int roomsCleared;
        private int secretsFound;
        private int roomGridX = -1;
        private int roomGridZ = -1;
        private long lastSeenTick;

        private PlayerStats(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public UUID uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        public DungeonClass dungeonClass() {
            return dungeonClass;
        }

        public int deaths() {
            return deaths;
        }

        public int roomsCleared() {
            return roomsCleared;
        }

        public int secretsFound() {
            return secretsFound;
        }

        public int roomGridX() {
            return roomGridX;
        }

        public int roomGridZ() {
            return roomGridZ;
        }

        public long lastSeenTick() {
            return lastSeenTick;
        }

        private void setName(String name) {
            this.name = name;
        }

        private void setDungeonClass(DungeonClass dungeonClass) {
            this.dungeonClass = dungeonClass;
        }

        private void incrementDeaths(int count) {
            deaths += count;
        }

        private void setDeaths(int deaths) {
            this.deaths = Math.max(this.deaths, deaths);
        }

        private void incrementSecrets(int count) {
            secretsFound += count;
        }

        private void setSecretsFound(int secretsFound) {
            this.secretsFound = Math.max(this.secretsFound, secretsFound);
        }

        private void merge(PlayerStats other) {
            deaths += other.deaths;
            roomsCleared += other.roomsCleared;
            secretsFound += other.secretsFound;
            if (dungeonClass == DungeonClass.UNKNOWN && other.dungeonClass != DungeonClass.UNKNOWN) {
                dungeonClass = other.dungeonClass;
            }
            if (roomGridX < 0 && other.roomGridX >= 0) {
                roomGridX = other.roomGridX;
                roomGridZ = other.roomGridZ;
                lastSeenTick = other.lastSeenTick;
            }
        }
    }

    private record RoomKey(int roomGridX, int roomGridZ) {
    }

    private record SecretTarget(RoomKey room, UUID playerUuid) {
    }

    public enum DungeonClass {
        UNKNOWN,
        ARCHER,
        BERSERKER,
        MAGE,
        HEALER,
        TANK
    }
}
