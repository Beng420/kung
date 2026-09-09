package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.message.HypixelChatSender;
import com.github.beng420.kung.message.KungMessages;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class DungeonAnnouncements {
    private static final long PARTY_COOLDOWN_MILLIS = 300L;

    private final Queue<String> pendingPartyMessages = new ArrayDeque<>();
    private final HypixelChatSender chatSender;
    private long lastPartyMessageMillis;

    public DungeonAnnouncements() {
        this(HypixelChatSender.INSTANCE);
    }

    DungeonAnnouncements(HypixelChatSender chatSender) {
        this.chatSender = java.util.Objects.requireNonNull(chatSender, "chatSender");
    }

    public void reset() {
        pendingPartyMessages.clear();
        lastPartyMessageMillis = 0L;
    }

    public void announceDeath(
        Minecraft client,
        DungeonConfig config,
        DungeonPlayerStats stats,
        int totalDeaths
    ) {
        if (!config.deathMessagesEnabled()) {
            return;
        }
        String individualText = stats.name()
            + " died "
            + stats.deaths()
            + " "
            + (stats.deaths() == 1 ? "time" : "times");
        String eventText = individualText + " | Total Deaths: " + totalDeaths;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(KungMessages.info("Dungeon", eventText));
        }
        String partyMessage = deathPartyMessage(
            individualText,
            eventText,
            totalDeaths,
            config.deathMessagesShareTotalEnabled(),
            config.deathMessagesShareIndividualEnabled()
        );
        if (!partyMessage.isBlank()) {
            sendPartyAfterCooldown(client, partyMessage);
        }
    }

    static String deathPartyMessage(
        String individualText,
        String eventText,
        int totalDeaths,
        boolean shareTotal,
        boolean shareIndividual
    ) {
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

    public void sendPartyAfterCooldown(Minecraft client, String message) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingPartyMessages.isEmpty() && now - lastPartyMessageMillis >= PARTY_COOLDOWN_MILLIS) {
            chatSender.sendParty(client, message);
            lastPartyMessageMillis = now;
            return;
        }
        pendingPartyMessages.add(message);
    }

    public void flush(Minecraft client) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        long now = System.currentTimeMillis();
        while (!pendingPartyMessages.isEmpty() && now - lastPartyMessageMillis >= PARTY_COOLDOWN_MILLIS) {
            chatSender.sendParty(client, pendingPartyMessages.remove());
            lastPartyMessageMillis = now;
        }
    }

    public static void showFiveCryptTitle(Minecraft client) {
        if (client == null || client.gui == null || client.player == null) {
            return;
        }
        client.gui.setTimes(0, 30, 5);
        client.gui.setTitle(Component.literal("5 Crypts").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.35F);
    }
}
