package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.feature.dungeon.room.RoomType;
import com.github.beng420.kung.util.KungChat;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class BloodRushHelperFeature {
    private static final long DOOR_TITLE_STABLE_TICKS = 1;
    private static final long DOOR_CLICK_FAST_SCAN_TICKS = 10;
    private static final int DOOR_CLICK_MAX_BLOCK_DISTANCE = 6;

    private boolean initialTitleScheduled;
    private boolean initialTitleShown;
    private long initialTitleScheduledTick = Long.MIN_VALUE;
    private long initialTitleStableSinceTick = Long.MIN_VALUE;
    private int initialTitleCandidateCount = -1;
    private String initialTitleCandidateText = "";
    private Integer forcedDoorTitleCount;
    private final Set<String> observedOpenedDoorKeys = new HashSet<>();
    private boolean progressBaselineReady;
    private int lastRemainingDoorCount = -1;
    private long doorClickFastScanUntilTick = Long.MIN_VALUE;
    private String lastLoggedDoorTitleState = "";
    private String lastLoggedDoorProgressState = "";
    private long serverTicksSinceRunStart;
    private int maxObservedBloodRushDoors;
    private boolean bloodRushDoneShown;

    public static Feature definition() {
        return new Feature(
            "blood-rush-helper",
            "Blood rush helper",
            false,
            "Shows door-count titles while rushing blood."
        );
    }

    boolean doorTitlePending(long dungeonTick) {
        return enabled()
            && initialTitleScheduled
            && !initialTitleShown
            && forcedDoorTitleCount == null;
    }

    boolean shouldFastScanDoors(long dungeonTick) {
        return doorTitlePending(dungeonTick) || dungeonTick <= doorClickFastScanUntilTick;
    }

    void debugShowDoorTitle(Minecraft client, int doorCount) {
        if (!enabled()) {
            return;
        }
        displayDoorTitle(client, Math.max(0, doorCount) + " doors");
    }

    void debugScheduleDoorTitle(int doorCount, long dungeonTick) {
        if (!enabled()) {
            return;
        }
        forcedDoorTitleCount = Math.max(0, doorCount);
        scheduleInitial(dungeonTick);
    }

    void scheduleInitial(long dungeonTick) {
        if (!enabled()) {
            return;
        }
        initialTitleScheduled = true;
        initialTitleShown = false;
        initialTitleScheduledTick = dungeonTick;
        initialTitleStableSinceTick = Long.MIN_VALUE;
        initialTitleCandidateCount = -1;
        initialTitleCandidateText = "";
        lastLoggedDoorTitleState = "";
        serverTicksSinceRunStart = 0L;
        maxObservedBloodRushDoors = 0;
        bloodRushDoneShown = false;
        KungDebugRecorder.event("door-title", "scheduled initial atTick=" + dungeonTick);
    }

    void clear(long dungeonTick) {
        initialTitleScheduled = false;
        initialTitleShown = false;
        initialTitleScheduledTick = Long.MIN_VALUE;
        initialTitleStableSinceTick = Long.MIN_VALUE;
        initialTitleCandidateCount = -1;
        initialTitleCandidateText = "";
        forcedDoorTitleCount = null;
        observedOpenedDoorKeys.clear();
        progressBaselineReady = false;
        lastRemainingDoorCount = -1;
        doorClickFastScanUntilTick = Long.MIN_VALUE;
        lastLoggedDoorTitleState = "";
        lastLoggedDoorProgressState = "";
        serverTicksSinceRunStart = 0L;
        maxObservedBloodRushDoors = 0;
        bloodRushDoneShown = false;
        KungDebugRecorder.event("door-title", "cleared atTick=" + dungeonTick);
    }

    void serverTick() {
        if (enabled()) {
            serverTicksSinceRunStart++;
        }
    }

    void observeMessage(Minecraft client, String message, boolean realRunStarted) {
        if (!enabled() || !realRunStarted || message == null) {
            return;
        }
        String clean = message.replaceAll("\u00a7.", "")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
        if ("the blood door has been opened!".equals(clean)) {
            showBloodRushDone(client);
        }
    }

    void observeBloodDoorOpened(Minecraft client) {
        showBloodRushDone(client);
    }

    void observeProgress(Minecraft client, DungeonStateTracker tracker) {
        if (!enabled() || !tracker.realRunStarted()) {
            return;
        }

        DungeonMapSnapshot snapshot = tracker.mapSnapshot();
        DungeonLiveMapWriter.MatchRenderPlan plan = tracker.renderPlan();
        recordBloodRushDoorObservation(plan, snapshot);
        DoorEstimate estimate = remainingDoorEstimate(plan, snapshot);
        if (!estimate.available()) {
            logDoorProgressState("waiting estimate=false " + doorTitleDiagnostics(tracker));
            return;
        }

        Set<String> openedDoors = keyed(plan.minimumVisibleOpenedSpecialDoorCells(snapshot));
        int remainingDoors = estimate.count();
        if (!progressBaselineReady) {
            progressBaselineReady = true;
            observedOpenedDoorKeys.clear();
            observedOpenedDoorKeys.addAll(openedDoors);
            lastRemainingDoorCount = remainingDoors;
            logDoorProgressState("baseline opened="
                + openedDoors.size()
                + " remaining="
                + remainingDoors
                + " "
                + doorTitleDiagnostics(tracker));
            return;
        }

        Set<String> newlyOpenedDoors = new HashSet<>(openedDoors);
        newlyOpenedDoors.removeAll(observedOpenedDoorKeys);

        int previousRemainingDoors = lastRemainingDoorCount;
        boolean remainingDropped = previousRemainingDoors < 0 || remainingDoors < previousRemainingDoors;
        if (newlyOpenedDoors.isEmpty() && !remainingDropped) {
            lastRemainingDoorCount = remainingDoors;
            observedOpenedDoorKeys.addAll(openedDoors);
            logDoorProgressState("steady opened="
                + openedDoors.size()
                + " remaining="
                + remainingDoors
                + " "
                + doorTitleDiagnostics(tracker));
            return;
        }

        if (newlyOpenedDoors.isEmpty()) {
            logDoorProgressState("path-adjusted opened=[] remaining="
                + remainingDoors
                + " previousRemaining="
                + previousRemainingDoors
                + " "
                + doorTitleDiagnostics(tracker));
            return;
        }

        observedOpenedDoorKeys.addAll(openedDoors);
        lastRemainingDoorCount = remainingDoors;
        logDoorProgressState("door-fell opened="
            + newlyOpenedDoors
            + " remaining="
            + remainingDoors
            + " dropped="
            + remainingDropped
            + " "
            + doorTitleDiagnostics(tracker));

        if (!remainingDropped || remainingDoors <= 0) {
            return;
        }
        initialTitleScheduled = false;
        initialTitleShown = true;
        initialTitleCandidateCount = -1;
        initialTitleCandidateText = "";
        initialTitleStableSinceTick = Long.MIN_VALUE;
        displayDoorTitle(client, remainingDoorTitleText(plan, estimate));
    }

    void observeDoorBlockUse(Minecraft client, DungeonStateTracker tracker, BlockPos clickedPos) {
        if (!enabled()
            || client == null
            || client.level == null
            || client.player == null
            || clickedPos == null
            || !tracker.realRunStarted()
            || !tracker.state().isInDungeon()) {
            return;
        }

        DungeonScanUtils.GridPosition grid = DungeonScanUtils.nearestScanGridPosition(clickedPos);
        int scanGridX = grid.gridX();
        int scanGridZ = grid.gridZ();
        if (scanGridX < 0
            || scanGridZ < 0
            || scanGridX >= DungeonScanUtils.SCAN_GRID_SIZE
            || scanGridZ >= DungeonScanUtils.SCAN_GRID_SIZE
            || !DungeonScanUtils.isDoorScanPoint(scanGridX, scanGridZ)) {
            return;
        }

        int doorWorldX = DungeonScanUtils.worldXForScanGrid(scanGridX);
        int doorWorldZ = DungeonScanUtils.worldZForScanGrid(scanGridZ);
        if (Math.abs(clickedPos.getX() - doorWorldX) > DOOR_CLICK_MAX_BLOCK_DISTANCE
            || Math.abs(clickedPos.getZ() - doorWorldZ) > DOOR_CLICK_MAX_BLOCK_DISTANCE) {
            return;
        }

        long dungeonTick = tracker.dungeonTick();
        doorClickFastScanUntilTick = Math.max(doorClickFastScanUntilTick, dungeonTick + DOOR_CLICK_FAST_SCAN_TICKS);
        tracker.scanDungeonNow(client);
        KungDebugRecorder.event("door-title", "click fast-scan only door="
            + scanGridX
            + ","
            + scanGridZ
            + " until="
            + doorClickFastScanUntilTick);
    }

    void maybeShowDoorTitle(Minecraft client, DungeonStateTracker tracker) {
        if (!enabled()) {
            return;
        }

        long dungeonTick = tracker.dungeonTick();
        if (forcedDoorTitleCount != null) {
            displayDoorTitle(client, forcedDoorTitleCount + " doors");
            forcedDoorTitleCount = null;
            initialTitleScheduled = false;
            initialTitleShown = true;
            return;
        }

        if (!initialTitleScheduled || initialTitleShown || !tracker.realRunStarted()) {
            return;
        }

        DungeonMapSnapshot snapshot = tracker.mapSnapshot();
        DungeonLiveMapWriter.MatchRenderPlan plan = tracker.renderPlan();
        recordBloodRushDoorObservation(plan, snapshot);
        DoorEstimate estimate = initialDoorEstimate(plan, snapshot);
        if (!estimate.available()) {
            initialTitleCandidateCount = -1;
            initialTitleCandidateText = "";
            initialTitleStableSinceTick = Long.MIN_VALUE;
            logDoorTitleState("waiting estimate=false since="
                + initialTitleScheduledTick
                + " "
                + doorTitleDiagnostics(tracker));
            return;
        }

        String titleText = initialDoorTitleText(estimate);
        if (estimate.count() != initialTitleCandidateCount || !titleText.equals(initialTitleCandidateText)) {
            initialTitleCandidateCount = estimate.count();
            initialTitleCandidateText = titleText;
            initialTitleStableSinceTick = dungeonTick;
            logDoorTitleState("candidate count="
                + estimate.count()
                + " text=\""
                + titleText
                + "\" exact="
                + estimate.exact()
                + " tick="
                + dungeonTick
                + " "
                + doorTitleDiagnostics(tracker));
            return;
        }

        if (initialTitleStableSinceTick == Long.MIN_VALUE
            || dungeonTick - initialTitleStableSinceTick < DOOR_TITLE_STABLE_TICKS) {
            logDoorTitleState("confirming count="
                + estimate.count()
                + " text=\""
                + titleText
                + "\" exact="
                + estimate.exact()
                + " since="
                + initialTitleStableSinceTick
                + " "
                + doorTitleDiagnostics(tracker));
            return;
        }

        displayDoorTitle(client, titleText);
        initialTitleShown = true;
        initialTitleScheduled = false;
        initialTitleCandidateCount = -1;
        initialTitleCandidateText = "";
        initialTitleStableSinceTick = Long.MIN_VALUE;
        progressBaselineReady = true;
        observedOpenedDoorKeys.clear();
        observedOpenedDoorKeys.addAll(keyed(plan.minimumVisibleOpenedSpecialDoorCells(snapshot)));
        lastRemainingDoorCount = remainingDoorEstimate(plan, snapshot).count();
        logDoorTitleState("show initial count="
            + estimate.count()
            + " text=\""
            + titleText
            + "\" exact="
            + estimate.exact()
            + " remaining="
            + lastRemainingDoorCount
            + " "
            + doorTitleDiagnostics(tracker));
    }

    private static Set<String> keyed(Set<DungeonLiveMapWriter.CellKey> cells) {
        Set<String> keys = new HashSet<>();
        for (DungeonLiveMapWriter.CellKey cell : cells) {
            keys.add(cell.x() + "," + cell.z());
        }
        return keys;
    }

    private DoorEstimate initialDoorEstimate(DungeonLiveMapWriter.MatchRenderPlan plan, DungeonMapSnapshot snapshot) {
        boolean exact = doorEstimateExact(plan, snapshot);
        int count = exact
            ? plan.bloodRushTotalSpecialDoorCount()
            : plan.minimumVisibleBloodRushSpecialDoorCount(snapshot);
        return new DoorEstimate(count, exact);
    }

    private DoorEstimate remainingDoorEstimate(DungeonLiveMapWriter.MatchRenderPlan plan, DungeonMapSnapshot snapshot) {
        boolean exact = doorEstimateExact(plan, snapshot);
        int count = exact
            ? plan.bloodRushLockedSpecialDoorCount()
            : plan.minimumVisibleBloodRushLockedSpecialDoorCount(snapshot);
        return new DoorEstimate(count, exact);
    }

    private boolean doorEstimateExact(DungeonLiveMapWriter.MatchRenderPlan plan, DungeonMapSnapshot snapshot) {
        return plan.hasRoomType(RoomType.BLOOD)
            && Math.max(plan.bloodRushTotalSpecialDoorCount(), plan.totalSpecialDoorCount(snapshot)) > 0;
    }

    private void recordBloodRushDoorObservation(DungeonLiveMapWriter.MatchRenderPlan plan, DungeonMapSnapshot snapshot) {
        if (plan == null || snapshot == null) {
            return;
        }
        int observedTotal = Math.max(
            Math.max(plan.bloodRushTotalSpecialDoorCount(), plan.totalSpecialDoorCount(snapshot)),
            Math.max(plan.knownNonStartSpecialDoorCount(), plan.rawNonStartSpecialDoorCount(snapshot))
        );
        observedTotal = Math.max(observedTotal, plan.openedRawNonStartSpecialDoorCount(snapshot));
        maxObservedBloodRushDoors = Math.max(
            maxObservedBloodRushDoors,
            Math.max(observedTotal, plan.minimumVisibleBloodRushSpecialDoorCount(snapshot))
        );
    }

    private String initialDoorTitleText(DoorEstimate estimate) {
        if (estimate.count() == 1) {
            return estimate.exact() ? "1 door" : "1+ door";
        }
        return estimate.count() + (estimate.exact() ? "" : "+") + " doors";
    }

    private String remainingDoorTitleText(DungeonLiveMapWriter.MatchRenderPlan plan, DoorEstimate estimate) {
        int doorCount = estimate.count();
        if (doorCount == 1 && estimate.exact() && plan.bloodIsNext()) {
            return "Blood next";
        }
        if (doorCount == 1 && !estimate.exact()) {
            return "1+ door left";
        }
        if (doorCount == 1) {
            return "Last door";
        }
        return doorCount + (estimate.exact() ? "" : "+") + " doors left";
    }

    private String doorTitleDiagnostics(DungeonStateTracker tracker) {
        DungeonMapSnapshot snapshot = tracker.mapSnapshot();
        DungeonLiveMapWriter.MatchRenderPlan plan = tracker.renderPlan();
        return "observedRooms="
            + plan.observedRoomCount()
            + " rawRoomCells="
            + snapshot.observedRoomCount()
            + " rawDoors="
            + snapshot.lockedSpecialDoorCount()
            + " bloodRushTotal="
            + plan.bloodRushTotalSpecialDoorCount()
            + " bloodRushLocked="
            + plan.bloodRushLockedSpecialDoorCount()
            + " bloodRushOpened="
            + plan.bloodRushOpenedSpecialDoorCells().size()
            + " minimumTotal="
            + plan.minimumVisibleBloodRushSpecialDoorCount(snapshot)
            + " minimumLocked="
            + plan.minimumVisibleBloodRushLockedSpecialDoorCount(snapshot)
            + " minimumOpened="
            + plan.minimumVisibleOpenedSpecialDoorCells(snapshot).size()
            + " mapVisibleRooms="
            + plan.mapVisibleRoomCount(snapshot)
            + " recognizedMapRooms="
            + plan.recognizedMapVisibleRoomCount(snapshot)
            + " pathVisible="
            + plan.bloodRushPathVisibleEnough(snapshot)
            + " matches="
            + plan.matches().size()
            + " hasBlood="
            + plan.hasRoomType(RoomType.BLOOD)
            + " bloodRushDoors="
            + plan.bloodRushSpecialDoorSummary();
    }

    private record DoorEstimate(int count, boolean exact) {
        boolean available() {
            return count > 0;
        }
    }

    private void logDoorTitleState(String stateText) {
        if (!stateText.equals(lastLoggedDoorTitleState)) {
            lastLoggedDoorTitleState = stateText;
            KungDebugRecorder.event("door-title", stateText);
        }
    }

    private void logDoorProgressState(String stateText) {
        if (!stateText.equals(lastLoggedDoorProgressState)) {
            lastLoggedDoorProgressState = stateText;
            KungDebugRecorder.event("door-title", stateText);
        }
    }

    private void displayDoorTitle(Minecraft client, String text) {
        ChatFormatting color = "Blood next".equals(text) ? ChatFormatting.RED : ChatFormatting.GOLD;
        showDoorTitle(client, text, null, color, doorTitleStayTicks());
        sendDoorTitleChat(client, text);
    }

    private void showBloodRushDone(Minecraft client) {
        if (bloodRushDoneShown) {
            return;
        }
        bloodRushDoneShown = true;
        String subtitle = "took "
            + formatServerTickTime(serverTicksSinceRunStart)
            + " | "
            + Math.max(0, maxObservedBloodRushDoors)
            + " doors";
        showDoorTitle(client, "Blood rush done", subtitle, ChatFormatting.RED, Math.max(20, doorTitleStayTicks()));
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(KungChat.message("BR", "Blood rush done - " + subtitle));
        }
        KungDebugRecorder.event("door-title", "blood done subtitle=\"" + subtitle + "\" serverTicks=" + serverTicksSinceRunStart);
    }

    private static void showDoorTitle(
        Minecraft client,
        String text,
        String subtitle,
        ChatFormatting color,
        int stayTicks
    ) {
        if (client == null || client.gui == null) {
            return;
        }
        client.gui.setTimes(0, Math.max(1, stayTicks), 5);
        client.gui.setSubtitle(subtitle == null || subtitle.isBlank()
            ? Component.empty()
            : Component.literal(subtitle).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
        client.gui.setTitle(Component.literal(text).withStyle(color, ChatFormatting.BOLD));
    }

    private static String formatServerTickTime(long ticks) {
        long millis = Math.max(0L, ticks) * 50L;
        long seconds = millis / 1000L;
        long milliseconds = millis % 1000L;
        return String.format(Locale.ROOT, "%d.%03ds", seconds, milliseconds);
    }

    private static void sendDoorTitleChat(Minecraft client, String text) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.sendSystemMessage(KungChat.message("BR", text));
    }

    private static boolean enabled() {
        return DungeonMapOverlayConfig.INSTANCE.bloodRushHelperEnabled();
    }

    private static int doorTitleStayTicks() {
        return Math.max(1, DungeonMapOverlayConfig.INSTANCE.bloodRushHelperTitleDurationTenths() * 2);
    }
}
