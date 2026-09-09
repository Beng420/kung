package com.github.beng420.kung.skyblock;

import com.github.beng420.kung.config.KungConfig;
import com.github.beng420.kung.config.category.DebugConfig;
import com.github.beng420.kung.message.KungMessages;
import com.github.beng420.kung.util.KungDebugRecorder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class HypixelInstanceTracker {
    public static final HypixelInstanceTracker INSTANCE = new HypixelInstanceTracker();

    private static final Pattern SERVER_ID_PATTERN =
        Pattern.compile("\\b(?:mini\\d{1,5}|mega\\d{1,5}|m\\d{2,5})[a-z]{0,4}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_LINE_PATTERN =
        Pattern.compile("^(?:\\s*\\u23e3\\s*)?(?:Area|Location)\\s*:\\s*(?<location>.+)$", Pattern.CASE_INSENSITIVE);
    private static final long PENDING_INSTANCE_TTL_TICKS = 400L;
    private static final long PENDING_TRANSFER_SERVER_TTL_TICKS = 200L;
    private static final long CURRENT_TRANSFER_SERVER_TTL_TICKS = 24_000L;

    private static Field tabHeaderField;
    private static Field tabFooterField;

    private boolean tracking;
    private boolean dungeonHub;
    private boolean catacombs;
    private boolean dungeonRunContext;
    private String instanceLine = "";
    private String serverId = "";
    private String lastAnnouncedContext = "";
    private String lastLoggedContextState = "";
    private String worldTransferServerId = "";
    private long lastWorldTransferServerTick = Long.MIN_VALUE;
    private String pendingTransferServerId = "";
    private long lastPendingTransferServerTick = Long.MIN_VALUE;
    private String pendingInstanceLine = "";
    private long lastPendingInstanceTick = Long.MIN_VALUE;
    private boolean pendingInstanceRequiresNewServer;
    private long tickCounter;
    private Object observedLevel;
    private Component packetTabHeader;
    private Component packetTabFooter;
    private List<String> sidebarLines = List.of();
    private List<String> visibleLines = List.of();

    private HypixelInstanceTracker() {
    }

    public static void initializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.tick(client));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
            INSTANCE.observeMessage(Minecraft.getInstance(), message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            INSTANCE.observeMessage(Minecraft.getInstance(), message.getString()));
        ClientSendMessageEvents.COMMAND.register(INSTANCE::observeSentCommand);
    }

    public void observeWorldChangePacket() {
        KungDebugRecorder.event("packet", "world-change packet");
        observeWorldChange();
    }

    private void observeWorldChange() {
        String promotedServer = !pendingTransferServerId.isBlank() && recentlyObservedPendingTransferServer()
            ? pendingTransferServerId
            : "";
        String keptServer = promotedServer.isBlank() && recentlyObservedWorldTransferServer()
            ? worldTransferServerId
            : "";
        KungDebugRecorder.event("context", "world-change reset oldInstance="
            + blank(instanceLine)
            + " oldServer="
            + blank(serverId)
            + " pendingInstance="
            + blank(pendingInstanceLine)
            + " pendingTransfer="
            + blank(pendingTransferServerId)
            + " promotedServer="
            + blank(promotedServer)
            + " keptServer="
            + blank(keptServer));
        if (!promotedServer.isBlank()) {
            worldTransferServerId = promotedServer;
            lastWorldTransferServerTick = tickCounter;
        } else if (!recentlyObservedWorldTransferServer()) {
            worldTransferServerId = "";
            lastWorldTransferServerTick = Long.MIN_VALUE;
        } else {
            lastWorldTransferServerTick = tickCounter;
        }
        pendingTransferServerId = "";
        lastPendingTransferServerTick = Long.MIN_VALUE;
        if (!pendingInstanceLine.isBlank() && recentlyObservedPendingInstance()) {
            pendingInstanceRequiresNewServer = false;
            KungDebugRecorder.event("context", "pending instance confirmed by world-change");
        }
        dungeonHub = false;
        catacombs = false;
        dungeonRunContext = false;
        instanceLine = "";
        serverId = "";
        packetTabHeader = null;
        packetTabFooter = null;
        sidebarLines = List.of();
        visibleLines = List.of();
    }

    public void observeTabList(Component header, Component footer) {
        packetTabHeader = header;
        packetTabFooter = footer;
        KungDebugRecorder.event("packet", "tab-list header="
            + compactComponent(header)
            + " footer="
            + compactComponent(footer));
    }

    public boolean tracking() {
        return tracking;
    }

    public boolean dungeonHub() {
        return dungeonHub;
    }

    public boolean catacombs() {
        return tracking && !dungeonHub && (catacombs || dungeonRunContext);
    }

    public boolean pendingCatacombs() {
        return tracking
            && !dungeonHub
            && isCatacombsInstance(pendingInstanceLine)
            && recentlyObservedPendingInstance()
            && pendingInstanceServerReady();
    }

    public boolean dungeonRunContext() {
        return tracking && !dungeonHub && dungeonRunContext;
    }

    public String instanceLine() {
        return instanceLine;
    }

    public String serverId() {
        return serverId;
    }

    public List<String> sidebarLines() {
        return sidebarLines;
    }

    public List<String> visibleLines() {
        return visibleLines;
    }

    private void tick(Minecraft client) {
        tickCounter++;
        if (client.level == null || client.player == null) {
            observedLevel = null;
            clear();
            return;
        }

        if (observedLevel != client.level) {
            Object oldLevel = observedLevel;
            observedLevel = client.level;
            KungDebugRecorder.event("client", "level identity changed old="
                + identity(oldLevel)
                + " new="
                + identity(observedLevel));
            observeWorldChange();
        }

        tracking = true;
        ArrayList<String> lines = new ArrayList<>();
        sidebarLines = List.copyOf(readSidebarLines(client));
        lines.addAll(sidebarLines);
        Component tabHeader = packetTabHeader != null ? packetTabHeader : readTabComponent(client, true);
        Component tabFooter = packetTabFooter != null ? packetTabFooter : readTabComponent(client, false);
        addComponentLines(lines, tabHeader);
        addComponentLines(lines, tabFooter);
        visibleLines = List.copyOf(lines);

        dungeonHub = false;
        catacombs = false;
        dungeonRunContext = false;
        instanceLine = "";
        serverId = "";
        int dungeonMarkerCount = 0;
        boolean dungeonStartCountdown = false;
        for (String line : visibleLines) {
            String clean = cleanLine(line);
            String lower = clean.toLowerCase(Locale.ROOT);
            if (lower.contains("dungeon hub")) {
                dungeonHub = true;
            }
            if (lower.contains("catacombs")) {
                catacombs = true;
            }
            if (isDungeonRunMarker(lower)) {
                dungeonMarkerCount++;
            }
            if (lower.contains("dungeon starts")) {
                dungeonStartCountdown = true;
            }
            String parsedInstance = instanceLineFrom(clean);
            if (instanceLine.isBlank() && !parsedInstance.isBlank()) {
                instanceLine = parsedInstance;
            }
            if (serverId.isBlank()) {
                serverId = serverIdFrom(clean);
            }
        }
        if (serverId.isBlank() && !worldTransferServerId.isBlank() && recentlyObservedWorldTransferServer()) {
            serverId = worldTransferServerId;
        } else if (!worldTransferServerId.isBlank() && !recentlyObservedWorldTransferServer()) {
            worldTransferServerId = "";
            lastWorldTransferServerTick = Long.MIN_VALUE;
        }
        dungeonRunContext = catacombs || dungeonStartCountdown || dungeonMarkerCount >= 2;
        boolean pendingInstanceAvailable = !pendingInstanceLine.isBlank() && recentlyObservedPendingInstance();
        boolean pendingInstanceServerReady = pendingInstanceServerReady();
        boolean usingPendingInstance = instanceLine.isBlank() && pendingInstanceAvailable && pendingInstanceServerReady;
        if (usingPendingInstance) {
            instanceLine = pendingInstanceLine;
        } else if (!instanceLine.isBlank()) {
            pendingInstanceLine = "";
            lastPendingInstanceTick = Long.MIN_VALUE;
            pendingInstanceRequiresNewServer = false;
        }
        applyFinalInstanceFlags();
        announceChanges(client);
        if (usingPendingInstance && !serverId.isBlank()) {
            pendingInstanceLine = "";
            lastPendingInstanceTick = Long.MIN_VALUE;
            pendingInstanceRequiresNewServer = false;
        }
        logContextState();
    }

    private void clear() {
        tracking = false;
        dungeonHub = false;
        catacombs = false;
        dungeonRunContext = false;
        instanceLine = "";
        serverId = "";
        packetTabHeader = null;
        packetTabFooter = null;
        sidebarLines = List.of();
        visibleLines = List.of();
    }

    private void announceChanges(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (instanceLine.isBlank() || serverId.isBlank()) {
            return;
        }
        String context = instanceLine + " (" + serverId + ")";
        if (!context.equals(lastAnnouncedContext)) {
            lastAnnouncedContext = context;
            KungDebugRecorder.event("context", "announce Entered " + context);
            DebugConfig debug = KungConfig.get().debug;
            if (debug.contextMessagesEnabled()) {
                client.player.sendSystemMessage(KungMessages.debug("Context", "Entered " + context));
            }
        }
    }

    private void observeMessage(Minecraft client, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (isKungMessage(text)) {
            return;
        }
        KungDebugRecorder.event("message", text);
        observeTransferServerMessage(text);
        observeWarpMessage(text);
    }

    private void observeTransferServerMessage(String text) {
        for (String line : text.split("\\R")) {
            String transferServerId = serverIdFromTransferMessage(cleanLine(line));
            if (!transferServerId.isBlank()) {
                pendingTransferServerId = transferServerId;
                lastPendingTransferServerTick = tickCounter;
                KungDebugRecorder.event("context", "pending transfer server=" + transferServerId);
            }
        }
    }

    private void observeWarpMessage(String text) {
        for (String line : text.split("\\R")) {
            String clean = cleanLine(line);
            String destination = destinationFromMessage(Minecraft.getInstance(), clean);
            if (!destination.isBlank()) {
                setPendingInstance(destination, destinationRequiresNewServer(Minecraft.getInstance(), clean));
            }
        }
    }

    private void observeSentCommand(String command) {
        String destination = destinationFromCommand(command);
        if (!destination.isBlank()) {
            KungDebugRecorder.event("context", "sent command destination=" + destination);
            setPendingInstance(destination, true);
        }
    }

    private void setPendingInstance(String destination, boolean requiresNewServer) {
        pendingInstanceLine = destination;
        lastPendingInstanceTick = tickCounter;
        pendingInstanceRequiresNewServer = requiresNewServer;
        KungDebugRecorder.event("context", "pending instance="
            + destination
            + " requiresNewServer="
            + requiresNewServer);
    }

    private void applyFinalInstanceFlags() {
        String lowerInstance = instanceLine.toLowerCase(Locale.ROOT);
        if (lowerInstance.contains("dungeon hub")) {
            dungeonHub = true;
        }
        if (lowerInstance.contains("catacombs")) {
            catacombs = true;
            dungeonRunContext = true;
        }
    }

    private void logContextState() {
        String state = "tracking="
            + tracking
            + " dungeonHub="
            + dungeonHub
            + " catacombs="
            + catacombs
            + " dungeonRunContext="
            + dungeonRunContext
            + " instance="
            + blank(instanceLine)
            + " server="
            + blank(serverId)
            + " worldTransfer="
            + blank(worldTransferServerId)
            + " pendingInstance="
            + blank(pendingInstanceLine)
            + " pendingNeedsNewServer="
            + pendingInstanceRequiresNewServer
            + " pendingTransfer="
            + blank(pendingTransferServerId);
        if (!state.equals(lastLoggedContextState)) {
            lastLoggedContextState = state;
            KungDebugRecorder.event("context-state", state + " lines=" + summarizeLines(visibleLines));
        }
    }

    private static List<String> readSidebarLines(Minecraft client) {
        if (client.level == null) {
            return List.of();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add(sidebar.getDisplayName().getString());
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            lines.add(entry.display() != null ? entry.display().getString() : entry.owner());
        }
        return lines;
    }

    private static void addComponentLines(List<String> lines, Component component) {
        if (component == null) {
            return;
        }

        for (String line : component.getString().split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
    }

    private static Component readTabComponent(Minecraft client, boolean header) {
        try {
            PlayerTabOverlay tabList = client.gui.getTabList();
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

    private boolean recentlyObservedPendingInstance() {
        return lastPendingInstanceTick != Long.MIN_VALUE
            && tickCounter - lastPendingInstanceTick <= PENDING_INSTANCE_TTL_TICKS;
    }

    private boolean recentlyObservedPendingTransferServer() {
        return lastPendingTransferServerTick != Long.MIN_VALUE
            && tickCounter - lastPendingTransferServerTick <= PENDING_TRANSFER_SERVER_TTL_TICKS;
    }

    private boolean recentlyObservedWorldTransferServer() {
        return lastWorldTransferServerTick != Long.MIN_VALUE
            && tickCounter - lastWorldTransferServerTick <= CURRENT_TRANSFER_SERVER_TTL_TICKS;
    }

    private boolean pendingInstanceServerReady() {
        return !pendingInstanceRequiresNewServer
            || lastWorldTransferServerTick >= lastPendingInstanceTick;
    }

    private static boolean isDungeonRunMarker(String lowerLine) {
        return lowerLine.contains("catacombs")
            || lowerLine.contains("dungeon starts")
            || lowerLine.startsWith("secrets:")
            || lowerLine.startsWith("crypts:")
            || lowerLine.startsWith("deaths:")
            || lowerLine.startsWith("score:")
            || lowerLine.contains("milestone")
            || lowerLine.contains("dungeon cleared")
            || lowerLine.matches(".*\\b(?:m|f)[1-7]\\b.*");
    }

    private static String instanceLineFrom(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }

        String clean = cleanLine(line);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (isOtherPlayerLocationLine(lower)) {
            return "";
        }
        if (lower.contains("the catacombs") || lower.contains("catacombs")) {
            return clean;
        }
        if (lower.contains("dungeon hub")) {
            return "Dungeon Hub";
        }
        if (lower.contains("private island") || lower.contains("your island")) {
            return "Private Island";
        }
        if (lower.contains("crimson isle")) {
            return "Crimson Isle";
        }
        if (lower.contains("the garden")) {
            return "The Garden";
        }

        Matcher locationMatcher = LOCATION_LINE_PATTERN.matcher(clean);
        if (locationMatcher.matches()) {
            return locationMatcher.group("location").trim();
        }

        int locationSymbol = clean.indexOf('\u23e3');
        if (locationSymbol >= 0 && locationSymbol + 1 < clean.length()) {
            String location = clean.substring(locationSymbol + 1).trim();
            return location.isBlank() ? "" : location;
        }
        return "";
    }

    private static boolean isCatacombsInstance(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("catacombs");
    }

    private static String serverIdFrom(String line) {
        Matcher matcher = SERVER_ID_PATTERN.matcher(line);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
    }

    private static String serverIdFromTransferMessage(String line) {
        String lower = cleanLine(line).toLowerCase(Locale.ROOT);
        if (!lower.contains("sending to server")
            && !lower.contains("transferring")
            && !lower.contains("joining server")) {
            return "";
        }
        return serverIdFrom(line);
    }

    private static String destinationFromMessage(Minecraft client, String line) {
        String clean = cleanLine(line);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (isOtherPlayerTravelLine(lower)) {
            KungDebugRecorder.event("context", "ignored other-player travel line=" + KungDebugRecorder.compact(clean));
            return "";
        }
        if (isOwnCatacombsEntryMessage(client, line)) {
            return "The Catacombs";
        }
        if (lower.contains("your skyblock island")) {
            return "Private Island";
        }
        if (lower.equals("starting in 1 second.") || lower.contains("dungeon starts in")) {
            return "The Catacombs";
        }
        if (lower.contains("dungeon hub") && isOwnOrSystemWarpLine(lower)) {
            return "Dungeon Hub";
        }
        if (lower.matches(".*\\b(?:hub|skyblock hub)\\b.*")
            && (lower.contains("warping") || lower.contains("sending"))) {
            return "Hub";
        }
        return "";
    }

    private static boolean destinationRequiresNewServer(Minecraft client, String line) {
        String lower = cleanLine(line).toLowerCase(Locale.ROOT);
        return isOwnCatacombsEntryMessage(client, line)
            || lower.contains("your skyblock island")
            || lower.contains("warping")
            || lower.contains("sending");
    }

    private static boolean isOwnOrSystemWarpLine(String lowerLine) {
        return lowerLine.startsWith("you ")
            || lowerLine.contains("warping")
            || lowerLine.contains("sending")
            || lowerLine.contains("transferring")
            || lowerLine.contains("joining server");
    }

    private static boolean isOwnCatacombsEntryMessage(Minecraft client, String line) {
        if (client == null || client.player == null || line == null) {
            return false;
        }
        String lower = cleanLine(line).toLowerCase(Locale.ROOT);
        String playerName = client.player.getName().getString().toLowerCase(Locale.ROOT);
        return lower.contains(" entered the catacombs")
            && lower.contains(playerName);
    }

    private static String destinationFromCommand(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }

        String clean = command.startsWith("/") ? command.substring(1) : command;
        String lower = cleanLine(clean).toLowerCase(Locale.ROOT);
        String[] parts = lower.split("\\s+");
        if (parts.length == 0) {
            return "";
        }

        return switch (parts[0]) {
            case "is", "island", "home" -> destinationFromIslandCommand(parts);
            case "warp" -> destinationFromWarpCommand(parts);
            case "hub", "lobby", "skyblock" -> "Hub";
            case "dh", "dungeonhub", "dungeon_hub" -> "Dungeon Hub";
            case "warpforge" -> "Dwarven Mines";
            default -> "";
        };
    }

    private static String destinationFromIslandCommand(String[] parts) {
        if (parts.length <= 1) {
            return "Private Island";
        }
        return switch (parts[1]) {
            case "home", "island" -> "Private Island";
            case "hub" -> "Hub";
            default -> "Private Island";
        };
    }

    private static String destinationFromWarpCommand(String[] parts) {
        if (parts.length <= 1) {
            return "";
        }

        return switch (parts[1]) {
            case "home", "island" -> "Private Island";
            case "hub" -> "Hub";
            case "dungeon_hub", "dungeonhub", "dh" -> "Dungeon Hub";
            case "forge" -> "Dwarven Mines";
            case "garden" -> "The Garden";
            case "crimson", "crimson_isle" -> "Crimson Isle";
            default -> "";
        };
    }

    private static boolean isOtherPlayerLocationLine(String lowerLine) {
        return lowerLine.startsWith("friends ")
            || lowerLine.contains(" is in ")
            || isOtherPlayerTravelLine(lowerLine)
            || lowerLine.contains(" entered ")
            || lowerLine.contains(" left ");
    }

    private static boolean isOtherPlayerTravelLine(String lowerLine) {
        return lowerLine.contains(" is traveling to ")
            || lowerLine.contains(" is travelling to ");
    }

    private static boolean isKungMessage(String line) {
        String clean = cleanLine(line);
        return clean.startsWith("[Kung") || clean.startsWith("Kung ");
    }

    private static String cleanLine(String rawLine) {
        return rawLine.replaceAll("\u00a7.", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String summarizeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(lines.size(), 16);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(KungDebugRecorder.compact(lines.get(index)));
        }
        if (lines.size() > limit) {
            builder.append(" | +").append(lines.size() - limit).append(" more");
        }
        return builder.append(']').toString();
    }

    private static String compactComponent(Component component) {
        return component == null ? "<null>" : KungDebugRecorder.compact(component.getString());
    }

    private static String identity(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "?" : KungDebugRecorder.compact(value);
    }
}
