package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.message.KungMessages;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import java.util.LinkedHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class LobbyHopHelperFeature extends ConfigurableFeature<MiscConfig> {
    public static final LobbyHopHelperFeature INSTANCE = new LobbyHopHelperFeature();
    private static final int MAX_HISTORY_SIZE = 128;

    private static final LinkedHashMap<String, Long> lastSeenLobbyMillis = new LinkedHashMap<>();
    private static String currentLobbyId = "";
    private static boolean wasEnabled;

    private LobbyHopHelperFeature() {
        super(config -> config.misc);
    }

    @Override
    protected void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(LobbyHopHelperFeature::tick);
    }

    @Override
    public boolean isEnabled() {
        return config().lobbyHopHelperEnabled();
    }

    private static void tick(Minecraft client) {
        boolean enabled = INSTANCE.isEnabled();
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
        long elapsedMillis = recordVisit(lobbyId, System.nanoTime() / 1_000_000L);
        if (elapsedMillis >= 0L) {
            alert(client, lobbyId, elapsedMillis);
        }
    }

    static void clearHistory() {
        lastSeenLobbyMillis.clear();
        currentLobbyId = "";
    }

    /** Returns time since last presence, or -1 when no revisit should be announced. */
    static long recordVisit(String lobbyId, long nowMillis) {
        if (lobbyId.isBlank()) return -1L;
        // Refresh while present so a long stay does not count toward time spent away.
        Long previousVisit = lastSeenLobbyMillis.put(lobbyId, nowMillis);
        if (lobbyId.equals(currentLobbyId)) return -1L;
        currentLobbyId = lobbyId;
        if (lastSeenLobbyMillis.size() > MAX_HISTORY_SIZE) {
            lastSeenLobbyMillis.pollFirstEntry();
        }
        return previousVisit == null ? -1L : Math.max(0L, nowMillis - previousVisit);
    }

    static String formatAgo(long elapsedMillis) {
        long seconds = Math.max(0L, elapsedMillis) / 1_000L;
        return (seconds >= 60L ? seconds / 60L + "m" : "") + seconds % 60L + "s ago";
    }

    private static void alert(Minecraft client, String lobbyId, long elapsedMillis) {
        String ago = formatAgo(elapsedMillis);
        client.gui.setSubtitle(Component.literal(lobbyId + " - " + ago));
        client.gui.setTitle(Component.literal("Swap Lobbies"));
        KungMessages.send(client, KungMessages.Type.WARNING, "",
            "You've been on lobby " + lobbyId + " before! " + ago + ".");
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.35F, 1.0F));
    }
}
