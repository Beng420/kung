package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.feature.dungeon.DungeonMapOverlayConfig;
import com.github.beng420.kung.skyblock.HypixelServerMatcher;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class LobbyHopHelperFeature {
    private static final Pattern LOBBY_ID_PATTERN =
        Pattern.compile("\\b(?:mini\\d{1,5}|mega\\d{1,5}|m\\d{2,5})[a-z]?\\b", Pattern.CASE_INSENSITIVE);
    private static final int MAX_HISTORY_SIZE = 128;

    private static final Set<String> seenLobbyIds = new LinkedHashSet<>();
    private static String currentLobbyId = "";
    private static boolean wasEnabled;
    private static Field tabHeaderField;
    private static Field tabFooterField;

    private LobbyHopHelperFeature() {
    }

    public static void initializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(LobbyHopHelperFeature::tick);
    }

    private static void tick(Minecraft client) {
        boolean enabled = DungeonMapOverlayConfig.INSTANCE.lobbyHopHelperEnabled();
        if (!enabled) {
            if (wasEnabled) {
                clearHistory();
            }
            wasEnabled = false;
            return;
        }

        if (!wasEnabled) {
            clearHistory();
            wasEnabled = true;
        }

        if (client.level == null || client.player == null || !isConnectedToHypixel(client)) {
            currentLobbyId = "";
            return;
        }

        String lobbyId = detectLobbyId(client);
        if (lobbyId.isBlank() || lobbyId.equals(currentLobbyId)) {
            return;
        }

        boolean seenBefore = seenLobbyIds.contains(lobbyId);
        addLobbyId(lobbyId);
        currentLobbyId = lobbyId;
        if (seenBefore) {
            alert(client);
        }
    }

    private static void clearHistory() {
        seenLobbyIds.clear();
        currentLobbyId = "";
    }

    private static boolean isConnectedToHypixel(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return server != null && HypixelServerMatcher.isHypixelAddress(stripPort(server.ip));
    }

    private static String stripPort(String address) {
        if (address == null) {
            return "";
        }

        int portSeparator = address.lastIndexOf(':');
        return portSeparator >= 0 ? address.substring(0, portSeparator) : address;
    }

    private static String detectLobbyId(Minecraft client) {
        for (String line : visibleHypixelLines(client)) {
            String lobbyId = lobbyIdFrom(line);
            if (!lobbyId.isBlank()) {
                return lobbyId;
            }
        }
        return "";
    }

    private static List<String> visibleHypixelLines(Minecraft client) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.addAll(sidebarLines(client));
        addComponentLines(lines, readTabComponent(client, true));
        addComponentLines(lines, readTabComponent(client, false));
        return lines;
    }

    private static List<String> sidebarLines(Minecraft client) {
        if (client.level == null) {
            return List.of();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
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
            PlayerTabOverlay tabList = client.gui.hud.getTabList();
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

    private static String lobbyIdFrom(String rawLine) {
        String line = rawLine.replaceAll("Â§.", "").replaceAll("\\s+", " ").trim();
        Matcher matcher = LOBBY_ID_PATTERN.matcher(line);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : "";
    }

    private static void addLobbyId(String lobbyId) {
        seenLobbyIds.add(lobbyId);
        while (seenLobbyIds.size() > MAX_HISTORY_SIZE) {
            String oldest = seenLobbyIds.iterator().next();
            seenLobbyIds.remove(oldest);
        }
    }

    private static void alert(Minecraft client) {
        client.gui.hud.setTitle(Component.literal("Swap Lobbies"));
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.35F);
    }
}
