package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.util.KungDebugRecorder;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DungeonChatFilterFeature {
    private static final String BOSS_PREFIX = "[BOSS] ";

    private DungeonChatFilterFeature() {
    }

    public static Feature definition() {
        return new Feature(
            "dungeon-chat-filter",
            "Dungeon chat filter",
            false,
            "Hides selected Dungeon chat spam."
        );
    }

    public static void initializeClient(DungeonStateTracker tracker) {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            allowMessage(tracker, message, false));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
            allowMessage(tracker, message, overlay));
    }

    private static boolean allowMessage(DungeonStateTracker tracker, Component message, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (!shouldHide(tracker, client, message)) {
            return true;
        }

        tracker.observeSuppressedMessage(client, message.getString(), overlay);
        KungDebugRecorder.event("chat-filter", "hidden " + KungDebugRecorder.compact(message.getString()));
        return false;
    }

    private static boolean shouldHide(DungeonStateTracker tracker, Minecraft client, Component component) {
        if (tracker == null || component == null) {
            return false;
        }

        DungeonMapOverlayConfig config = DungeonMapOverlayConfig.INSTANCE;
        if (!config.dungeonChatFilterEnabled() || !tracker.canFilterDungeonChat(client)) {
            return false;
        }

        String message = clean(component.getString());
        if (message.isEmpty() || message.startsWith("[Kung")) {
            return false;
        }

        if (config.dungeonChatFilterBlessings() && isBlessingMessage(message)) {
            return true;
        }
        if (config.dungeonChatFilterLootSpam() && isLootSpamMessage(message)) {
            return true;
        }
        if (config.dungeonChatFilterWatcher() && isBossMessageFrom(message, "the watcher")) {
            return true;
        }
        return config.dungeonChatFilterBossMessages() && bossMessageEnabled(config, bossName(message));
    }

    private static boolean isBlessingMessage(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("dungeon buff!") && lower.contains("blessing of ")
            || lower.contains(" has obtained blessing of ")
            || lower.contains(" found a blessing of ")
            || lower.contains("a blessing of ") && lower.contains(" was picked up")
            || lower.startsWith("granted you +")
            || lower.contains(" granted you +")
            || lower.startsWith("also granted you +")
            || lower.contains(" also granted you +");
    }

    private static boolean isLootSpamMessage(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains(" has obtained superboom tnt")
            || lower.contains("you picked up a defense orb")
            || lower.contains(" found a wither essence") && lower.contains("everyone gains an extra essence")
            || lower.contains(" essence! you found ");
    }

    private static boolean isBossMessageFrom(String message, String expectedBoss) {
        return expectedBoss.equals(bossName(message));
    }

    private static String bossName(String message) {
        if (!message.startsWith(BOSS_PREFIX)) {
            return "";
        }
        int end = message.indexOf(':', BOSS_PREFIX.length());
        if (end <= BOSS_PREFIX.length()) {
            return "";
        }
        return message.substring(BOSS_PREFIX.length(), end)
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean bossMessageEnabled(DungeonMapOverlayConfig config, String bossName) {
        return switch (bossName) {
            case "bonzo" -> config.dungeonChatFilterBonzo();
            case "scarf" -> config.dungeonChatFilterScarf();
            case "the professor", "professor" -> config.dungeonChatFilterProfessor();
            case "thorn" -> config.dungeonChatFilterThorn();
            case "livid" -> config.dungeonChatFilterLivid();
            case "sadan" -> config.dungeonChatFilterSadan();
            case "maxor" -> config.dungeonChatFilterMaxor();
            case "storm" -> config.dungeonChatFilterStorm();
            case "goldor" -> config.dungeonChatFilterGoldor();
            case "necron" -> config.dungeonChatFilterNecron();
            case "the wither king", "wither king" -> config.dungeonChatFilterWitherKing();
            default -> false;
        };
    }

    private static String clean(String message) {
        return message == null
            ? ""
            : message.replaceAll("\u00a7.", "").replaceAll("\\s+", " ").trim();
    }
}
