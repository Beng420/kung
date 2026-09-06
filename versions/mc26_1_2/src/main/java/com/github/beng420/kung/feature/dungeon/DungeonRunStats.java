package com.github.beng420.kung.feature.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.github.beng420.kung.skyblock.HypixelPartyTracker;
import com.github.beng420.kung.skyblock.SkyBlockMayorTracker;
import com.github.beng420.kung.util.HypixelSkyBlockProfileClient;
import com.github.beng420.kung.util.KungChat;
import com.github.beng420.kung.util.KungDebugRecorder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class DungeonRunStats {
    private static final int MAX_DUNGEON_PLAYERS = 5;
    private static final int ROOM_SIZE = 19;
    private static final int DOOR_SIZE = 6;
    private static final int TARGET_CRYPTS = 5;
    private static final long ROOM_CLEAR_PLAYER_STALE_TICKS = 40;
    private static final long RUN_SECRET_FINAL_TIMEOUT_TICKS = 60;
    private static final Pattern FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+|\\?)");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("(-?\\d+)");
    private static final Pattern CLEARED_PATTERN = Pattern.compile("Cleared:\\s*(\\d+(?:\\.\\d+)?)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOOR_PATTERN = Pattern.compile("\\b(?:(M)(?:F)?|F)([1-7])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOOR_NAME_PATTERN = Pattern.compile(
        "\\b(The Professor|Bonzo|Scarf|Thorn|Livid|Sadan|Maxor|Storm|Goldor|Necron)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:(\\d+)m)?\\s*(\\d+)s");
    private static final Pattern SCORE_TEXT_PATTERN = Pattern.compile("^\\s*Score\\s*:?\\s*(\\d{1,3})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM_SCORE_TEXT_PATTERN =
        Pattern.compile("^\\s*Team\\s+Score\\s*:?\\s*(\\d{1,3})(?:\\s*\\([A-Z+]+\\))?(?:\\s*\\(NEW RECORD!\\))?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_SECRETS_PATTERN =
        Pattern.compile("\\bSecrets\\s*:?\\s*(\\d+)\\s*/\\s*(\\d+)(?:\\s*\\(Total:\\s*(\\d+)\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRETS_PERCENT_PATTERN = Pattern.compile("\\s*Secrets Found:\\s*(\\d+(?:\\.\\d+)?)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRETS_FRACTION_PATTERN = Pattern.compile("\\bSecrets?:\\s*(\\d+)\\s*/\\s*(\\d+|\\?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_SECRETS_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s+Secrets\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile("\\s*Completed Rooms:\\s*(\\d+)(?:\\s*/\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENED_ROOMS_PATTERN = Pattern.compile("\\s*Opened Rooms:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUZZLE_STATE_PATTERN = Pattern.compile(".+: \\[([✖xX])]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRYPTS_TAB_PATTERN = Pattern.compile("\\s*Crypts:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRYPT_PROGRESS_PATTERN = Pattern.compile(
        ".*(?:\\b(?:opened|destroyed|blew up|blown up)\\b.*\\bcrypt\\b|\\bcrypt\\b.*\\b(?:opened|destroyed|blew up|blown up)\\b).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PLAYER_TAB_PATTERN = Pattern.compile("(?:\\[\\d+]\\s+)?(?:\\[[^\\]]+]\\s*)*(?<name>[A-Za-z0-9_]{3,16})\\b.*\\((?:Archer|Berserk(?:er)?|Mage|Healer|Tank)\\b.*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("\\b[A-Za-z0-9_]{3,16}\\b");
    private static final Pattern MIMIC_KILL_PATTERN = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!|\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRINCE_KILL_PATTERN = Pattern.compile(".*?(?:Prince dead!?|Prince Killed!)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAT_KILL_PATTERN = Pattern.compile(".*?(?:Bat dead!?|Bat Killed!|BatScore Killed!|Bat Score Killed!?)$", Pattern.CASE_INSENSITIVE);
    private static final String HYPIXEL_PRINCE_KILL_MESSAGE = "A Prince falls. +1 Bonus Score";
    private static final String HYPIXEL_BAT_KILL_MESSAGE = "A Bat has been slain. +1 Bonus Score";
    private static final String SERVER_CHAT_PREFIX = "[Kung] ";
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
        "^[^A-Za-z0-9_]*(?:\\[[^\\]]+]\\s*)*(?<name>[A-Za-z0-9_]{3,16})\\s+"
            + "(?:(?:died\\b|was killed\\b|was slain\\b|was shot\\b|fell\\b|burned\\b|drowned\\b|blew up\\b|"
            + "suffocated\\b|hit the ground\\b|disconnected\\b|became a ghost\\b)|.*\\bbecame a ghost\\b)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PLAYER_CLASS_PATTERN =
        Pattern.compile("\\b(Archer|Berserk(?:er)?|Mage|Healer|Tank)\\b", Pattern.CASE_INSENSITIVE);
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static Field tabHeaderField;
    private static Field tabFooterField;

    private final Map<UUID, PlayerStats> players = new HashMap<>();
    private final Map<String, UUID> playerNames = new HashMap<>();
    private final Map<String, DungeonClass> playerClasses = new HashMap<>();
    private final Set<String> dungeonPlayerNames = new HashSet<>();
    private final List<UUID> dungeonPlayerOrder = new ArrayList<>();
    private final UUID[] dungeonPlayerSlots = new UUID[MAX_DUNGEON_PLAYERS];
    private final Map<UUID, RoomKey> playerRooms = new HashMap<>();
    private final Map<RoomKey, UUID> lastPlayerInRoom = new HashMap<>();
    private final Set<String> countedClearedRooms = new HashSet<>();
    private final Map<RoomKey, Integer> roomSecretsFound = new HashMap<>();
    private final Map<RoomKey, Long> lastRoomPresenceTick = new HashMap<>();
    private final Map<Integer, Integer> derivedSecretsTotalHistogram = new HashMap<>();
    private final Set<String> debugClearedRooms = new HashSet<>();
    private final Set<String> debugCompletedRooms = new HashSet<>();
    private final Set<String> requestedTotalSecretNames = new HashSet<>();
    private final Set<String> requestedRunSecretBaselineNames = new HashSet<>();
    private final Set<String> requestedRunSecretFinalNames = new HashSet<>();
    private final Map<String, Integer> runSecretBaselines = new HashMap<>();
    private int secretsFound;
    private boolean serverSecretsFoundObserved;
    private int secretsAvailable = -1;
    private int secretsTotalAvailable = -1;
    private int derivedSecretsTotalAvailable = -1;
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
    private String serverScoreSource = "";
    private int estimatedScore = -1;
    private int floor;
    private boolean masterMode;
    private long runStartTick;
    private long elapsedSeconds = -1L;
    private boolean mimicKilled;
    private boolean princeKilled;
    private boolean batScoreKilled;
    private boolean bloodRoomCompleted;
    private boolean sendingRunSummary;
    private boolean mimicMessageSent;
    private boolean princeMessageSent;
    private boolean batMessageSent;
    private boolean fiveCryptMessageSent;
    private boolean fiveCryptTitleShown;
    private final Queue<String> pendingPartyMessages = new ArrayDeque<>();
    private long lastPartyMessageMillis;
    private String lastCountedSecretMessage = "";
    private long lastCountedSecretTick = Long.MIN_VALUE;
    private String lastCountedDeathMessage = "";
    private long lastCountedDeathTick = Long.MIN_VALUE;
    private int unattributedDeaths;
    private long currentObserveTick;
    private UUID lastTabStatsPlayerUuid;
    private int remainingTabStatsLines;
    private boolean runSecretFinalFetchStarted;
    private long runSecretFinalFetchStartTick = Long.MIN_VALUE;
    private int pendingRunSecretFinalFetches;
    private String lastLoggedDungeonSlotState = "";
    private String lastLoggedScoreCalcState = "";

    public void reset() {
        players.clear();
        playerNames.clear();
        playerClasses.clear();
        dungeonPlayerNames.clear();
        dungeonPlayerOrder.clear();
        Arrays.fill(dungeonPlayerSlots, null);
        playerRooms.clear();
        lastPlayerInRoom.clear();
        countedClearedRooms.clear();
        roomSecretsFound.clear();
        lastRoomPresenceTick.clear();
        derivedSecretsTotalHistogram.clear();
        debugClearedRooms.clear();
        debugCompletedRooms.clear();
        requestedTotalSecretNames.clear();
        requestedRunSecretBaselineNames.clear();
        requestedRunSecretFinalNames.clear();
        runSecretBaselines.clear();
        secretsFound = 0;
        serverSecretsFoundObserved = false;
        secretsAvailable = -1;
        secretsTotalAvailable = -1;
        derivedSecretsTotalAvailable = -1;
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
        serverScoreSource = "";
        estimatedScore = -1;
        floor = 0;
        masterMode = false;
        runStartTick = 0L;
        elapsedSeconds = -1L;
        mimicKilled = false;
        princeKilled = false;
        batScoreKilled = false;
        bloodRoomCompleted = false;
        mimicMessageSent = false;
        princeMessageSent = false;
        batMessageSent = false;
        fiveCryptMessageSent = false;
        fiveCryptTitleShown = false;
        pendingPartyMessages.clear();
        lastPartyMessageMillis = 0L;
        sendingRunSummary = false;
        lastCountedSecretMessage = "";
        lastCountedSecretTick = Long.MIN_VALUE;
        lastCountedDeathMessage = "";
        lastCountedDeathTick = Long.MIN_VALUE;
        unattributedDeaths = 0;
        currentObserveTick = 0L;
        lastTabStatsPlayerUuid = null;
        remainingTabStatsLines = 0;
        runSecretFinalFetchStarted = false;
        runSecretFinalFetchStartTick = Long.MIN_VALUE;
        pendingRunSecretFinalFetches = 0;
        lastLoggedDungeonSlotState = "";
        lastLoggedScoreCalcState = "";
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

    public boolean prepareRunSecretDeltas(Minecraft client, long nowTick) {
        if (client == null
            || client.player == null
            || !DungeonMapOverlayConfig.INSTANCE.directHypixelApiEnabled()) {
            return true;
        }
        if (runSecretFinalFetchStarted) {
            return pendingRunSecretFinalFetches <= 0
                || nowTick - runSecretFinalFetchStartTick >= RUN_SECRET_FINAL_TIMEOUT_TICKS;
        }

        runSecretFinalFetchStarted = true;
        runSecretFinalFetchStartTick = nowTick;
        Set<String> names = new HashSet<>();
        for (PlayerStats stats : summaryPlayers(client, client.player.getUUID())) {
            if (isPlayerName(stats.name())) {
                names.add(stats.name());
            }
        }
        if (names.isEmpty()) {
            return true;
        }

        for (String name : names) {
            requestRunSecretFinal(client, name);
        }
        return pendingRunSecretFinalFetches <= 0;
    }

    public void observePlayers(Minecraft client, long nowTick) {
        if (client.level == null || client.player == null) {
            return;
        }

        currentObserveTick = nowTick;
        flushPartyMessages(client);
        observeScoreboard(client);
        observeTabList(client);
        registerPlayerName(client.player.getName().getString(), client.player.getUUID());
        observePlayerRoom(client.player.getUUID(), client.player.blockPosition(), nowTick);
        for (UUID uuid : knownTrackedPlayerUuids()) {
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

    public boolean hasServerSecretsFound() {
        return serverSecretsFoundObserved;
    }

    public int secretsAvailable() {
        return secretsAvailable;
    }

    public int secretsTotalAvailable() {
        return secretsTotalAvailable;
    }

    public int derivedSecretsTotalAvailable() {
        return derivedSecretsTotalAvailable;
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
        return estimatedScore;
    }

    public int score(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        return estimatedScore(renderPlan, estimatedSecretsAvailable);
    }

    public int estimatedScore() {
        return estimatedScore;
    }

    public int sPlusSecretTarget(DungeonLiveMapWriter.MatchRenderPlan renderPlan, int estimatedSecretsAvailable) {
        int totalSecrets = bestSecretsAvailable(estimatedSecretsAvailable);
        if (totalSecrets <= 0) {
            return -1;
        }

        int scoreWithoutSecrets = projectedSPlusSkillScore() + 60 + speedScore() + bonusScore() + paulScoreBonus();
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
        int totalSecrets = bestSecretsAvailable(estimatedSecretsAvailable);
        int found = displayedSecretsFound(totalSecrets);
        int remaining = Math.max(0, target - found);
        logScoreCalculation(renderPlan, estimatedSecretsAvailable, totalSecrets, found, target, remaining);
        return remaining;
    }

    private void logScoreCalculation(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        int estimatedSecretsAvailable,
        int totalSecrets,
        int found,
        int target,
        int remaining
    ) {
        int skillRoomScore = roomProgressScore(renderPlan, 80.0);
        int deathPenalty = deathPenalty();
        int puzzlePenalty = failedPuzzles * 14;
        int skill = skillScore(renderPlan);
        int exploreRoom = exploreRoomScore(renderPlan);
        int secret = secretScore(totalSecrets);
        int explore = exploreScore(renderPlan, totalSecrets);
        int speed = speedScore();
        int bonus = bonusScore();
        int paul = paulScoreBonus();
        SkyBlockMayorTracker mayorTracker = SkyBlockMayorTracker.INSTANCE;
        int rawEstimate = skill + explore + speed + bonus + paul;
        int bestCompleted = bestCompletedRooms(renderPlan);
        int projectedCompletedCells = projectedCompletedRoomCells(renderPlan);
        int scoreRoomCells = scoreRoomCellTotal(renderPlan);
        int mapCompleted = mapCompletedRooms(renderPlan);
        int mapVisited = mapVisitedRooms(renderPlan);
        int mapObserved = observedRoomCount(renderPlan);
        int mapCells = roomCellCount(renderPlan);
        int tabCompletedOwners = tabCompletedRoomOwners(renderPlan);
        int scoreCompleted = scoreCompletedRooms(renderPlan);
        int bestOpened = bestOpenedRooms(renderPlan);
        int totalRooms = totalRoomsEstimate(renderPlan);
        int unfinishedPuzzleCells = unfinishedPuzzleRoomCells(renderPlan);
        String state = "found=" + found
            + " target=" + target
            + " remaining=" + remaining
            + " total=" + totalSecrets
            + " serverFound=" + serverSecretsFoundObserved
            + " available=" + secretsAvailable
            + " totalAvailable=" + secretsTotalAvailable
            + " derivedTotal=" + derivedSecretsTotalAvailable
            + " totalSource=" + secretsTotalSource(totalSecrets, estimatedSecretsAvailable)
            + " percent=" + secretsPercent
            + " score=" + score(renderPlan, totalSecrets)
            + " scoreSource=estimate"
            + " observedHypixelScore=" + serverScore
            + " observedHypixelScoreSource=" + (serverScoreSource.isBlank() ? "none" : serverScoreSource)
            + " estimatedScore=" + rawEstimate
            + " skill=" + skill
            + "(room=" + skillRoomScore
            + ",deathPenalty=" + deathPenalty
            + ",puzzlePenalty=" + puzzlePenalty
            + ") explore=" + explore
            + "(room=" + exploreRoom
            + ",secret=" + secret
            + ") speed=" + speed
            + " bonus=" + bonus
            + " paul=" + paul
            + " paulActive=" + paulScoreBonusActive()
            + " paulForce=" + DungeonMapOverlayConfig.INSTANCE.forcePaulScoreEnabled()
            + " paulMayor=" + mayorTracker.paulScoreBonusActive()
            + " mayor=" + blank(mayorTracker.mayorName())
            + " minister=" + blank(mayorTracker.ministerName())
            + " mayorStatus=" + mayorTracker.status()
            + " rooms=completed:" + bestCompleted
            + ",scoreCompleted:" + scoreCompleted
            + ",projectedCells:" + projectedCompletedCells
            + ",scoreCells:" + scoreRoomCells
            + ",opened:" + bestOpened
            + ",total:" + totalRooms
            + ",mapObserved:" + mapObserved
            + ",mapCells:" + mapCells
            + ",mapVisited:" + mapVisited
            + ",mapCompleted:" + mapCompleted
            + ",tabCompletedOwners:" + tabCompletedOwners
            + ",tabCompleted:" + completedRooms
            + ",tabOpened:" + openedRooms
            + ",tabTotal:" + totalRoomsFromTab
            + ",clearedPercent:" + clearedPercent
            + ",bloodCompleted:" + bloodRoomCompleted
            + ",unfinishedPuzzleCells:" + unfinishedPuzzleCells
            + " failedPuzzles=" + failedPuzzles
            + " crypts=" + cryptsOpened + "/" + cryptsAvailable
            + " mimic=" + mimicKilled
            + " prince=" + princeKilled
            + " bat=" + batScoreKilled;
        if (!state.equals(lastLoggedScoreCalcState)) {
            lastLoggedScoreCalcState = state;
            KungDebugRecorder.event("score-calc", state);
        }
    }

    public boolean mimicKilled() {
        return mimicKilled;
    }

    public boolean princeKilled() {
        return princeKilled;
    }

    public boolean batScoreKilled() {
        return batScoreKilled;
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

    public DungeonClass dungeonClass(UUID uuid) {
        PlayerStats stats = playerStats(uuid);
        if (stats != null && stats.dungeonClass() != DungeonClass.UNKNOWN) {
            return stats.dungeonClass();
        }
        if (stats != null) {
            DungeonClass namedClass = dungeonClass(stats.name());
            if (namedClass != DungeonClass.UNKNOWN) {
                return namedClass;
            }
        }
        return dungeonClass(trackedPlayerName(uuid));
    }

    public DungeonClass dungeonClass(String name) {
        if (!isPlayerName(name)) {
            return DungeonClass.UNKNOWN;
        }
        return playerClasses.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), DungeonClass.UNKNOWN);
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
                recordClearedRoom(
                    roomKey,
                    playersForClearedRoom(match.components()),
                    lastPlayerForClearedRoom(match.components())
                );
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
                recordClearedRoom(roomKey, playersForClearedCell(room), lastPlayerInRoom.get(room));
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
        int next = found;
        roomSecretsFound.put(currentRoom, next);
        if (next != previous) {
            debug(client, "Secret in " + roomNameFor(currentRoom, renderPlan) + ": " + next + "/" + max);
        }
    }

    public void sendRunSummary(Minecraft client, DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        if (client.player == null) {
            return;
        }

        UUID selfUuid = client.player.getUUID();
        List<PlayerStats> sortedPlayers = summaryPlayers(client, selfUuid);

        sendingRunSummary = true;
        try {
            client.player.sendSystemMessage(KungChat.message("Run Stats"));
            client.player.sendSystemMessage(Component.literal(roomProgressSummary(renderPlan)));
            client.player.sendSystemMessage(Component.literal(
                "Score " + score(renderPlan, 0)
                    + " | Secrets " + displayedSecretsFound(bestSecretsAvailable(0)) + "/" + unknownPositive(bestSecretsAvailable(0))
                    + " | Crypts " + cryptsOpened + "/" + unknownDash(cryptsAvailable)
            ));
            if (DungeonMapOverlayConfig.INSTANCE.playerTrackingEnabled()) {
                int totalFoundSecrets = displayedSecretsFound(bestSecretsAvailable(0));
                for (PlayerStats stats : sortedPlayers) {
                    client.player.sendSystemMessage(Component.literal(playerStatsSummaryLine(stats, totalFoundSecrets)));
                }
            }
        } finally {
            sendingRunSummary = false;
        }
    }

    private List<PlayerStats> summaryPlayers(Minecraft client, UUID selfUuid) {
        Map<UUID, PlayerStats> result = new LinkedHashMap<>();
        if (client != null && client.player != null) {
            addSummaryPlayer(result, selfUuid, client.player.getName().getString());
        }
        for (UUID uuid : dungeonPlayerSlots) {
            addSummaryPlayer(result, uuid, trackedPlayerName(uuid));
        }
        for (UUID uuid : dungeonPlayerOrder) {
            addSummaryPlayer(result, uuid, trackedPlayerName(uuid));
        }
        for (UUID uuid : knownTrackedPlayerUuids()) {
            addSummaryPlayer(result, uuid, trackedPlayerName(uuid));
        }
        for (PlayerStats stats : players.values()) {
            if (isSummaryPlayer(stats, selfUuid)) {
                result.putIfAbsent(stats.uuid(), stats);
            }
        }

        List<PlayerStats> sorted = new ArrayList<>(result.values());
        sorted.sort(Comparator
            .comparing((PlayerStats stats) -> selfUuid == null || !stats.uuid().equals(selfUuid))
            .thenComparing(PlayerStats::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(sorted);
    }

    private void addSummaryPlayer(Map<UUID, PlayerStats> playersByUuid, UUID uuid, String fallbackName) {
        if (uuid == null || !isPlayerName(fallbackName)) {
            return;
        }
        PlayerStats stats = playerStats(uuid, fallbackName);
        stats.setName(fallbackName);
        playersByUuid.putIfAbsent(uuid, stats);
    }

    private static String playerStatsSummaryLine(PlayerStats stats, int totalFoundSecrets) {
        return stats.name()
            + " - " + stats.secretsFound()
            + "/" + totalFoundSecrets
            + " Secrets"
            + totalSecretsSummary(stats)
            + " | " + stats.soloRoomsCleared()
            + "-" + stats.roomsCleared()
            + " Rooms | "
            + stats.deaths()
            + " Deaths";
    }

    private static String totalSecretsSummary(PlayerStats stats) {
        return stats.totalSecretsFound() >= 0
            ? " | " + INTEGER_FORMAT.format(stats.totalSecretsFound()) + " Total Secrets"
            : "";
    }

    public int roomSecretsFound(int roomGridX, int roomGridZ) {
        return roomSecretsFound.getOrDefault(new RoomKey(roomGridX, roomGridZ), 0);
    }

    public int matchedRoomSecretsFound(DungeonKnownRoomCatalog.MatchedRoom match) {
        return secretsFoundFor(match);
    }

    public List<DungeonRoomDataSyncClient.LivePlayerReport> livePlayerReports(Minecraft client) {
        UUID selfUuid = client != null && client.player != null ? client.player.getUUID() : null;
        List<DungeonRoomDataSyncClient.LivePlayerReport> reports = new ArrayList<>();
        for (PlayerStats stats : summaryPlayers(client, selfUuid)) {
            if (!isPlayerName(stats.name())) {
                continue;
            }
            reports.add(new DungeonRoomDataSyncClient.LivePlayerReport(
                stats.name(),
                Math.max(0, stats.secretsFound()),
                Math.max(0, stats.deaths()),
                "",
                System.currentTimeMillis()
            ));
        }
        return List.copyOf(reports);
    }

    public void mergeRemoteLivePlayers(List<DungeonRoomDataSyncClient.LivePlayerReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }
        for (DungeonRoomDataSyncClient.LivePlayerReport report : reports) {
            if (report == null || !isPlayerName(report.name())) {
                continue;
            }
            UUID uuid = registerDungeonPlayerName(report.name());
            if (uuid == null) {
                continue;
            }
            PlayerStats stats = playerStats(uuid, report.name());
            stats.setName(report.name());
            stats.setSecretsFound(Math.max(0, report.secretsFound()));
            stats.setDeaths(Math.max(0, report.deaths()));
        }
    }

    public void observeMapPlayerRoom(int roomGridX, int roomGridZ, long nowTick) {
        if (!isValidRoomGrid(roomGridX, roomGridZ)) {
            return;
        }
        lastRoomPresenceTick.put(new RoomKey(roomGridX, roomGridZ), nowTick);
    }

    public void observeMapPlayerRoom(String name, int roomGridX, int roomGridZ, long nowTick) {
        observeMapPlayerRoom(roomGridX, roomGridZ, nowTick);
        if (name == null || name.isBlank() || !isKnownTrackedPlayer(name)) {
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
        return name != null && dungeonPlayerNames.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isKnownPartyPlayer(String name) {
        return HypixelPartyTracker.INSTANCE.isKnownPartyPlayer(name);
    }

    public boolean isKnownTrackedPlayer(String name) {
        return isKnownPartyPlayer(name) || isKnownDungeonPlayer(name);
    }

    public int partyPlayerCount() {
        return HypixelPartyTracker.INSTANCE.partyPlayerCount();
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

    public Set<UUID> knownTrackedPlayerUuids() {
        Set<UUID> uuids = new HashSet<>();
        uuids.addAll(HypixelPartyTracker.INSTANCE.knownPartyPlayerUuids());
        for (String name : dungeonPlayerNames) {
            UUID uuid = playerNames.get(name);
            if (uuid != null) {
                uuids.add(uuid);
            }
        }
        return Set.copyOf(uuids);
    }

    public List<DungeonPlayerSlot> dungeonPlayerSlots(Minecraft client) {
        List<DungeonPlayerSlot> slots = new ArrayList<>(MAX_DUNGEON_PLAYERS);
        for (int index = 0; index < dungeonPlayerSlots.length; index++) {
            UUID uuid = dungeonPlayerSlots[index];
            if (uuid == null) {
                continue;
            }
            String name = trackedPlayerName(uuid);
            slots.add(new DungeonPlayerSlot(index, uuid, name, dungeonClass(uuid)));
        }
        if (!slots.isEmpty()) {
            return List.copyOf(slots);
        }

        if (client != null && client.player != null) {
            UUID selfUuid = client.player.getUUID();
            slots.add(new DungeonPlayerSlot(0, selfUuid, client.player.getName().getString(), dungeonClass(selfUuid)));
        }
        return List.copyOf(slots);
    }

    public UUID dungeonPlayerUuid(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return playerNames.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public UUID trackedPlayerUuid(String name) {
        if (name == null || name.isBlank() || !isKnownTrackedPlayer(name)) {
            return null;
        }
        UUID dungeonUuid = playerNames.get(name.toLowerCase(java.util.Locale.ROOT));
        if (dungeonUuid != null) {
            return dungeonUuid;
        }
        UUID partyUuid = HypixelPartyTracker.INSTANCE.partyPlayerUuid(name);
        if (partyUuid != null) {
            return partyUuid;
        }
        return null;
    }

    public String trackedPlayerName(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        PlayerStats stats = players.get(uuid);
        if (stats != null && isPlayerName(stats.name())) {
            return stats.name();
        }
        for (Map.Entry<String, UUID> entry : playerNames.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                return entry.getKey();
            }
        }
        return HypixelPartyTracker.INSTANCE.partyPlayerName(uuid);
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

        String message = stripFormatting(rawMessage).replaceAll("\\s+", " ").trim();
        if (isGeneratedRunSummaryLine(message)) {
            return;
        }

        String lower = message.toLowerCase(java.util.Locale.ROOT);
        observeScoreKillMessage(client, message);
        observeStatLine(client, message);
        if (lower.contains("secret")) {
            int increment = secretIncrement(lower);
            if (increment > 0 && countSecretMessageOnce(message, nowTick)) {
                recordSecret(client, playerFromMessage(client, message), increment);
            }
        }
        if (lower.contains("crypt")) {
            if (isCryptProgressMessage(message)) {
                updateCryptsOpened(client, cryptsOpened + 1);
            } else {
                KungDebugRecorder.event("crypts", "ignored chat line=" + KungDebugRecorder.compact(message));
            }
        }
        UUID deathUuid = deathEventPlayer(client, message);
        if (deathUuid != null && countDeathMessageOnce(message, nowTick)) {
            recordDeathEvent(client, deathUuid);
        }
        if (lower.contains("failed") && lower.contains("puzzle")) {
            failedPuzzles++;
        }
        if (lower.contains("[boss] the watcher: you have proven yourself")
            || lower.contains("blood clear")
            || lower.contains("blood room completed")) {
            bloodRoomCompleted = true;
        }
        updateEstimatedScore();
    }

    public void observeEntityDeath(Minecraft client, Entity entity) {
        if (entity == null) {
            return;
        }
        if (isMimicEntity(entity)) {
            markMimicKilled(client, true);
            updateEstimatedScore();
        }
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
            if (!isKnownTrackedPlayer(player.getName().getString())) {
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
        if (!name.isEmpty()) {
            return name.equalsIgnoreCase(client.player.getName().getString()) || isKnownTrackedPlayer(name);
        }

        RoomKey room = roomFromMapDecoration(decoration);
        if (room == null) {
            return false;
        }
        DungeonScanUtils.GridPosition selfGrid = DungeonScanUtils.getRoomGridPosition(client.player.blockPosition());
        if (selfGrid.gridX() == room.roomGridX() && selfGrid.gridZ() == room.roomGridZ()) {
            return true;
        }
        if (client.level == null || client.getConnection() == null) {
            return false;
        }
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == client.player || player.isInvisible() || !isKnownTrackedPlayer(player.getName().getString())) {
                continue;
            }
            if (client.getConnection().getPlayerInfo(player.getUUID()) == null) {
                continue;
            }
            DungeonScanUtils.GridPosition playerGrid = DungeonScanUtils.getRoomGridPosition(player.blockPosition());
            if (playerGrid.gridX() == room.roomGridX() && playerGrid.gridZ() == room.roomGridZ()) {
                return true;
            }
        }
        return false;
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
                observeTotalDeaths(value);
            }
        } else if (lower.contains("crypt")) {
            Matcher matcher = FRACTION_PATTERN.matcher(line);
            if (matcher.find()) {
                updateCryptsOpened(client, Integer.parseInt(matcher.group(1)));
                if (!matcher.group(2).equals("?")) {
                    cryptsAvailable = Math.max(cryptsAvailable, Integer.parseInt(matcher.group(2)));
                }
            } else {
                int value = firstInteger(line);
                if (value >= 0) {
                    updateCryptsOpened(client, value);
                }
            }
        } else if (lower.contains("score")) {
            observeServerScoreLine(line);
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
        List<UUID> currentSlots = new ArrayList<>(MAX_DUNGEON_PLAYERS);
        if (client.player != null) {
            HypixelPartyTracker.INSTANCE.observeOnlinePlayer(client.player.getName().getString(), client.player.getUUID());
            registerDungeonPlayerName(client, client.player.getName().getString(), client.player.getUUID());
        }
        observeTabOverlayText(client);
        for (PlayerInfo info : sortedTabPlayers(client)) {
            String profileName = info.getProfile().name();
            HypixelPartyTracker.INSTANCE.observeOnlinePlayer(profileName, info.getProfile().id());
            if (isPlayerName(profileName)) {
                registerPlayerName(profileName, info.getProfile().id());
            }
            String line = info.getTabListDisplayName() != null
                ? info.getTabListDisplayName().getString()
                : profileName;
            UUID dungeonPlayerUuid = observeTabLine(client, line, info.getProfile().id());
            if (dungeonPlayerUuid != null && currentSlots.size() < MAX_DUNGEON_PLAYERS) {
                currentSlots.add(dungeonPlayerUuid);
            }
        }
        updateDungeonPlayerSlots(client, currentSlots);
    }

    private static List<PlayerInfo> sortedTabPlayers(Minecraft client) {
        List<PlayerInfo> players = new ArrayList<>(client.getConnection().getOnlinePlayers());
        players.sort(Comparator
            .comparingInt(PlayerInfo::getTabListOrder)
            .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private UUID observeTabLine(String line) {
        return observeTabLine(null, line, null);
    }

    private UUID observeTabLine(Minecraft client, String line, UUID uuid) {
        Matcher secretsMatcher = SECRETS_PERCENT_PATTERN.matcher(line);
        if (secretsMatcher.find()) {
            clearTabStatsContinuation();
            observeSecretsPercent(Double.parseDouble(secretsMatcher.group(1)));
            return null;
        }

        Matcher completedMatcher = COMPLETED_ROOMS_PATTERN.matcher(line);
        if (completedMatcher.find()) {
            clearTabStatsContinuation();
            completedRooms = Integer.parseInt(completedMatcher.group(1));
            if (completedMatcher.group(2) != null) {
                totalRoomsFromTab = Integer.parseInt(completedMatcher.group(2));
            }
            return null;
        }

        Matcher openedMatcher = OPENED_ROOMS_PATTERN.matcher(line);
        if (openedMatcher.find()) {
            clearTabStatsContinuation();
            openedRooms = Integer.parseInt(openedMatcher.group(1));
            return null;
        }

        Matcher puzzleMatcher = PUZZLE_STATE_PATTERN.matcher(line);
        if (puzzleMatcher.find()) {
            clearTabStatsContinuation();
            failedPuzzles++;
            return null;
        }

        Matcher cryptsMatcher = CRYPTS_TAB_PATTERN.matcher(line);
        if (cryptsMatcher.find()) {
            clearTabStatsContinuation();
            updateCryptsOpened(client, Integer.parseInt(cryptsMatcher.group(1)));
            return null;
        }

        Matcher playerMatcher = PLAYER_TAB_PATTERN.matcher(line);
        if (playerMatcher.find()) {
            UUID playerUuid = registerDungeonPlayerName(client, playerMatcher.group("name"), uuid);
            lastTabStatsPlayerUuid = playerUuid;
            remainingTabStatsLines = 6;
            observePlayerStatLine(line, playerUuid);
            return playerUuid;
        }

        if (observeKnownPlayerStatLine(line)) {
            return null;
        }
        if (observeRecentTabPlayerStatLine(line)) {
            return null;
        }

        observeStatLine(client, line);
        return null;
    }

    private void updateDungeonPlayerSlots(Minecraft client, List<UUID> currentSlots) {
        Arrays.fill(dungeonPlayerSlots, null);
        int targetIndex = 0;
        Set<UUID> seen = new HashSet<>();
        if (client != null && client.player != null) {
            UUID selfUuid = client.player.getUUID();
            currentSlots.removeIf(selfUuid::equals);
            dungeonPlayerSlots[targetIndex++] = selfUuid;
            seen.add(selfUuid);
        }
        for (UUID uuid : currentSlots) {
            if (uuid == null || targetIndex >= dungeonPlayerSlots.length) {
                break;
            }
            if (!seen.add(uuid)) {
                continue;
            }
            dungeonPlayerSlots[targetIndex++] = uuid;
        }
        logDungeonPlayerSlots(currentSlots);
    }

    private void observeTabOverlayText(Minecraft client) {
        PlayerTabOverlay tabList = client.gui.getTabList();
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
        observeServerScoreLine(line);

        Matcher serverSecretsMatcher = SERVER_SECRETS_PATTERN.matcher(line);
        if (serverSecretsMatcher.find()) {
            observeSecretsTotal(client, Integer.parseInt(serverSecretsMatcher.group(1)));
            observeServerSecretsAvailable(Integer.parseInt(serverSecretsMatcher.group(2)));
            if (serverSecretsMatcher.group(3) != null) {
                secretsTotalAvailable = Integer.parseInt(serverSecretsMatcher.group(3));
            }
            updateDerivedSecretsTotalFromPercent();
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

    private void observeServerScoreLine(String line) {
        Matcher teamScoreMatcher = TEAM_SCORE_TEXT_PATTERN.matcher(line);
        if (teamScoreMatcher.matches()) {
            serverScore = Integer.parseInt(teamScoreMatcher.group(1));
            serverScoreSource = "team";
            return;
        }

        Matcher scoreMatcher = SCORE_TEXT_PATTERN.matcher(line);
        if (scoreMatcher.matches()) {
            serverScore = Integer.parseInt(scoreMatcher.group(1));
            serverScoreSource = "score";
        }
    }

    private void observeSecretsPercent(double observedPercent) {
        if (observedPercent < 0.0) {
            return;
        }
        secretsPercent = Math.max(secretsPercent, observedPercent);
        updateDerivedSecretsTotalFromPercent();
    }

    private void updateDerivedSecretsTotalFromPercent() {
        if (!serverSecretsFoundObserved || secretsFound <= 0 || secretsPercent <= 0.0) {
            return;
        }

        int candidate = (int) Math.floor(secretsFound * 100.0 / secretsPercent + 0.5);
        if (candidate < secretsFound || candidate <= 0 || candidate > 250) {
            return;
        }

        double reconstructedPercent = 100.0 * secretsFound / candidate;
        if (Math.abs(reconstructedPercent - secretsPercent) > 0.15) {
            return;
        }

        derivedSecretsTotalHistogram.merge(candidate, 1, Integer::sum);
        derivedSecretsTotalAvailable = derivedSecretsTotalHistogram.entrySet().stream()
            .max(Comparator
                .comparingInt((Map.Entry<Integer, Integer> entry) -> entry.getValue())
                .thenComparingInt(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .orElse(derivedSecretsTotalAvailable);
    }

    private void observeServerSecretsAvailable(int observedAvailable) {
        if (observedAvailable < 0) {
            return;
        }
        secretsAvailable = observedAvailable;
        if (secretsTotalAvailable > 0 && secretsTotalAvailable != observedAvailable) {
            secretsTotalAvailable = -1;
        }
        updateDerivedSecretsTotalFromPercent();
    }

    private void observeSecretsTotal(Minecraft client, int observedTotal) {
        if (observedTotal < 0) {
            return;
        }

        serverSecretsFoundObserved = true;
        int previousTotal = secretsFound;
        if (observedTotal <= previousTotal) {
            secretsFound = Math.max(secretsFound, observedTotal);
            updateDerivedSecretsTotalFromPercent();
            return;
        }

        secretsFound = observedTotal;
        updateDerivedSecretsTotalFromPercent();
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
        if (targetRoom == null || (next < previous && !debugSecret)) {
            return;
        }

        for (DungeonKnownRoomCatalog.MatchedComponent component : match.components()) {
            roomSecretsFound.remove(new RoomKey(component.roomGridX(), component.roomGridZ()));
        }
        roomSecretsFound.put(targetRoom, next);

        if (debugSecret && next != previous) {
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
        if (DungeonMapOverlayConfig.INSTANCE.dungeonDebugMessagesEnabled() && client != null && client.player != null) {
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

    private boolean observeKnownPlayerStatLine(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        for (Map.Entry<String, UUID> entry : playerNames.entrySet()) {
            if (containsPlayerName(lower, entry.getKey())) {
                observePlayerStatLine(line, entry.getValue());
                lastTabStatsPlayerUuid = entry.getValue();
                remainingTabStatsLines = 6;
                return true;
            }
        }
        return false;
    }

    private boolean observeRecentTabPlayerStatLine(String line) {
        if (lastTabStatsPlayerUuid == null || remainingTabStatsLines <= 0 || !isPlayerStatLine(line)) {
            return false;
        }
        remainingTabStatsLines--;
        observePlayerStatLine(line, lastTabStatsPlayerUuid);
        return true;
    }

    private void clearTabStatsContinuation() {
        lastTabStatsPlayerUuid = null;
        remainingTabStatsLines = 0;
    }

    private static boolean isPlayerStatLine(String line) {
        return PLAYER_SECRETS_PATTERN.matcher(line).find()
            || PLAYER_DEATHS_PATTERN.matcher(line).find()
            || PLAYER_CLASS_PATTERN.matcher(line).find();
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
            setDungeonClass(playerUuid, dungeonClass);
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
        return registerDungeonPlayerName(null, name, null);
    }

    private UUID registerDungeonPlayerName(Minecraft client, String name, UUID uuid) {
        if (!isPlayerName(name)) {
            return null;
        }

        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        dungeonPlayerNames.add(lowerName);
        UUID loadedUuid = loadedPlayerUuidByName(client, name);
        UUID observedPartyUuid = HypixelPartyTracker.INSTANCE.observedOnlinePlayerUuid(name);
        UUID partyUuid = HypixelPartyTracker.INSTANCE.partyPlayerUuid(name);
        UUID rememberedUuid = playerNames.get(lowerName);
        UUID playerUuid = loadedUuid;
        String source = "loaded-name";
        if (playerUuid == null) {
            playerUuid = observedPartyUuid;
            source = "party-observed";
        }
        if (playerUuid == null) {
            playerUuid = rememberedUuid;
            source = "remembered";
        }
        if (playerUuid == null) {
            playerUuid = partyUuid;
            source = "party";
        }
        if (playerUuid == null) {
            playerUuid = uuid;
            source = "tab";
        }
        if (playerUuid == null) {
            playerUuid = syntheticUuid(name);
            source = "synthetic";
        }
        if (uuid != null && !uuid.equals(playerUuid)) {
            KungDebugRecorder.event("player-slots", "name=" + name
                + " source=" + source
                + " uuid=" + shortUuid(playerUuid)
                + " tabUuid=" + shortUuid(uuid));
        }
        registerPlayerName(name, playerUuid);
        rememberDungeonPlayerOrder(playerUuid);
        requestTotalSecrets(client, name);
        requestRunSecretBaseline(client, name);
        return playerUuid;
    }

    private UUID loadedPlayerUuidByName(Minecraft client, String name) {
        if (client == null || client.level == null || client.getConnection() == null || !isPlayerName(name)) {
            return null;
        }
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == null || player.isInvisible() || !name.equalsIgnoreCase(player.getName().getString())) {
                continue;
            }
            if (client.getConnection().getPlayerInfo(player.getUUID()) != null) {
                return player.getUUID();
            }
        }
        return null;
    }

    private void logDungeonPlayerSlots(List<UUID> tabSlots) {
        String state = "tab=" + describeUuids(tabSlots) + " slots=" + describeDungeonSlots();
        if (!state.equals(lastLoggedDungeonSlotState)) {
            lastLoggedDungeonSlotState = state;
            KungDebugRecorder.event("player-slots", state);
        }
    }

    private String describeDungeonSlots() {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < dungeonPlayerSlots.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            UUID uuid = dungeonPlayerSlots[index];
            builder.append(index).append(':');
            if (uuid == null) {
                builder.append("<empty>");
            } else {
                builder.append(trackedPlayerName(uuid))
                    .append(':')
                    .append(shortUuid(uuid))
                    .append(':')
                    .append(dungeonClass(uuid));
            }
        }
        return builder.append(']').toString();
    }

    private String describeUuids(List<UUID> uuids) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < uuids.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            UUID uuid = uuids.get(index);
            builder.append(trackedPlayerName(uuid)).append(':').append(shortUuid(uuid));
        }
        return builder.append(']').toString();
    }

    private void registerPlayerName(String name, UUID uuid) {
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        UUID previousUuid = playerNames.put(lowerName, uuid);
        PlayerStats stats = playerStats(uuid, name);
        stats.setName(name);
        DungeonClass rememberedClass = playerClasses.get(lowerName);
        if (rememberedClass != null && rememberedClass != DungeonClass.UNKNOWN && stats.dungeonClass() == DungeonClass.UNKNOWN) {
            stats.setDungeonClass(rememberedClass);
        }
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

    private void setDungeonClass(UUID uuid, DungeonClass dungeonClass) {
        if (uuid == null || dungeonClass == null || dungeonClass == DungeonClass.UNKNOWN) {
            return;
        }
        PlayerStats stats = playerStats(uuid, playerName(uuid));
        stats.setDungeonClass(dungeonClass);
        if (isPlayerName(stats.name())) {
            playerClasses.put(stats.name().toLowerCase(java.util.Locale.ROOT), dungeonClass);
        }
    }

    private void requestTotalSecrets(Minecraft client, String name) {
        if (client == null
            || client.player == null
            || !DungeonMapOverlayConfig.INSTANCE.directHypixelApiEnabled()
            || !isPlayerName(name)) {
            return;
        }
        String requestedName = name.toLowerCase(java.util.Locale.ROOT);
        if (!requestedTotalSecretNames.add(requestedName)) {
            return;
        }
        HypixelSkyBlockProfileClient.INSTANCE.loadTotalSecrets(name).whenComplete((result, throwable) -> client.execute(() -> {
            if (throwable != null) {
                KungDebugRecorder.event("player-stats", "total-secrets failed player=" + name
                    + " error=" + throwable.getClass().getSimpleName());
                return;
            }
            if (!result.success()) {
                KungDebugRecorder.event("player-stats", "total-secrets failed player=" + name
                    + " error=" + result.error());
                return;
            }
            UUID uuid = playerNames.get(requestedName);
            if (uuid == null && isPlayerName(result.name())) {
                uuid = playerNames.get(result.name().toLowerCase(java.util.Locale.ROOT));
            }
            if (uuid == null) {
                return;
            }
            PlayerStats stats = playerStats(uuid, playerName(uuid));
            stats.setTotalSecretsFound(result.secretsFound());
            KungDebugRecorder.event("player-stats", "total-secrets player=" + stats.name()
                + " total=" + result.secretsFound());
        }));
    }

    private void requestRunSecretBaseline(Minecraft client, String name) {
        if (client == null
            || client.player == null
            || runStartTick <= 0L
            || !DungeonMapOverlayConfig.INSTANCE.directHypixelApiEnabled()
            || !isPlayerName(name)) {
            return;
        }
        String requestedName = name.toLowerCase(java.util.Locale.ROOT);
        if (!requestedRunSecretBaselineNames.add(requestedName)) {
            return;
        }
        HypixelSkyBlockProfileClient.INSTANCE.loadTotalSecrets(name).whenComplete((result, throwable) -> client.execute(() -> {
            if (throwable != null) {
                KungDebugRecorder.event("player-stats", "run-secret baseline failed player=" + name
                    + " error=" + throwable.getClass().getSimpleName());
                return;
            }
            if (!result.success()) {
                KungDebugRecorder.event("player-stats", "run-secret baseline failed player=" + name
                    + " error=" + result.error());
                return;
            }
            String resultName = isPlayerName(result.name()) ? result.name() : name;
            String key = resultName.toLowerCase(java.util.Locale.ROOT);
            UUID uuid = playerNames.get(key);
            if (uuid == null) {
                uuid = playerNames.get(requestedName);
            }
            if (uuid == null) {
                return;
            }
            runSecretBaselines.put(key, result.secretsFound());
            PlayerStats stats = playerStats(uuid, playerName(uuid));
            stats.setTotalSecretsFound(result.secretsFound());
            stats.setRunSecretBaseline(result.secretsFound());
            KungDebugRecorder.event("player-stats", "run-secret baseline player=" + stats.name()
                + " total=" + result.secretsFound());
        }));
    }

    private void requestRunSecretFinal(Minecraft client, String name) {
        if (client == null
            || client.player == null
            || !DungeonMapOverlayConfig.INSTANCE.directHypixelApiEnabled()
            || !isPlayerName(name)) {
            return;
        }
        String requestedName = name.toLowerCase(java.util.Locale.ROOT);
        if (!requestedRunSecretFinalNames.add(requestedName)) {
            return;
        }
        pendingRunSecretFinalFetches++;
        HypixelSkyBlockProfileClient.INSTANCE.loadTotalSecrets(name).whenComplete((result, throwable) -> client.execute(() -> {
            try {
                if (throwable != null) {
                    KungDebugRecorder.event("player-stats", "run-secret final failed player=" + name
                        + " error=" + throwable.getClass().getSimpleName());
                    return;
                }
                if (!result.success()) {
                    KungDebugRecorder.event("player-stats", "run-secret final failed player=" + name
                        + " error=" + result.error());
                    return;
                }
                String resultName = isPlayerName(result.name()) ? result.name() : name;
                String key = resultName.toLowerCase(java.util.Locale.ROOT);
                UUID uuid = playerNames.get(key);
                if (uuid == null) {
                    uuid = playerNames.get(requestedName);
                }
                if (uuid == null) {
                    return;
                }
                PlayerStats stats = playerStats(uuid, playerName(uuid));
                stats.setTotalSecretsFound(result.secretsFound());
                Integer baseline = runSecretBaselines.get(key);
                if (baseline == null) {
                    baseline = runSecretBaselines.get(requestedName);
                }
                if (baseline != null && result.secretsFound() >= baseline) {
                    int delta = result.secretsFound() - baseline;
                    stats.setApiRunSecretsFound(delta);
                    KungDebugRecorder.event("player-stats", "run-secret delta player=" + stats.name()
                        + " baseline=" + baseline
                        + " final=" + result.secretsFound()
                        + " delta=" + delta);
                }
            } finally {
                pendingRunSecretFinalFetches = Math.max(0, pendingRunSecretFinalFetches - 1);
            }
        }));
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

    private Set<UUID> playersForClearedRoom(List<DungeonKnownRoomCatalog.MatchedComponent> components) {
        Set<RoomKey> rooms = new HashSet<>();
        for (DungeonKnownRoomCatalog.MatchedComponent component : components) {
            rooms.add(new RoomKey(component.roomGridX(), component.roomGridZ()));
        }

        Set<UUID> playerUuids = new HashSet<>();
        for (Map.Entry<UUID, RoomKey> entry : playerRooms.entrySet()) {
            if (!rooms.contains(entry.getValue())) {
                continue;
            }
            PlayerStats stats = players.get(entry.getKey());
            if (stats == null
                || currentObserveTick > 0L
                    && stats.lastSeenTick() > 0L
                    && currentObserveTick - stats.lastSeenTick() > ROOM_CLEAR_PLAYER_STALE_TICKS) {
                continue;
            }
            playerUuids.add(entry.getKey());
        }
        return playerUuids;
    }

    private UUID lastPlayerForClearedRoom(List<DungeonKnownRoomCatalog.MatchedComponent> components) {
        for (DungeonKnownRoomCatalog.MatchedComponent component : components) {
            UUID uuid = lastPlayerInRoom.get(new RoomKey(component.roomGridX(), component.roomGridZ()));
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private Set<UUID> playersForClearedCell(RoomKey room) {
        Set<UUID> playerUuids = new HashSet<>();
        for (Map.Entry<UUID, RoomKey> entry : playerRooms.entrySet()) {
            if (!room.equals(entry.getValue())) {
                continue;
            }
            PlayerStats stats = players.get(entry.getKey());
            if (stats == null
                || currentObserveTick > 0L
                    && stats.lastSeenTick() > 0L
                    && currentObserveTick - stats.lastSeenTick() > ROOM_CLEAR_PLAYER_STALE_TICKS) {
                continue;
            }
            playerUuids.add(entry.getKey());
        }
        return playerUuids;
    }

    private void recordClearedRoom(String clearKey, Set<UUID> playerUuids, UUID fallbackPlayerUuid) {
        if (!countedClearedRooms.add(clearKey)) {
            return;
        }
        Set<UUID> knownPlayers = new HashSet<>();
        for (UUID playerUuid : playerUuids) {
            if (isKnownStatsUuid(playerUuid)) {
                knownPlayers.add(playerUuid);
            }
        }
        if (knownPlayers.isEmpty() && isKnownStatsUuid(fallbackPlayerUuid)) {
            knownPlayers.add(fallbackPlayerUuid);
        }
        if (knownPlayers.isEmpty()) {
            return;
        }

        boolean solo = knownPlayers.size() == 1 && !playerUuids.isEmpty();
        for (UUID playerUuid : knownPlayers) {
            PlayerStats stats = playerStats(playerUuid, playerName(playerUuid));
            stats.roomsCleared++;
            if (solo) {
                stats.soloRoomsCleared++;
            }
        }
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
            && (stats.uuid().equals(selfUuid) || isKnownTrackedPlayer(stats.name()));
    }

    private boolean isKnownStatsUuid(UUID uuid) {
        PlayerStats stats = players.get(uuid);
        return stats != null && isPlayerName(stats.name()) && isKnownTrackedPlayer(stats.name());
    }

    private String playerName(UUID uuid) {
        PlayerStats stats = players.get(uuid);
        return stats == null ? "Unknown" : stats.name();
    }

    private static UUID syntheticUuid(String name) {
        return UUID.nameUUIDFromBytes(("kung:dungeon-player:" + name.toLowerCase(java.util.Locale.ROOT))
            .getBytes(StandardCharsets.UTF_8));
    }

    private static String shortUuid(UUID uuid) {
        return uuid == null ? "null" : uuid.toString().substring(0, 8);
    }

    private static String stripFormatting(String text) {
        return text == null ? "" : text.replaceAll("\u00a7.", "");
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
            + bonusScore()
            + paulScoreBonus();
    }

    private int skillScore() {
        return skillScore(null);
    }

    private int skillScore(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int completedRoomScore = roomProgressScore(renderPlan, 80.0);
        int deathPenalty = deathPenalty();
        int puzzlePenalty = failedPuzzles * 14;
        return 20 + Math.clamp(completedRoomScore - deathPenalty - puzzlePenalty, 0, 80);
    }

    private int projectedSPlusSkillScore() {
        int deathPenalty = deathPenalty();
        int puzzlePenalty = failedPuzzles * 14;
        return 20 + Math.clamp(80 - deathPenalty - puzzlePenalty, 0, 80);
    }

    private int deathPenalty() {
        return deaths <= 0 ? 0 : deaths * 2 - 1;
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
        int scoreRoomCells = scoreRoomCellTotal(renderPlan);
        int projectedCompletedCells = projectedCompletedRoomCells(renderPlan);
        if (scoreRoomCells > 0 && projectedCompletedCells > 0) {
            return Math.clamp((int) Math.floor(maxScore * projectedCompletedCells / scoreRoomCells), 0, (int) maxScore);
        }
        return Math.clamp((int) (maxScore * scoreCompletedRooms(renderPlan) / totalRooms), 0, (int) maxScore);
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
            + (princeKilled ? 1 : 0)
            + (batScoreKilled ? 1 : 0);
    }

    private int paulScoreBonus() {
        return paulScoreBonusActive() ? 10 : 0;
    }

    private boolean paulScoreBonusActive() {
        return DungeonMapOverlayConfig.INSTANCE.forcePaulScoreEnabled()
            || SkyBlockMayorTracker.INSTANCE.paulScoreBonusActive();
    }

    private int totalRoomsEstimate(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int mapObservedRooms = observedRoomCount(renderPlan);
        if (mapObservedRooms > 0) {
            return Math.max(mapObservedRooms, Math.max(mapVisitedRooms(renderPlan), mapCompletedRooms(renderPlan)));
        }

        int minimumObservedRooms = Math.max(openedRooms, completedRooms);
        if (totalRoomsFromTab > 0) {
            return Math.max(totalRoomsFromTab, minimumObservedRooms);
        }
        int completed = completedRooms;
        if (completed <= 0 || clearedPercent <= 0.0) {
            return minimumObservedRooms;
        }
        int derivedTotal = (int) Math.floor(completed / (clearedPercent / 100.0) + 0.4);
        return Math.max(derivedTotal, minimumObservedRooms);
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
        if (renderPlan == null) {
            return completedRooms;
        }
        return Math.max(mapCompletedRooms(renderPlan), tabCompletedRoomOwners(renderPlan));
    }

    private int bestOpenedRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int mapObservedRooms = observedRoomCount(renderPlan);
        if (mapObservedRooms > 0) {
            return Math.min(
                Math.max(mapVisitedRooms(renderPlan), bestCompletedRooms(renderPlan)),
                mapObservedRooms
            );
        }
        return Math.max(openedRooms, completedRooms);
    }

    private static String unknownDash(int value) {
        return value >= 0 ? Integer.toString(value) : "?";
    }

    private static String unknownPositive(int value) {
        return value > 0 ? Integer.toString(value) : "?";
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String formatPercent(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private int scoreCompletedRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int completed = bestCompletedRooms(renderPlan);
        int totalRooms = totalRoomsEstimate(renderPlan);
        return totalRooms > 0 ? Math.min(completed, totalRooms) : completed;
    }

    private int projectedCompletedRoomCells(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int totalCells = scoreRoomCellTotal(renderPlan);
        if (totalCells <= 0) {
            return 0;
        }

        int completedCells = Math.max(completedRooms, bestCompletedRooms(renderPlan));
        if (completedCells >= totalCells) {
            return totalCells;
        }
        if (completedCells <= 0) {
            return 0;
        }

        int unfinishedPuzzleCells = unfinishedPuzzleRoomCells(renderPlan);
        if (unfinishedPuzzleCells <= 0 && shouldProjectFinishedBossRoom(renderPlan, completedCells, totalCells)) {
            return totalCells;
        }

        int projected = completedCells + 1;
        if (!bloodRoomCompleted) {
            projected++;
        }
        int cappedTotal = unfinishedPuzzleCells > 0
            ? Math.max(0, totalCells - unfinishedPuzzleCells)
            : totalCells;
        return Math.clamp(projected, 0, cappedTotal);
    }

    private boolean shouldProjectFinishedBossRoom(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        int completedCells,
        int totalCells
    ) {
        if (!bloodRoomCompleted || totalCells <= 1) {
            return false;
        }

        int observedRooms = observedRoomCount(renderPlan);
        if (observedRooms <= 0 || mapVisitedRooms(renderPlan) < observedRooms) {
            return false;
        }

        int openedCells = Math.max(openedRooms, bestOpenedRooms(renderPlan));
        return openedCells >= totalCells - 1 && completedCells >= totalCells - 2;
    }

    private int unfinishedPuzzleRoomCells(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        if (renderPlan == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<DungeonLiveMapWriter.CellKey, RoomType> entry : renderPlan.roomTypes().entrySet()) {
            if (entry.getValue() == RoomType.PUZZLE && !renderPlan.completedRooms().contains(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    private int scoreRoomCellTotal(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int mapCells = roomCellCount(renderPlan);
        if (mapCells > 0) {
            return Math.max(mapCells, Math.max(openedRooms, completedRooms));
        }
        if (totalRoomsFromTab > 0) {
            return Math.max(totalRoomsFromTab, Math.max(openedRooms, completedRooms));
        }
        int fallback = totalRoomsEstimate(renderPlan);
        return fallback > 0 ? Math.max(fallback, Math.max(openedRooms, completedRooms)) : 0;
    }

    private int mapCompletedRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        return mapRoomCount(renderPlan, renderPlan == null ? null : renderPlan.completedRooms());
    }

    private int mapVisitedRooms(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        return mapRoomCount(renderPlan, renderPlan == null ? null : renderPlan.visitedRooms());
    }

    private int tabCompletedRoomOwners(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        int mapObservedRooms = observedRoomCount(renderPlan);
        int mapRoomCells = roomCellCount(renderPlan);
        if (completedRooms <= 0 || mapObservedRooms <= 0 || mapRoomCells <= 0) {
            return 0;
        }
        if (completedRooms >= mapRoomCells) {
            return mapObservedRooms;
        }
        if (mapRoomCells >= 24 && completedRooms >= mapRoomCells - 1) {
            return Math.max(0, mapObservedRooms - 1);
        }
        return 0;
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

    public int bestSecretsAvailable(int estimatedSecretsAvailable) {
        int observedServerTotal = Math.max(secretsTotalAvailable, secretsAvailable);
        int observedTabTotal = Math.max(observedServerTotal, derivedSecretsTotalAvailable);
        if (observedTabTotal > 0) {
            return observedTabTotal;
        }
        return Math.max(0, estimatedSecretsAvailable);
    }

    private String secretsTotalSource(int totalSecrets, int estimatedSecretsAvailable) {
        if (totalSecrets > 0 && totalSecrets == Math.max(secretsTotalAvailable, secretsAvailable)) {
            return secretsTotalAvailable > 0 ? "server-total" : "server-fraction";
        }
        if (totalSecrets > 0 && totalSecrets == derivedSecretsTotalAvailable) {
            return "derived-percent";
        }
        if (estimatedSecretsAvailable > 0) {
            return "room-estimate";
        }
        return "unknown";
    }

    private int displayedSecretsFound(int secretsAvailable) {
        if (serverSecretsFoundObserved) {
            if (secretsAvailable > 0) {
                return Math.clamp(secretsFound, 0, secretsAvailable);
            }
            return Math.max(0, secretsFound);
        }
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
        if (renderPlan == null) {
            return 0;
        }
        return renderPlan.observedRoomCount();
    }

    private static int roomCellCount(DungeonLiveMapWriter.MatchRenderPlan renderPlan) {
        if (renderPlan == null) {
            return 0;
        }
        return renderPlan.roomCellCount();
    }

    private static int mapRoomCount(
        DungeonLiveMapWriter.MatchRenderPlan renderPlan,
        Set<DungeonLiveMapWriter.CellKey> cells
    ) {
        if (renderPlan == null || cells == null || cells.isEmpty()) {
            return 0;
        }
        return renderPlan.ownerCount(cells);
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

    private void observeTotalDeaths(int observedDeaths) {
        if (observedDeaths <= deaths) {
            return;
        }
        int delta = observedDeaths - deaths;
        deaths = observedDeaths;
        unattributedDeaths += delta;
        KungDebugRecorder.event("deaths", "observed-total total=" + deaths + " delta=" + delta
            + " unattributed=" + unattributedDeaths);
    }

    private void recordDeathEvent(Minecraft client, UUID deathUuid) {
        if (unattributedDeaths > 0) {
            unattributedDeaths--;
        } else {
            deaths++;
        }
        PlayerStats stats = playerStats(deathUuid, playerName(deathUuid));
        stats.incrementDeaths(1);
        KungDebugRecorder.event("deaths", "event player=" + stats.name() + " playerDeaths=" + stats.deaths()
            + " total=" + deaths + " unattributed=" + unattributedDeaths);
        sendDeathMessage(client, stats, deaths);
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
        return registerDungeonPlayerName(client, name, null);
    }

    private static boolean isGeneratedRunSummaryLine(String message) {
        return message.startsWith("[Kung")
            || message.contains("Run Stats")
            || message.startsWith("Rooms cleared:")
            || (message.startsWith("Score ") && message.contains(" | Secrets ") && message.contains(" | Crypts "))
            || message.contains(": Attributed Rooms ");
    }

    private void sendDeathMessage(Minecraft client, PlayerStats stats, int totalDeaths) {
        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        if (!config.deathMessagesEnabled()) {
            return;
        }
        String individualText = deathIndividualText(stats);
        String eventText = individualText + " | Total Deaths: " + totalDeaths;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(KungChat.message("Dungeon", eventText));
        }
        String partyMessage = deathPartyMessage(config, individualText, eventText, totalDeaths);
        if (!partyMessage.isBlank()) {
            sendPartyMessageAfterCooldown(client, partyMessage);
        }
    }

    private static String deathIndividualText(PlayerStats stats) {
        int playerDeaths = stats.deaths();
        return stats.name() + " died " + playerDeaths + " " + (playerDeaths == 1 ? "time" : "times");
    }

    private static String deathPartyMessage(
        DungeonMapOverlayConfig config,
        String individualText,
        String eventText,
        int totalDeaths
    ) {
        boolean shareTotal = config.deathMessagesShareTotalEnabled();
        boolean shareIndividual = config.deathMessagesShareIndividualEnabled();
        if (shareTotal && shareIndividual) {
            return eventText;
        }
        if (shareTotal) {
            return "Total Deaths: " + totalDeaths;
        }
        if (shareIndividual) {
            return individualText;
        }
        return "";
    }

    private void observeScoreKillMessage(Minecraft client, String message) {
        if (MIMIC_KILL_PATTERN.matcher(message).matches()) {
            markMimicKilled(client, false);
        }
        if (PRINCE_KILL_PATTERN.matcher(message).matches() || message.equals(HYPIXEL_PRINCE_KILL_MESSAGE)) {
            markPrinceKilled(client, message.equals(HYPIXEL_PRINCE_KILL_MESSAGE));
        }
        if (BAT_KILL_PATTERN.matcher(message).matches() || message.equals(HYPIXEL_BAT_KILL_MESSAGE)) {
            markBatScoreKilled(client, message.equals(HYPIXEL_BAT_KILL_MESSAGE));
        }
    }

    private void markMimicKilled(Minecraft client, boolean announce) {
        if (mimicKilled) {
            return;
        }
        mimicKilled = true;
        if (announce && !mimicMessageSent) {
            sendPartyMessageAfterCooldown(client, "Mimic dead!");
            mimicMessageSent = true;
        }
    }

    private void markPrinceKilled(Minecraft client, boolean announce) {
        if (princeKilled) {
            return;
        }
        princeKilled = true;
        if (announce && !princeMessageSent) {
            sendPartyMessageAfterCooldown(client, "Prince dead!");
            princeMessageSent = true;
        }
    }

    private void markBatScoreKilled(Minecraft client, boolean announce) {
        if (batScoreKilled) {
            return;
        }
        batScoreKilled = true;
        if (announce && !batMessageSent) {
            sendPartyMessageAfterCooldown(client, "Bat dead!");
            batMessageSent = true;
        }
    }

    private void updateCryptsOpened(Minecraft client, int value) {
        int previous = cryptsOpened;
        cryptsOpened = Math.max(cryptsOpened, value);
        if (cryptsOpened > previous) {
            KungDebugRecorder.event("crypts", "opened=" + cryptsOpened + " previous=" + previous);
            maybeAnnounceCryptProgress(client);
            maybeShowFiveCryptTitle(client);
            maybeAnnounceFiveCrypts(client);
        }
    }

    private static boolean isCryptProgressMessage(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("party >")
            || lower.contains("guild >")
            || lower.contains("from ")
            || lower.contains("to ")
            || lower.contains("[skyblocker]")
            || lower.contains("[kung]")
            || lower.contains("wither skull")
            || lower.contains("hitting you")
            || lower.contains("damage")) {
            return false;
        }
        return CRYPT_PROGRESS_PATTERN.matcher(message).matches();
    }

    private void maybeAnnounceFiveCrypts(Minecraft client) {
        if (fiveCryptMessageSent
            || cryptsOpened < TARGET_CRYPTS
            || !DungeonMapOverlayConfig.INSTANCE.fiveCryptPartyMessageEnabled()) {
            return;
        }
        fiveCryptMessageSent = true;
        sendPartyMessageAfterCooldown(client, DungeonMapOverlayConfig.INSTANCE.fiveCryptPartyMessage());
    }

    private void maybeAnnounceCryptProgress(Minecraft client) {
        if (!DungeonMapOverlayConfig.INSTANCE.cryptProgressPartyMessageEnabled()
            || cryptsOpened <= 0
            || cryptsOpened >= TARGET_CRYPTS) {
            return;
        }

        int remaining = Math.max(0, TARGET_CRYPTS - cryptsOpened);
        sendPartyMessageAfterCooldown(client, "CRYPT! " + remaining + " crypts to go!");
    }

    private void maybeShowFiveCryptTitle(Minecraft client) {
        if (fiveCryptTitleShown
            || cryptsOpened < TARGET_CRYPTS
            || !DungeonMapOverlayConfig.INSTANCE.fiveCryptTitleEnabled()) {
            return;
        }
        fiveCryptTitleShown = true;
        showFiveCryptTitle(client);
    }

    private static void showFiveCryptTitle(Minecraft client) {
        if (client == null || client.gui == null || client.player == null) {
            return;
        }
        client.gui.setTimes(0, 30, 5);
        client.gui.setTitle(Component.literal("5 Crypts").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.35F);
    }

    private void sendPartyMessageAfterCooldown(Minecraft client, String message) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingPartyMessages.isEmpty() && now - lastPartyMessageMillis >= 300L) {
            client.player.connection.sendCommand("pc " + prefixedServerChatMessage(message));
            lastPartyMessageMillis = now;
            return;
        }
        pendingPartyMessages.add(message);
    }

    private void flushPartyMessages(Minecraft client) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        long now = System.currentTimeMillis();
        while (!pendingPartyMessages.isEmpty() && now - lastPartyMessageMillis >= 300L) {
            client.player.connection.sendCommand("pc " + prefixedServerChatMessage(pendingPartyMessages.remove()));
            lastPartyMessageMillis = now;
        }
    }

    private static String prefixedServerChatMessage(String message) {
        String normalized = message == null ? "" : message.strip();
        return normalized.startsWith(SERVER_CHAT_PREFIX) ? normalized : SERVER_CHAT_PREFIX + normalized;
    }

    private boolean isMimicEntity(Entity entity) {
        if (!hasMimic() || !(entity instanceof Zombie zombie) || !zombie.isBaby()) {
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR.slots()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack armor = zombie.getItemBySlot(slot);
            if (!armor.isEmpty()) {
                return false;
            }
        }
        return true;
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
        private int soloRoomsCleared;
        private int secretsFound;
        private int totalSecretsFound = -1;
        private int runSecretBaseline = -1;
        private int apiRunSecretsFound = -1;
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

        public int soloRoomsCleared() {
            return soloRoomsCleared;
        }

        public int secretsFound() {
            return Math.max(secretsFound, apiRunSecretsFound);
        }

        public int totalSecretsFound() {
            return totalSecretsFound;
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

        private void setTotalSecretsFound(int totalSecretsFound) {
            this.totalSecretsFound = Math.max(this.totalSecretsFound, totalSecretsFound);
        }

        private void setRunSecretBaseline(int runSecretBaseline) {
            this.runSecretBaseline = Math.max(this.runSecretBaseline, runSecretBaseline);
        }

        private void setApiRunSecretsFound(int apiRunSecretsFound) {
            this.apiRunSecretsFound = Math.max(this.apiRunSecretsFound, apiRunSecretsFound);
        }

        private void merge(PlayerStats other) {
            deaths += other.deaths;
            roomsCleared += other.roomsCleared;
            soloRoomsCleared += other.soloRoomsCleared;
            secretsFound += other.secretsFound;
            totalSecretsFound = Math.max(totalSecretsFound, other.totalSecretsFound);
            runSecretBaseline = Math.max(runSecretBaseline, other.runSecretBaseline);
            apiRunSecretsFound = Math.max(apiRunSecretsFound, other.apiRunSecretsFound);
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

    public record DungeonPlayerSlot(int index, UUID uuid, String name, DungeonClass dungeonClass) {
    }
}
