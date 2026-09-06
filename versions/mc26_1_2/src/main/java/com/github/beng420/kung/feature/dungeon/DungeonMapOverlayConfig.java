package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class DungeonMapOverlayConfig {
    public static final DungeonMapOverlayConfig INSTANCE = new DungeonMapOverlayConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULTS_RESOURCE = "/kung-defaults.properties";

    private boolean enabled = false;
    private int x = 8;
    private int y = 8;
    private int scale = 100;
    private int unopenedRoomAlpha = 12;
    private boolean showHeader = true;
    private boolean showLegend = false;
    private boolean princeIconsEnabled = true;
    private boolean forcePaulScoreEnabled = false;
    private boolean playerTrackingEnabled = false;
    private boolean deathMessagesEnabled = false;
    private boolean deathMessagesShareTotalEnabled = false;
    private boolean deathMessagesShareIndividualEnabled = false;
    private boolean debugMessagesEnabled = false;
    private boolean debugContextMessages = true;
    private boolean debugMessages = true;
    private boolean debugRoomCrypts = false;
    private boolean debugRoomMatches = false;
    private boolean localRoomDataEnabled = false;
    private boolean bloodRushHelperEnabled = false;
    private int bloodRushHelperTitleDurationTenths = 5;
    private boolean fiveCryptPartyMessageEnabled = false;
    private String fiveCryptPartyMessage = "We got all 5 crypts (✿◠‿◠)";
    private boolean cryptProgressPartyMessageEnabled = false;
    private boolean fiveCryptTitleEnabled = false;
    private boolean dungeonChatFilterEnabled = false;
    private boolean dungeonChatFilterBlessings = true;
    private boolean dungeonChatFilterLootSpam = true;
    private boolean dungeonChatFilterWatcher = true;
    private boolean dungeonChatFilterBossMessages = false;
    private boolean dungeonChatFilterBonzo = true;
    private boolean dungeonChatFilterScarf = true;
    private boolean dungeonChatFilterProfessor = true;
    private boolean dungeonChatFilterThorn = true;
    private boolean dungeonChatFilterLivid = true;
    private boolean dungeonChatFilterSadan = true;
    private boolean dungeonChatFilterMaxor = true;
    private boolean dungeonChatFilterStorm = true;
    private boolean dungeonChatFilterGoldor = true;
    private boolean dungeonChatFilterNecron = true;
    private boolean dungeonChatFilterWitherKing = true;
    private boolean roomSyncEnabled = false;
    private boolean roomSyncUploadEnabled = false;
    private String roomSyncServerUrl = "";
    private String roomSyncToken = "";
    private boolean splitsEnabled = false;
    private int splitsX = 226;
    private int splitsY = 8;
    private int splitsScale = 85;
    private boolean debugInterfaceMessages = true;
    private boolean lobbyHopHelperEnabled = false;
    private boolean hypixelApiEnabled = false;
    private String hypixelApiKey = "";
    private boolean chatCommandsEnabled = false;
    private boolean ca50ChatCommandEnabled = true;
    private boolean ca50PartyCommandsEnabled = true;
    private boolean ca50GuildCommandsEnabled = true;
    private boolean ca50AllChatCommandsEnabled = false;
    private boolean ca50PrivateCommandsEnabled = false;
    private boolean loadoutsAutoCloseEnabled = false;
    private String[] loadoutKeybinds = defaultLoadoutKeybinds();
    private boolean superpairsHelperEnabled = false;
    private boolean superpairsHelperDebugEnabled = false;
    private int superpairsHelperX = 6;
    private int superpairsHelperY = 34;
    private int superpairsHelperScale = 75;
    private boolean customSoundsEnabled = false;
    private String customArrowHitSounds = "kung_arrow_ping.wav";
    private int customArrowHitVolumeTenths = 10;
    private int customArrowHitPitchHundredths = 100;
    private String customWitherShieldExpireSounds = "kung_wither_fade.wav";
    private int customWitherShieldExpireVolumeTenths = 10;
    private int customWitherShieldExpirePitchHundredths = 100;
    private Map<String, Integer> customArrowHitSoundVolumeTenths = new HashMap<>();
    private Map<String, Integer> customArrowHitSoundPitchHundredths = new HashMap<>();
    private Map<String, Integer> customWitherShieldExpireSoundVolumeTenths = new HashMap<>();
    private Map<String, Integer> customWitherShieldExpireSoundPitchHundredths = new HashMap<>();
    private boolean tarantulaHelperEnabled = false;
    private boolean eggSacPredictionRendererEnabled = false;
    private boolean eggSacPredictionEnabled = false;
    private EggSacPredictionRenderMode eggSacPredictionRenderMode = EggSacPredictionRenderMode.BOX;
    private boolean tarantulaHelperDebugMessages = true;
    private boolean tarantulaDebugSlayerSpawned = true;
    private boolean tarantulaDebugSlayerPosition = true;
    private boolean tarantulaDebugSlayerPhaseChange = true;
    private boolean tarantulaDebugSlayerDead = true;
    private boolean tarantulaDebugEggSacPhaseStart = true;
    private boolean tarantulaDebugEggSacPhaseDone = true;
    private boolean tarantulaDebugEggSacLearning = true;
    private boolean loading;

    private DungeonMapOverlayConfig() {
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public int x() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
        save();
    }

    public int y() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
        save();
    }

    public int scale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = Math.clamp(scale, 25, 150);
        save();
    }

    public int unopenedRoomAlpha() {
        return unopenedRoomAlpha;
    }

    public void setUnopenedRoomAlpha(int unopenedRoomAlpha) {
        this.unopenedRoomAlpha = Math.clamp(unopenedRoomAlpha, 0, 28);
        save();
    }

    public boolean showHeader() {
        return showHeader;
    }

    public void setShowHeader(boolean showHeader) {
        this.showHeader = showHeader;
        save();
    }

    public boolean showLegend() {
        return showLegend;
    }

    public void setShowLegend(boolean showLegend) {
        this.showLegend = showLegend;
        save();
    }

    public boolean princeIconsEnabled() {
        return princeIconsEnabled;
    }

    public void setPrinceIconsEnabled(boolean princeIconsEnabled) {
        this.princeIconsEnabled = princeIconsEnabled;
        save();
    }

    public boolean forcePaulScoreEnabled() {
        return forcePaulScoreEnabled;
    }

    public void setForcePaulScoreEnabled(boolean forcePaulScoreEnabled) {
        this.forcePaulScoreEnabled = forcePaulScoreEnabled;
        save();
    }

    public boolean playerTrackingEnabled() {
        return playerTrackingEnabled;
    }

    public void setPlayerTrackingEnabled(boolean playerTrackingEnabled) {
        this.playerTrackingEnabled = playerTrackingEnabled;
        save();
    }

    public boolean deathMessagesEnabled() {
        return deathMessagesEnabled;
    }

    public void setDeathMessagesEnabled(boolean deathMessagesEnabled) {
        this.deathMessagesEnabled = deathMessagesEnabled;
        save();
    }

    public boolean deathMessagesShareTotalEnabled() {
        return deathMessagesShareTotalEnabled;
    }

    public void setDeathMessagesShareTotalEnabled(boolean deathMessagesShareTotalEnabled) {
        this.deathMessagesShareTotalEnabled = deathMessagesShareTotalEnabled;
        save();
    }

    public boolean deathMessagesShareIndividualEnabled() {
        return deathMessagesShareIndividualEnabled;
    }

    public void setDeathMessagesShareIndividualEnabled(boolean deathMessagesShareIndividualEnabled) {
        this.deathMessagesShareIndividualEnabled = deathMessagesShareIndividualEnabled;
        save();
    }

    public boolean debugMessagesEnabled() {
        return debugMessagesEnabled;
    }

    public void setDebugMessagesEnabled(boolean debugMessagesEnabled) {
        this.debugMessagesEnabled = debugMessagesEnabled;
        save();
    }

    public boolean debugContextMessages() {
        return debugContextMessages;
    }

    public void setDebugContextMessages(boolean debugContextMessages) {
        this.debugContextMessages = debugContextMessages;
        save();
    }

    public boolean contextDebugMessagesEnabled() {
        return debugMessagesEnabled && debugContextMessages;
    }

    public boolean debugMessages() {
        return debugMessages;
    }

    public void setDebugMessages(boolean debugMessages) {
        this.debugMessages = debugMessages;
        save();
    }

    public boolean dungeonDebugMessagesEnabled() {
        return debugMessagesEnabled && debugMessages;
    }

    public boolean debugRoomCrypts() {
        return debugRoomCrypts;
    }

    public void setDebugRoomCrypts(boolean debugRoomCrypts) {
        this.debugRoomCrypts = debugRoomCrypts;
        save();
    }

    public boolean debugRoomMatches() {
        return debugRoomMatches;
    }

    public void setDebugRoomMatches(boolean debugRoomMatches) {
        this.debugRoomMatches = debugRoomMatches;
        save();
    }

    public boolean localRoomDataEnabled() {
        return localRoomDataEnabled;
    }

    public void setLocalRoomDataEnabled(boolean localRoomDataEnabled) {
        this.localRoomDataEnabled = localRoomDataEnabled;
        save();
    }

    public boolean bloodRushHelperEnabled() {
        return bloodRushHelperEnabled;
    }

    public void setBloodRushHelperEnabled(boolean bloodRushHelperEnabled) {
        this.bloodRushHelperEnabled = bloodRushHelperEnabled;
        save();
    }

    public int bloodRushHelperTitleDurationTenths() {
        return bloodRushHelperTitleDurationTenths;
    }

    public void setBloodRushHelperTitleDurationTenths(int bloodRushHelperTitleDurationTenths) {
        this.bloodRushHelperTitleDurationTenths = Math.clamp(bloodRushHelperTitleDurationTenths, 1, 50);
        save();
    }

    public boolean fiveCryptPartyMessageEnabled() {
        return fiveCryptPartyMessageEnabled;
    }

    public void setFiveCryptPartyMessageEnabled(boolean fiveCryptPartyMessageEnabled) {
        this.fiveCryptPartyMessageEnabled = fiveCryptPartyMessageEnabled;
        save();
    }

    public String fiveCryptPartyMessage() {
        return fiveCryptPartyMessage == null || fiveCryptPartyMessage.isBlank()
            ? "We got all 5 crypts (✿◠‿◠)"
            : fiveCryptPartyMessage;
    }

    public void setFiveCryptPartyMessage(String fiveCryptPartyMessage) {
        String normalized = fiveCryptPartyMessage == null ? "" : fiveCryptPartyMessage.strip();
        if (normalized.startsWith("[Kung]")) {
            normalized = normalized.substring("[Kung]".length()).strip();
        }
        this.fiveCryptPartyMessage = normalized.isBlank()
            ? "We got all 5 crypts (✿◠‿◠)"
            : normalized;
        save();
    }

    public boolean cryptProgressPartyMessageEnabled() {
        return cryptProgressPartyMessageEnabled;
    }

    public void setCryptProgressPartyMessageEnabled(boolean cryptProgressPartyMessageEnabled) {
        this.cryptProgressPartyMessageEnabled = cryptProgressPartyMessageEnabled;
        save();
    }

    public boolean fiveCryptTitleEnabled() {
        return fiveCryptTitleEnabled;
    }

    public void setFiveCryptTitleEnabled(boolean fiveCryptTitleEnabled) {
        this.fiveCryptTitleEnabled = fiveCryptTitleEnabled;
        save();
    }

    public boolean dungeonChatFilterEnabled() {
        return dungeonChatFilterEnabled;
    }

    public void setDungeonChatFilterEnabled(boolean dungeonChatFilterEnabled) {
        this.dungeonChatFilterEnabled = dungeonChatFilterEnabled;
        save();
    }

    public boolean dungeonChatFilterBlessings() {
        return dungeonChatFilterBlessings;
    }

    public void setDungeonChatFilterBlessings(boolean dungeonChatFilterBlessings) {
        this.dungeonChatFilterBlessings = dungeonChatFilterBlessings;
        save();
    }

    public boolean dungeonChatFilterLootSpam() {
        return dungeonChatFilterLootSpam;
    }

    public void setDungeonChatFilterLootSpam(boolean dungeonChatFilterLootSpam) {
        this.dungeonChatFilterLootSpam = dungeonChatFilterLootSpam;
        save();
    }

    public boolean dungeonChatFilterWatcher() {
        return dungeonChatFilterWatcher;
    }

    public void setDungeonChatFilterWatcher(boolean dungeonChatFilterWatcher) {
        this.dungeonChatFilterWatcher = dungeonChatFilterWatcher;
        save();
    }

    public boolean dungeonChatFilterBossMessages() {
        return dungeonChatFilterBossMessages;
    }

    public void setDungeonChatFilterBossMessages(boolean dungeonChatFilterBossMessages) {
        this.dungeonChatFilterBossMessages = dungeonChatFilterBossMessages;
        save();
    }

    public boolean dungeonChatFilterBonzo() {
        return dungeonChatFilterBonzo;
    }

    public void setDungeonChatFilterBonzo(boolean dungeonChatFilterBonzo) {
        this.dungeonChatFilterBonzo = dungeonChatFilterBonzo;
        save();
    }

    public boolean dungeonChatFilterScarf() {
        return dungeonChatFilterScarf;
    }

    public void setDungeonChatFilterScarf(boolean dungeonChatFilterScarf) {
        this.dungeonChatFilterScarf = dungeonChatFilterScarf;
        save();
    }

    public boolean dungeonChatFilterProfessor() {
        return dungeonChatFilterProfessor;
    }

    public void setDungeonChatFilterProfessor(boolean dungeonChatFilterProfessor) {
        this.dungeonChatFilterProfessor = dungeonChatFilterProfessor;
        save();
    }

    public boolean dungeonChatFilterThorn() {
        return dungeonChatFilterThorn;
    }

    public void setDungeonChatFilterThorn(boolean dungeonChatFilterThorn) {
        this.dungeonChatFilterThorn = dungeonChatFilterThorn;
        save();
    }

    public boolean dungeonChatFilterLivid() {
        return dungeonChatFilterLivid;
    }

    public void setDungeonChatFilterLivid(boolean dungeonChatFilterLivid) {
        this.dungeonChatFilterLivid = dungeonChatFilterLivid;
        save();
    }

    public boolean dungeonChatFilterSadan() {
        return dungeonChatFilterSadan;
    }

    public void setDungeonChatFilterSadan(boolean dungeonChatFilterSadan) {
        this.dungeonChatFilterSadan = dungeonChatFilterSadan;
        save();
    }

    public boolean dungeonChatFilterMaxor() {
        return dungeonChatFilterMaxor;
    }

    public void setDungeonChatFilterMaxor(boolean dungeonChatFilterMaxor) {
        this.dungeonChatFilterMaxor = dungeonChatFilterMaxor;
        save();
    }

    public boolean dungeonChatFilterStorm() {
        return dungeonChatFilterStorm;
    }

    public void setDungeonChatFilterStorm(boolean dungeonChatFilterStorm) {
        this.dungeonChatFilterStorm = dungeonChatFilterStorm;
        save();
    }

    public boolean dungeonChatFilterGoldor() {
        return dungeonChatFilterGoldor;
    }

    public void setDungeonChatFilterGoldor(boolean dungeonChatFilterGoldor) {
        this.dungeonChatFilterGoldor = dungeonChatFilterGoldor;
        save();
    }

    public boolean dungeonChatFilterNecron() {
        return dungeonChatFilterNecron;
    }

    public void setDungeonChatFilterNecron(boolean dungeonChatFilterNecron) {
        this.dungeonChatFilterNecron = dungeonChatFilterNecron;
        save();
    }

    public boolean dungeonChatFilterWitherKing() {
        return dungeonChatFilterWitherKing;
    }

    public void setDungeonChatFilterWitherKing(boolean dungeonChatFilterWitherKing) {
        this.dungeonChatFilterWitherKing = dungeonChatFilterWitherKing;
        save();
    }

    public boolean roomSyncEnabled() {
        return roomSyncEnabled;
    }

    public void setRoomSyncEnabled(boolean roomSyncEnabled) {
        this.roomSyncEnabled = roomSyncEnabled;
        save();
    }

    public boolean roomSyncUploadEnabled() {
        return roomSyncUploadEnabled;
    }

    public void setRoomSyncUploadEnabled(boolean roomSyncUploadEnabled) {
        this.roomSyncUploadEnabled = roomSyncUploadEnabled;
        save();
    }

    public String roomSyncServerUrl() {
        return roomSyncServerUrl == null ? "" : roomSyncServerUrl;
    }

    public void setRoomSyncServerUrl(String roomSyncServerUrl) {
        this.roomSyncServerUrl = normalizeRoomSyncServerUrl(roomSyncServerUrl);
        save();
    }

    public String roomSyncToken() {
        return roomSyncToken == null ? "" : roomSyncToken;
    }

    public void setRoomSyncToken(String roomSyncToken) {
        this.roomSyncToken = roomSyncToken == null ? "" : roomSyncToken.trim();
        save();
    }

    public boolean splitsEnabled() {
        return splitsEnabled;
    }

    public void setSplitsEnabled(boolean splitsEnabled) {
        this.splitsEnabled = splitsEnabled;
        save();
    }

    public int splitsX() {
        return splitsX;
    }

    public void setSplitsX(int splitsX) {
        this.splitsX = splitsX;
        save();
    }

    public int splitsY() {
        return splitsY;
    }

    public void setSplitsY(int splitsY) {
        this.splitsY = splitsY;
        save();
    }

    public int splitsScale() {
        return splitsScale;
    }

    public void setSplitsScale(int splitsScale) {
        this.splitsScale = Math.clamp(splitsScale, 25, 300);
        save();
    }

    public boolean debugInterfaceMessages() {
        return debugInterfaceMessages;
    }

    public void setDebugInterfaceMessages(boolean debugInterfaceMessages) {
        this.debugInterfaceMessages = debugInterfaceMessages;
        save();
    }

    public boolean interfaceDebugMessagesEnabled() {
        return debugMessagesEnabled && debugInterfaceMessages;
    }

    public boolean lobbyHopHelperEnabled() {
        return lobbyHopHelperEnabled;
    }

    public void setLobbyHopHelperEnabled(boolean lobbyHopHelperEnabled) {
        this.lobbyHopHelperEnabled = lobbyHopHelperEnabled;
        save();
    }

    public boolean hypixelApiEnabled() {
        return hypixelApiEnabled;
    }

    public void setHypixelApiEnabled(boolean hypixelApiEnabled) {
        this.hypixelApiEnabled = hypixelApiEnabled;
        save();
    }

    public boolean directHypixelApiEnabled() {
        return hypixelApiEnabled && !hypixelApiKey().isBlank();
    }

    public String hypixelApiKey() {
        return hypixelApiKey == null ? "" : hypixelApiKey;
    }

    public String hypixelApiKeyDisplay() {
        String key = hypixelApiKey();
        if (key.isBlank()) {
            return "Not set";
        }
        if (key.length() <= 8) {
            return "Set";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    public void setHypixelApiKey(String hypixelApiKey) {
        this.hypixelApiKey = hypixelApiKey == null ? "" : hypixelApiKey.trim();
        save();
    }

    public boolean chatCommandsEnabled() {
        return chatCommandsEnabled;
    }

    public void setChatCommandsEnabled(boolean chatCommandsEnabled) {
        this.chatCommandsEnabled = chatCommandsEnabled;
        save();
    }

    public boolean ca50ChatCommandEnabled() {
        return ca50ChatCommandEnabled;
    }

    public void setCa50ChatCommandEnabled(boolean ca50ChatCommandEnabled) {
        this.ca50ChatCommandEnabled = ca50ChatCommandEnabled;
        save();
    }

    public boolean ca50PartyCommandsEnabled() {
        return ca50PartyCommandsEnabled;
    }

    public void setCa50PartyCommandsEnabled(boolean ca50PartyCommandsEnabled) {
        this.ca50PartyCommandsEnabled = ca50PartyCommandsEnabled;
        save();
    }

    public boolean ca50GuildCommandsEnabled() {
        return ca50GuildCommandsEnabled;
    }

    public void setCa50GuildCommandsEnabled(boolean ca50GuildCommandsEnabled) {
        this.ca50GuildCommandsEnabled = ca50GuildCommandsEnabled;
        save();
    }

    public boolean ca50AllChatCommandsEnabled() {
        return ca50AllChatCommandsEnabled;
    }

    public void setCa50AllChatCommandsEnabled(boolean ca50AllChatCommandsEnabled) {
        this.ca50AllChatCommandsEnabled = ca50AllChatCommandsEnabled;
        save();
    }

    public boolean ca50PrivateCommandsEnabled() {
        return ca50PrivateCommandsEnabled;
    }

    public void setCa50PrivateCommandsEnabled(boolean ca50PrivateCommandsEnabled) {
        this.ca50PrivateCommandsEnabled = ca50PrivateCommandsEnabled;
        save();
    }

    public boolean loadoutsAutoCloseEnabled() {
        return loadoutsAutoCloseEnabled;
    }

    public void setLoadoutsAutoCloseEnabled(boolean loadoutsAutoCloseEnabled) {
        this.loadoutsAutoCloseEnabled = loadoutsAutoCloseEnabled;
        save();
    }

    public String loadoutKeybind(int index) {
        if (index < 0 || index >= loadoutKeybinds.length) {
            return "";
        }
        String keybind = loadoutKeybinds[index];
        return keybind == null ? "" : keybind;
    }

    public void setLoadoutKeybind(int index, String keybind) {
        if (index < 0 || index >= loadoutKeybinds.length) {
            return;
        }
        loadoutKeybinds[index] = keybind == null ? "" : keybind;
        save();
    }

    public boolean superpairsHelperEnabled() {
        return superpairsHelperEnabled;
    }

    public void setSuperpairsHelperEnabled(boolean superpairsHelperEnabled) {
        this.superpairsHelperEnabled = superpairsHelperEnabled;
        save();
    }

    public boolean superpairsHelperDebugEnabled() {
        return superpairsHelperDebugEnabled;
    }

    public void setSuperpairsHelperDebugEnabled(boolean superpairsHelperDebugEnabled) {
        this.superpairsHelperDebugEnabled = superpairsHelperDebugEnabled;
        save();
    }

    public int superpairsHelperX() {
        return superpairsHelperX;
    }

    public void setSuperpairsHelperX(int superpairsHelperX) {
        this.superpairsHelperX = superpairsHelperX;
        save();
    }

    public int superpairsHelperY() {
        return superpairsHelperY;
    }

    public void setSuperpairsHelperY(int superpairsHelperY) {
        this.superpairsHelperY = superpairsHelperY;
        save();
    }

    public int superpairsHelperScale() {
        return superpairsHelperScale;
    }

    public void setSuperpairsHelperScale(int superpairsHelperScale) {
        this.superpairsHelperScale = Math.clamp(superpairsHelperScale, 25, 300);
        save();
    }

    public boolean customSoundsEnabled() {
        return customSoundsEnabled;
    }

    public void setCustomSoundsEnabled(boolean customSoundsEnabled) {
        this.customSoundsEnabled = customSoundsEnabled;
        save();
    }

    public String customArrowHitSounds() {
        return customArrowHitSounds == null ? "" : customArrowHitSounds;
    }

    public void setCustomArrowHitSounds(String customArrowHitSounds) {
        this.customArrowHitSounds = normalizeCustomSoundList(customArrowHitSounds);
        save();
    }

    public int customArrowHitVolumeTenths() {
        return customArrowHitVolumeTenths;
    }

    public void setCustomArrowHitVolumeTenths(int customArrowHitVolumeTenths) {
        this.customArrowHitVolumeTenths = Math.clamp(customArrowHitVolumeTenths, 0, 50);
        save();
    }

    public int customArrowHitPitchHundredths() {
        return customArrowHitPitchHundredths;
    }

    public void setCustomArrowHitPitchHundredths(int customArrowHitPitchHundredths) {
        this.customArrowHitPitchHundredths = Math.clamp(customArrowHitPitchHundredths, 25, 300);
        save();
    }

    public String customWitherShieldExpireSounds() {
        return customWitherShieldExpireSounds == null ? "" : customWitherShieldExpireSounds;
    }

    public void setCustomWitherShieldExpireSounds(String customWitherShieldExpireSounds) {
        this.customWitherShieldExpireSounds = normalizeCustomSoundList(customWitherShieldExpireSounds);
        save();
    }

    public int customWitherShieldExpireVolumeTenths() {
        return customWitherShieldExpireVolumeTenths;
    }

    public void setCustomWitherShieldExpireVolumeTenths(int customWitherShieldExpireVolumeTenths) {
        this.customWitherShieldExpireVolumeTenths = Math.clamp(customWitherShieldExpireVolumeTenths, 0, 50);
        save();
    }

    public int customWitherShieldExpirePitchHundredths() {
        return customWitherShieldExpirePitchHundredths;
    }

    public void setCustomWitherShieldExpirePitchHundredths(int customWitherShieldExpirePitchHundredths) {
        this.customWitherShieldExpirePitchHundredths = Math.clamp(customWitherShieldExpirePitchHundredths, 25, 300);
        save();
    }

    public int customArrowHitSoundVolumeTenths(String soundName) {
        return customSoundValue(customArrowHitSoundVolumeTenths, soundName, customArrowHitVolumeTenths, 0, 50);
    }

    public void setCustomArrowHitSoundVolumeTenths(String soundName, int volumeTenths) {
        putCustomSoundValue(customArrowHitSoundVolumeTenths, soundName, volumeTenths, 0, 50);
        save();
    }

    public int customArrowHitSoundPitchHundredths(String soundName) {
        return customSoundValue(customArrowHitSoundPitchHundredths, soundName, customArrowHitPitchHundredths, 25, 300);
    }

    public void setCustomArrowHitSoundPitchHundredths(String soundName, int pitchHundredths) {
        putCustomSoundValue(customArrowHitSoundPitchHundredths, soundName, pitchHundredths, 25, 300);
        save();
    }

    public int customWitherShieldExpireSoundVolumeTenths(String soundName) {
        return customSoundValue(
            customWitherShieldExpireSoundVolumeTenths,
            soundName,
            customWitherShieldExpireVolumeTenths,
            0,
            50
        );
    }

    public void setCustomWitherShieldExpireSoundVolumeTenths(String soundName, int volumeTenths) {
        putCustomSoundValue(customWitherShieldExpireSoundVolumeTenths, soundName, volumeTenths, 0, 50);
        save();
    }

    public int customWitherShieldExpireSoundPitchHundredths(String soundName) {
        return customSoundValue(
            customWitherShieldExpireSoundPitchHundredths,
            soundName,
            customWitherShieldExpirePitchHundredths,
            25,
            300
        );
    }

    public void setCustomWitherShieldExpireSoundPitchHundredths(String soundName, int pitchHundredths) {
        putCustomSoundValue(customWitherShieldExpireSoundPitchHundredths, soundName, pitchHundredths, 25, 300);
        save();
    }

    public boolean tarantulaHelperEnabled() {
        return tarantulaHelperEnabled;
    }

    public void setTarantulaHelperEnabled(boolean tarantulaHelperEnabled) {
        this.tarantulaHelperEnabled = tarantulaHelperEnabled;
        save();
    }

    public boolean eggSacPredictionRendererEnabled() {
        return eggSacPredictionRendererEnabled;
    }

    public void setEggSacPredictionRendererEnabled(boolean eggSacPredictionRendererEnabled) {
        this.eggSacPredictionRendererEnabled = eggSacPredictionRendererEnabled;
        save();
    }

    public boolean eggSacPredictionEnabled() {
        return eggSacPredictionEnabled;
    }

    public void setEggSacPredictionEnabled(boolean eggSacPredictionEnabled) {
        this.eggSacPredictionEnabled = eggSacPredictionEnabled;
        save();
    }

    public EggSacPredictionRenderMode eggSacPredictionRenderMode() {
        return eggSacPredictionRenderMode;
    }

    public String eggSacPredictionRenderModeLabel() {
        return eggSacPredictionRenderMode.label();
    }

    public void cycleEggSacPredictionRenderMode() {
        setEggSacPredictionRenderMode(eggSacPredictionRenderMode.next());
    }

    public void setEggSacPredictionRenderMode(EggSacPredictionRenderMode eggSacPredictionRenderMode) {
        this.eggSacPredictionRenderMode = eggSacPredictionRenderMode == null
            ? EggSacPredictionRenderMode.BOX
            : eggSacPredictionRenderMode;
        save();
    }

    public boolean tarantulaHelperDebugMessages() {
        return tarantulaHelperDebugMessages;
    }

    public void setTarantulaHelperDebugMessages(boolean tarantulaHelperDebugMessages) {
        this.tarantulaHelperDebugMessages = tarantulaHelperDebugMessages;
        save();
    }

    public boolean tarantulaDebugMessagesEnabled() {
        return debugMessagesEnabled && tarantulaHelperDebugMessages;
    }

    public boolean tarantulaDebugSlayerSpawned() {
        return tarantulaDebugSlayerSpawned;
    }

    public void setTarantulaDebugSlayerSpawned(boolean tarantulaDebugSlayerSpawned) {
        this.tarantulaDebugSlayerSpawned = tarantulaDebugSlayerSpawned;
        save();
    }

    public boolean tarantulaDebugSlayerPosition() {
        return tarantulaDebugSlayerPosition;
    }

    public void setTarantulaDebugSlayerPosition(boolean tarantulaDebugSlayerPosition) {
        this.tarantulaDebugSlayerPosition = tarantulaDebugSlayerPosition;
        save();
    }

    public boolean tarantulaDebugSlayerPhaseChange() {
        return tarantulaDebugSlayerPhaseChange;
    }

    public void setTarantulaDebugSlayerPhaseChange(boolean tarantulaDebugSlayerPhaseChange) {
        this.tarantulaDebugSlayerPhaseChange = tarantulaDebugSlayerPhaseChange;
        save();
    }

    public boolean tarantulaDebugSlayerDead() {
        return tarantulaDebugSlayerDead;
    }

    public void setTarantulaDebugSlayerDead(boolean tarantulaDebugSlayerDead) {
        this.tarantulaDebugSlayerDead = tarantulaDebugSlayerDead;
        save();
    }

    public boolean tarantulaDebugEggSacPhaseStart() {
        return tarantulaDebugEggSacPhaseStart;
    }

    public void setTarantulaDebugEggSacPhaseStart(boolean tarantulaDebugEggSacPhaseStart) {
        this.tarantulaDebugEggSacPhaseStart = tarantulaDebugEggSacPhaseStart;
        save();
    }

    public boolean tarantulaDebugEggSacPhaseDone() {
        return tarantulaDebugEggSacPhaseDone;
    }

    public void setTarantulaDebugEggSacPhaseDone(boolean tarantulaDebugEggSacPhaseDone) {
        this.tarantulaDebugEggSacPhaseDone = tarantulaDebugEggSacPhaseDone;
        save();
    }

    public boolean tarantulaDebugEggSacLearning() {
        return tarantulaDebugEggSacLearning;
    }

    public void setTarantulaDebugEggSacLearning(boolean tarantulaDebugEggSacLearning) {
        this.tarantulaDebugEggSacLearning = tarantulaDebugEggSacLearning;
        save();
    }

    public void load() {
        Path file = configFile();
        boolean migrateLegacyConfig = false;
        if (!Files.exists(file) && Files.exists(legacyConfigFile())) {
            file = legacyConfigFile();
            migrateLegacyConfig = true;
        }
        if (!Files.exists(file)) {
            applyBundledDefaults();
            save();
            return;
        }

        loading = true;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SavedConfig saved = GSON.fromJson(reader, SavedConfig.class);
            if (saved != null && saved.dungeonMap != null) {
                setEnabled(saved.dungeonMap.enabled);
                setX(saved.dungeonMap.x);
                setY(saved.dungeonMap.y);
                setScale(saved.dungeonMap.scale);
                setUnopenedRoomAlpha(saved.dungeonMap.unopenedRoomAlpha > 0
                    ? saved.dungeonMap.unopenedRoomAlpha
                    : 12);
                setShowHeader(saved.dungeonMap.showHeader);
                setShowLegend(saved.dungeonMap.showLegend);
                if (saved.dungeonMap.princeIconsEnabled != null) {
                    setPrinceIconsEnabled(saved.dungeonMap.princeIconsEnabled);
                }
                if (saved.dungeonMap.forcePaulScoreEnabled != null) {
                    setForcePaulScoreEnabled(saved.dungeonMap.forcePaulScoreEnabled);
                }
                if (saved.dungeonMap.playerTrackingEnabled != null) {
                    setPlayerTrackingEnabled(saved.dungeonMap.playerTrackingEnabled);
                }
                if (saved.dungeonMap.deathMessagesEnabled != null) {
                    setDeathMessagesEnabled(saved.dungeonMap.deathMessagesEnabled);
                }
                if (saved.dungeonMap.deathMessagesShareTotalEnabled != null) {
                    setDeathMessagesShareTotalEnabled(saved.dungeonMap.deathMessagesShareTotalEnabled);
                }
                if (saved.dungeonMap.deathMessagesShareIndividualEnabled != null) {
                    setDeathMessagesShareIndividualEnabled(saved.dungeonMap.deathMessagesShareIndividualEnabled);
                }
                if (saved.dungeonMap.debugMessages != null) {
                    setDebugMessages(saved.dungeonMap.debugMessages);
                }
                if (saved.dungeonMap.debugRoomCrypts != null) {
                    setDebugRoomCrypts(saved.dungeonMap.debugRoomCrypts);
                }
                if (saved.dungeonMap.debugRoomMatches != null) {
                    setDebugRoomMatches(saved.dungeonMap.debugRoomMatches);
                }
                if (saved.dungeonMap.localRoomDataEnabled != null) {
                    setLocalRoomDataEnabled(saved.dungeonMap.localRoomDataEnabled);
                }
                if (saved.dungeonMap.fiveCryptPartyMessageEnabled != null) {
                    setFiveCryptPartyMessageEnabled(saved.dungeonMap.fiveCryptPartyMessageEnabled);
                }
                if (saved.dungeonMap.fiveCryptPartyMessage != null) {
                    setFiveCryptPartyMessage(saved.dungeonMap.fiveCryptPartyMessage);
                }
                if (saved.dungeonMap.cryptProgressPartyMessageEnabled != null) {
                    setCryptProgressPartyMessageEnabled(saved.dungeonMap.cryptProgressPartyMessageEnabled);
                }
                if (saved.dungeonMap.fiveCryptTitleEnabled != null) {
                    setFiveCryptTitleEnabled(saved.dungeonMap.fiveCryptTitleEnabled);
                }
                if (saved.dungeonMap.roomSyncEnabled != null) {
                    setRoomSyncEnabled(saved.dungeonMap.roomSyncEnabled);
                }
                if (saved.dungeonMap.roomSyncServerUrl != null) {
                    setRoomSyncServerUrl(saved.dungeonMap.roomSyncServerUrl);
                }
                if (saved.dungeonMap.roomSyncToken != null) {
                    setRoomSyncToken(saved.dungeonMap.roomSyncToken);
                }
                if (saved.dungeonMap.roomSyncUploadEnabled != null) {
                    setRoomSyncUploadEnabled(saved.dungeonMap.roomSyncUploadEnabled);
                }
            }
            if (saved != null && saved.bloodRushHelper != null) {
                if (saved.bloodRushHelper.enabled != null) {
                    setBloodRushHelperEnabled(saved.bloodRushHelper.enabled);
                }
                if (saved.bloodRushHelper.titleDurationTenths != null) {
                    setBloodRushHelperTitleDurationTenths(saved.bloodRushHelper.titleDurationTenths);
                }
            }
            if (saved != null && saved.dungeonChatFilter != null) {
                if (saved.dungeonChatFilter.enabled != null) {
                    setDungeonChatFilterEnabled(saved.dungeonChatFilter.enabled);
                }
                if (saved.dungeonChatFilter.blessings != null) {
                    setDungeonChatFilterBlessings(saved.dungeonChatFilter.blessings);
                }
                if (saved.dungeonChatFilter.lootSpam != null) {
                    setDungeonChatFilterLootSpam(saved.dungeonChatFilter.lootSpam);
                }
                if (saved.dungeonChatFilter.watcher != null) {
                    setDungeonChatFilterWatcher(saved.dungeonChatFilter.watcher);
                }
                if (saved.dungeonChatFilter.bossMessages != null) {
                    setDungeonChatFilterBossMessages(saved.dungeonChatFilter.bossMessages);
                }
                if (saved.dungeonChatFilter.bonzo != null) {
                    setDungeonChatFilterBonzo(saved.dungeonChatFilter.bonzo);
                }
                if (saved.dungeonChatFilter.scarf != null) {
                    setDungeonChatFilterScarf(saved.dungeonChatFilter.scarf);
                }
                if (saved.dungeonChatFilter.professor != null) {
                    setDungeonChatFilterProfessor(saved.dungeonChatFilter.professor);
                }
                if (saved.dungeonChatFilter.thorn != null) {
                    setDungeonChatFilterThorn(saved.dungeonChatFilter.thorn);
                }
                if (saved.dungeonChatFilter.livid != null) {
                    setDungeonChatFilterLivid(saved.dungeonChatFilter.livid);
                }
                if (saved.dungeonChatFilter.sadan != null) {
                    setDungeonChatFilterSadan(saved.dungeonChatFilter.sadan);
                }
                if (saved.dungeonChatFilter.maxor != null) {
                    setDungeonChatFilterMaxor(saved.dungeonChatFilter.maxor);
                }
                if (saved.dungeonChatFilter.storm != null) {
                    setDungeonChatFilterStorm(saved.dungeonChatFilter.storm);
                }
                if (saved.dungeonChatFilter.goldor != null) {
                    setDungeonChatFilterGoldor(saved.dungeonChatFilter.goldor);
                }
                if (saved.dungeonChatFilter.necron != null) {
                    setDungeonChatFilterNecron(saved.dungeonChatFilter.necron);
                }
                if (saved.dungeonChatFilter.witherKing != null) {
                    setDungeonChatFilterWitherKing(saved.dungeonChatFilter.witherKing);
                }
            }
            if (saved != null && saved.splitsOverlay != null) {
                setSplitsEnabled(saved.splitsOverlay.enabled);
                setSplitsX(saved.splitsOverlay.x);
                setSplitsY(saved.splitsOverlay.y);
                setSplitsScale(saved.splitsOverlay.scale <= 0 || saved.splitsOverlay.scale == 100
                    ? 85
                    : saved.splitsOverlay.scale);
            }
            if (saved != null && saved.debug != null) {
                if (saved.debug.enabled != null) {
                    setDebugMessagesEnabled(saved.debug.enabled);
                }
                if (saved.debug.contextMessages != null) {
                    setDebugContextMessages(saved.debug.contextMessages);
                }
                if (saved.debug.dungeonMessages != null) {
                    setDebugMessages(saved.debug.dungeonMessages);
                }
                if (saved.debug.interfaceMessages != null) {
                    setDebugInterfaceMessages(saved.debug.interfaceMessages);
                }
                if (saved.debug.tarantulaMessages != null) {
                    setTarantulaHelperDebugMessages(saved.debug.tarantulaMessages);
                }
            }
            if (saved != null && saved.misc != null) {
                if (saved.misc.lobbyHopHelperEnabled != null) {
                    setLobbyHopHelperEnabled(saved.misc.lobbyHopHelperEnabled);
                }
                if (saved.misc.hypixelApiKey != null) {
                    setHypixelApiKey(saved.misc.hypixelApiKey);
                }
                if (saved.misc.hypixelApiEnabled != null) {
                    setHypixelApiEnabled(saved.misc.hypixelApiEnabled);
                } else if (!hypixelApiKey().isBlank()) {
                    setHypixelApiEnabled(true);
                }
                if (saved.misc.chatCommandsEnabled != null) {
                    setChatCommandsEnabled(saved.misc.chatCommandsEnabled);
                }
                if (saved.misc.ca50ChatCommandEnabled != null) {
                    setCa50ChatCommandEnabled(saved.misc.ca50ChatCommandEnabled);
                }
                if (saved.misc.ca50PartyCommandsEnabled != null) {
                    setCa50PartyCommandsEnabled(saved.misc.ca50PartyCommandsEnabled);
                }
                if (saved.misc.ca50GuildCommandsEnabled != null) {
                    setCa50GuildCommandsEnabled(saved.misc.ca50GuildCommandsEnabled);
                }
                if (saved.misc.ca50AllChatCommandsEnabled != null) {
                    setCa50AllChatCommandsEnabled(saved.misc.ca50AllChatCommandsEnabled);
                }
                if (saved.misc.ca50PrivateCommandsEnabled != null) {
                    setCa50PrivateCommandsEnabled(saved.misc.ca50PrivateCommandsEnabled);
                }
                if (saved.misc.loadoutsAutoCloseEnabled != null) {
                    setLoadoutsAutoCloseEnabled(saved.misc.loadoutsAutoCloseEnabled);
                }
                if (saved.misc.loadoutKeybinds != null) {
                    loadoutKeybinds = normalizeLoadoutKeybinds(saved.misc.loadoutKeybinds);
                }
                if (saved.misc.superpairsHelperEnabled != null) {
                    setSuperpairsHelperEnabled(saved.misc.superpairsHelperEnabled);
                }
                if (saved.misc.superpairsHelperDebugEnabled != null) {
                    setSuperpairsHelperDebugEnabled(saved.misc.superpairsHelperDebugEnabled);
                }
                if (saved.misc.superpairsHelperX != null) {
                    setSuperpairsHelperX(saved.misc.superpairsHelperX);
                }
                if (saved.misc.superpairsHelperY != null) {
                    setSuperpairsHelperY(saved.misc.superpairsHelperY);
                }
                if (saved.misc.superpairsHelperScale != null) {
                    setSuperpairsHelperScale(saved.misc.superpairsHelperScale);
                }
                if (saved.misc.customSoundsEnabled != null) {
                    setCustomSoundsEnabled(saved.misc.customSoundsEnabled);
                }
                if (saved.misc.customArrowHitSounds != null) {
                    setCustomArrowHitSounds(saved.misc.customArrowHitSounds);
                }
                if (saved.misc.customArrowHitVolumeTenths != null) {
                    setCustomArrowHitVolumeTenths(saved.misc.customArrowHitVolumeTenths);
                }
                if (saved.misc.customArrowHitPitchHundredths != null) {
                    setCustomArrowHitPitchHundredths(saved.misc.customArrowHitPitchHundredths);
                }
                if (saved.misc.customWitherShieldExpireSounds != null) {
                    setCustomWitherShieldExpireSounds(saved.misc.customWitherShieldExpireSounds);
                }
                if (saved.misc.customWitherShieldExpireVolumeTenths != null) {
                    setCustomWitherShieldExpireVolumeTenths(saved.misc.customWitherShieldExpireVolumeTenths);
                }
                if (saved.misc.customWitherShieldExpirePitchHundredths != null) {
                    setCustomWitherShieldExpirePitchHundredths(saved.misc.customWitherShieldExpirePitchHundredths);
                }
                customArrowHitSoundVolumeTenths = normalizeCustomSoundValues(
                    saved.misc.customArrowHitSoundVolumeTenths,
                    0,
                    50
                );
                customArrowHitSoundPitchHundredths = normalizeCustomSoundValues(
                    saved.misc.customArrowHitSoundPitchHundredths,
                    25,
                    300
                );
                customWitherShieldExpireSoundVolumeTenths = normalizeCustomSoundValues(
                    saved.misc.customWitherShieldExpireSoundVolumeTenths,
                    0,
                    50
                );
                customWitherShieldExpireSoundPitchHundredths = normalizeCustomSoundValues(
                    saved.misc.customWitherShieldExpireSoundPitchHundredths,
                    25,
                    300
                );
            }
            if (saved != null && saved.slayer != null) {
                if (saved.slayer.tarantulaHelperEnabled != null) {
                    setTarantulaHelperEnabled(saved.slayer.tarantulaHelperEnabled);
                }
                if (saved.slayer.eggSacPredictionRendererEnabled != null) {
                    setEggSacPredictionRendererEnabled(saved.slayer.eggSacPredictionRendererEnabled);
                }
                if (saved.slayer.eggSacPredictionEnabled != null) {
                    setEggSacPredictionEnabled(saved.slayer.eggSacPredictionEnabled);
                }
                if (saved.slayer.eggSacPredictionRenderMode != null) {
                    setEggSacPredictionRenderMode(saved.slayer.eggSacPredictionRenderMode);
                }
                if (saved.slayer.tarantulaHelperDebugMessages != null) {
                    setTarantulaHelperDebugMessages(saved.slayer.tarantulaHelperDebugMessages);
                }
                if (saved.slayer.tarantulaDebugSlayerSpawned != null) {
                    setTarantulaDebugSlayerSpawned(saved.slayer.tarantulaDebugSlayerSpawned);
                }
                if (saved.slayer.tarantulaDebugSlayerPosition != null) {
                    setTarantulaDebugSlayerPosition(saved.slayer.tarantulaDebugSlayerPosition);
                }
                if (saved.slayer.tarantulaDebugSlayerPhaseChange != null) {
                    setTarantulaDebugSlayerPhaseChange(saved.slayer.tarantulaDebugSlayerPhaseChange);
                }
                if (saved.slayer.tarantulaDebugSlayerDead != null) {
                    setTarantulaDebugSlayerDead(saved.slayer.tarantulaDebugSlayerDead);
                }
                if (saved.slayer.tarantulaDebugEggSacPhaseStart != null) {
                    setTarantulaDebugEggSacPhaseStart(saved.slayer.tarantulaDebugEggSacPhaseStart);
                }
                if (saved.slayer.tarantulaDebugEggSacPhaseDone != null) {
                    setTarantulaDebugEggSacPhaseDone(saved.slayer.tarantulaDebugEggSacPhaseDone);
                }
                if (saved.slayer.tarantulaDebugEggSacLearning != null) {
                    setTarantulaDebugEggSacLearning(saved.slayer.tarantulaDebugEggSacLearning);
                }
            }
        } catch (IOException | RuntimeException exception) {
            KungMod.LOGGER.warn("Failed to load kung config.", exception);
        } finally {
            loading = false;
        }
        applyBundledDefaults();
        if (migrateLegacyConfig) {
            save();
        }
    }

    private void applyBundledDefaults() {
        Properties defaults = bundledDefaults();
        if (defaults.isEmpty()) {
            return;
        }

        String defaultRoomSyncServerUrl = defaults.getProperty("roomSync.serverUrl", "").trim();
        if (roomSyncServerUrl().isBlank() && !defaultRoomSyncServerUrl.isBlank()) {
            roomSyncServerUrl = defaultRoomSyncServerUrl;
        }

        String defaultRoomSyncToken = defaults.getProperty("roomSync.token", "").trim();
        if (roomSyncToken().isBlank() && !defaultRoomSyncToken.isBlank()) {
            roomSyncToken = defaultRoomSyncToken;
        }

    }

    private static Properties bundledDefaults() {
        Properties properties = new Properties();
        try (InputStream stream = DungeonMapOverlayConfig.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to load bundled kung defaults.", exception);
        }
        return properties;
    }

    private static String normalizeRoomSyncServerUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }

        boolean addedScheme = !normalized.contains("://");
        if (addedScheme) {
            normalized = "http://" + normalized;
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!addedScheme) {
            return normalized;
        }

        try {
            URI uri = new URI(normalized);
            if (uri.getHost() == null || uri.getPort() >= 0) {
                return normalized;
            }
            return new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                8765,
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
            ).toString();
        } catch (URISyntaxException exception) {
            return normalized;
        }
    }

    private static String normalizeCustomSoundList(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.replace('\n', ',').replace(';', ',').split(",");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            String name = part == null ? "" : part.trim();
            if ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("'") && name.endsWith("'"))) {
                name = name.substring(1, name.length() - 1).trim();
            }
            name = name.replace('\\', '/');
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            if (name.isBlank() || name.equals(".") || name.equals("..")) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(name);
        }
        return builder.toString();
    }

    private static int customSoundValue(
        Map<String, Integer> values,
        String soundName,
        int fallback,
        int min,
        int max
    ) {
        String normalized = normalizeCustomSoundName(soundName);
        Integer value = normalized.isBlank() ? null : values.get(normalized);
        return Math.clamp(value == null ? fallback : value, min, max);
    }

    private static void putCustomSoundValue(
        Map<String, Integer> values,
        String soundName,
        int value,
        int min,
        int max
    ) {
        String normalized = normalizeCustomSoundName(soundName);
        if (normalized.isBlank()) {
            return;
        }
        values.put(normalized, Math.clamp(value, min, max));
    }

    private static Map<String, Integer> normalizeCustomSoundValues(Map<String, Integer> saved, int min, int max) {
        Map<String, Integer> normalizedValues = new HashMap<>();
        if (saved == null) {
            return normalizedValues;
        }
        for (Map.Entry<String, Integer> entry : saved.entrySet()) {
            String name = normalizeCustomSoundName(entry.getKey());
            Integer value = entry.getValue();
            if (name.isBlank() || value == null) {
                continue;
            }
            normalizedValues.put(name, Math.clamp(value, min, max));
        }
        return normalizedValues;
    }

    private static String normalizeCustomSoundName(String value) {
        if (value == null) {
            return "";
        }
        String name = value.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if ((name.startsWith("\"") && name.endsWith("\"")) || (name.startsWith("'") && name.endsWith("'"))) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name.equals(".") || name.equals("..") ? "" : name;
    }

    private void save() {
        if (loading) {
            return;
        }

        try {
            Path file = configFile();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(SavedConfig.from(this), writer);
            }
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to save kung config.", exception);
        }
    }

    public static Path configDirectory() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve("kung");
    }

    private static Path configFile() {
        return configDirectory().resolve("kung.json");
    }

    private static Path legacyConfigFile() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve("kung.json");
    }

    private static String[] defaultLoadoutKeybinds() {
        String[] defaults = new String[12];
        for (int index = 0; index < 9; index++) {
            defaults[index] = "key:" + (49 + index) + ":0";
        }
        defaults[9] = "key:48:0";
        defaults[10] = "key:45:0";
        defaults[11] = "key:61:0";
        return defaults;
    }

    private static String[] normalizeLoadoutKeybinds(String[] saved) {
        String[] normalized = defaultLoadoutKeybinds();
        int length = Math.min(saved.length, normalized.length);
        for (int index = 0; index < length; index++) {
            if (saved[index] != null) {
                normalized[index] = saved[index];
            }
        }
        return normalized;
    }

    private record SavedConfig(
        SavedDungeonMap dungeonMap,
        SavedSplitsOverlay splitsOverlay,
        SavedBloodRushHelper bloodRushHelper,
        SavedDungeonChatFilter dungeonChatFilter,
        SavedDebug debug,
        SavedMisc misc,
        SavedSlayer slayer
    ) {
        static SavedConfig from(DungeonMapOverlayConfig config) {
            return new SavedConfig(
                new SavedDungeonMap(
                    config.enabled,
                    config.x,
                    config.y,
                    config.scale,
                    config.unopenedRoomAlpha,
                    config.showHeader,
                    config.showLegend,
                    config.princeIconsEnabled,
                    config.forcePaulScoreEnabled,
                    config.playerTrackingEnabled,
                    config.deathMessagesEnabled,
                    config.deathMessagesShareTotalEnabled,
                    config.deathMessagesShareIndividualEnabled,
                    config.debugMessages,
                    config.debugRoomCrypts,
                    config.debugRoomMatches,
                    config.localRoomDataEnabled,
                    config.fiveCryptPartyMessageEnabled,
                    config.fiveCryptPartyMessage(),
                    config.cryptProgressPartyMessageEnabled,
                    config.fiveCryptTitleEnabled,
                    config.roomSyncEnabled,
                    config.roomSyncServerUrl,
                    config.roomSyncToken,
                    config.roomSyncUploadEnabled
                ),
                new SavedSplitsOverlay(
                    config.splitsEnabled,
                    config.splitsX,
                    config.splitsY,
                    config.splitsScale
                ),
                new SavedBloodRushHelper(
                    config.bloodRushHelperEnabled,
                    config.bloodRushHelperTitleDurationTenths
                ),
                new SavedDungeonChatFilter(
                    config.dungeonChatFilterEnabled,
                    config.dungeonChatFilterBlessings,
                    config.dungeonChatFilterLootSpam,
                    config.dungeonChatFilterWatcher,
                    config.dungeonChatFilterBossMessages,
                    config.dungeonChatFilterBonzo,
                    config.dungeonChatFilterScarf,
                    config.dungeonChatFilterProfessor,
                    config.dungeonChatFilterThorn,
                    config.dungeonChatFilterLivid,
                    config.dungeonChatFilterSadan,
                    config.dungeonChatFilterMaxor,
                    config.dungeonChatFilterStorm,
                    config.dungeonChatFilterGoldor,
                    config.dungeonChatFilterNecron,
                    config.dungeonChatFilterWitherKing
                ),
                new SavedDebug(
                    config.debugMessagesEnabled,
                    config.debugContextMessages,
                    config.debugMessages,
                    config.debugInterfaceMessages,
                    config.tarantulaHelperDebugMessages
                ),
                new SavedMisc(
                    config.lobbyHopHelperEnabled,
                    config.hypixelApiEnabled,
                    config.hypixelApiKey,
                    config.chatCommandsEnabled,
                    config.ca50ChatCommandEnabled,
                    config.ca50PartyCommandsEnabled,
                    config.ca50GuildCommandsEnabled,
                    config.ca50AllChatCommandsEnabled,
                    config.ca50PrivateCommandsEnabled,
                    config.loadoutsAutoCloseEnabled,
                    config.loadoutKeybinds.clone(),
                    config.superpairsHelperEnabled,
                    config.superpairsHelperDebugEnabled,
                    config.superpairsHelperX,
                    config.superpairsHelperY,
                    config.superpairsHelperScale,
                    config.customSoundsEnabled,
                    config.customArrowHitSounds(),
                    config.customArrowHitVolumeTenths,
                    config.customArrowHitPitchHundredths,
                    config.customWitherShieldExpireSounds(),
                    config.customWitherShieldExpireVolumeTenths,
                    config.customWitherShieldExpirePitchHundredths,
                    Map.copyOf(config.customArrowHitSoundVolumeTenths),
                    Map.copyOf(config.customArrowHitSoundPitchHundredths),
                    Map.copyOf(config.customWitherShieldExpireSoundVolumeTenths),
                    Map.copyOf(config.customWitherShieldExpireSoundPitchHundredths)
                ),
                new SavedSlayer(
                    config.tarantulaHelperEnabled,
                    config.eggSacPredictionRendererEnabled,
                    config.eggSacPredictionEnabled,
                    config.eggSacPredictionRenderMode,
                    config.tarantulaHelperDebugMessages,
                    config.tarantulaDebugSlayerSpawned,
                    config.tarantulaDebugSlayerPosition,
                    config.tarantulaDebugSlayerPhaseChange,
                    config.tarantulaDebugSlayerDead,
                    config.tarantulaDebugEggSacPhaseStart,
                    config.tarantulaDebugEggSacPhaseDone,
                    config.tarantulaDebugEggSacLearning
                )
            );
        }
    }

    private record SavedDungeonMap(
        boolean enabled,
        int x,
        int y,
        int scale,
        int unopenedRoomAlpha,
        boolean showHeader,
        boolean showLegend,
        Boolean princeIconsEnabled,
        Boolean forcePaulScoreEnabled,
        Boolean playerTrackingEnabled,
        Boolean deathMessagesEnabled,
        Boolean deathMessagesShareTotalEnabled,
        Boolean deathMessagesShareIndividualEnabled,
        Boolean debugMessages,
        Boolean debugRoomCrypts,
        Boolean debugRoomMatches,
        Boolean localRoomDataEnabled,
        Boolean fiveCryptPartyMessageEnabled,
        String fiveCryptPartyMessage,
        Boolean cryptProgressPartyMessageEnabled,
        Boolean fiveCryptTitleEnabled,
        Boolean roomSyncEnabled,
        String roomSyncServerUrl,
        String roomSyncToken,
        Boolean roomSyncUploadEnabled
    ) {
    }

    private record SavedSplitsOverlay(
        boolean enabled,
        int x,
        int y,
        int scale
    ) {
    }

    private record SavedBloodRushHelper(Boolean enabled, Integer titleDurationTenths) {
    }

    private record SavedDungeonChatFilter(
        Boolean enabled,
        Boolean blessings,
        Boolean lootSpam,
        Boolean watcher,
        Boolean bossMessages,
        Boolean bonzo,
        Boolean scarf,
        Boolean professor,
        Boolean thorn,
        Boolean livid,
        Boolean sadan,
        Boolean maxor,
        Boolean storm,
        Boolean goldor,
        Boolean necron,
        Boolean witherKing
    ) {
    }

    private record SavedDebug(
        Boolean enabled,
        Boolean contextMessages,
        Boolean dungeonMessages,
        Boolean interfaceMessages,
        Boolean tarantulaMessages
    ) {
    }

    private record SavedMisc(
        Boolean lobbyHopHelperEnabled,
        Boolean hypixelApiEnabled,
        String hypixelApiKey,
        Boolean chatCommandsEnabled,
        Boolean ca50ChatCommandEnabled,
        Boolean ca50PartyCommandsEnabled,
        Boolean ca50GuildCommandsEnabled,
        Boolean ca50AllChatCommandsEnabled,
        Boolean ca50PrivateCommandsEnabled,
        Boolean loadoutsAutoCloseEnabled,
        String[] loadoutKeybinds,
        Boolean superpairsHelperEnabled,
        Boolean superpairsHelperDebugEnabled,
        Integer superpairsHelperX,
        Integer superpairsHelperY,
        Integer superpairsHelperScale,
        Boolean customSoundsEnabled,
        String customArrowHitSounds,
        Integer customArrowHitVolumeTenths,
        Integer customArrowHitPitchHundredths,
        String customWitherShieldExpireSounds,
        Integer customWitherShieldExpireVolumeTenths,
        Integer customWitherShieldExpirePitchHundredths,
        Map<String, Integer> customArrowHitSoundVolumeTenths,
        Map<String, Integer> customArrowHitSoundPitchHundredths,
        Map<String, Integer> customWitherShieldExpireSoundVolumeTenths,
        Map<String, Integer> customWitherShieldExpireSoundPitchHundredths
    ) {
    }

    private record SavedSlayer(
        Boolean tarantulaHelperEnabled,
        Boolean eggSacPredictionRendererEnabled,
        Boolean eggSacPredictionEnabled,
        EggSacPredictionRenderMode eggSacPredictionRenderMode,
        Boolean tarantulaHelperDebugMessages,
        Boolean tarantulaDebugSlayerSpawned,
        Boolean tarantulaDebugSlayerPosition,
        Boolean tarantulaDebugSlayerPhaseChange,
        Boolean tarantulaDebugSlayerDead,
        Boolean tarantulaDebugEggSacPhaseStart,
        Boolean tarantulaDebugEggSacPhaseDone,
        Boolean tarantulaDebugEggSacLearning
    ) {
    }

    public enum EggSacPredictionRenderMode {
        BOX("Quader"),
        GRID("Grid");

        private final String label;

        EggSacPredictionRenderMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        private EggSacPredictionRenderMode next() {
            EggSacPredictionRenderMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
