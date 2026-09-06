package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.feature.dungeon.DungeonMapOverlayConfig;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class LobbyHopHelperFeature {
    private static final int MAX_HISTORY_SIZE = 128;

    private static final Set<String> seenLobbyIds = new LinkedHashSet<>();
    private static String currentLobbyId = "";
    private static boolean wasEnabled;

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

        if (client.level == null
            || client.player == null
            || !HypixelInstanceTracker.INSTANCE.tracking()) {
            currentLobbyId = "";
            return;
        }

        String lobbyId = HypixelInstanceTracker.INSTANCE.serverId();
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

    private static void addLobbyId(String lobbyId) {
        seenLobbyIds.add(lobbyId);
        while (seenLobbyIds.size() > MAX_HISTORY_SIZE) {
            String oldest = seenLobbyIds.iterator().next();
            seenLobbyIds.remove(oldest);
        }
    }

    private static void alert(Minecraft client) {
        client.gui.setTitle(Component.literal("Swap Lobbies"));
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.35F);
    }
}
