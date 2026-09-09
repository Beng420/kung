package com.github.beng420.kung.config.category;

import java.util.HashMap;
import java.util.Map;

public final class MiscConfig extends ConfigCategory {
    private boolean lobbyHopHelperEnabled = false;
    private boolean hypixelApiEnabled = false;
    private String hypixelApiKey = "";

    private boolean chatCommandsEnabled = false;

    private boolean c50ChatCommandEnabled = true;
    private boolean c50PartyCommandsEnabled = true;
    private boolean c50GuildCommandsEnabled = true;
    private boolean c50AllChatCommandsEnabled = false;
    private boolean c50PrivateCommandsEnabled = false;
    private boolean ca50ChatCommandEnabled = true;
    private boolean ca50PartyCommandsEnabled = true;
    private boolean ca50GuildCommandsEnabled = true;
    private boolean ca50AllChatCommandsEnabled = false;
    private boolean ca50PrivateCommandsEnabled = false;
    private boolean tpsChatCommandEnabled = true;
    private boolean tpsPartyCommandsEnabled = true;
    private boolean tpsGuildCommandsEnabled = true;
    private boolean tpsAllChatCommandsEnabled = false;
    private boolean tpsPrivateCommandsEnabled = false;

    // Loadouts & Superpairs
    private boolean loadoutsAutoCloseEnabled = false;
    private boolean loadoutsCloseOnlyOnChange = false;
    private String[] loadoutKeybinds = defaultLoadoutKeybinds();
    private boolean superpairsHelperEnabled = false;
    private boolean superpairsHelperDebugEnabled = false;
    private int superpairsHelperX = 6;
    private int superpairsHelperY = 34;
    private int superpairsHelperScale = 75;

    // Custom Sounds
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

    public boolean lobbyHopHelperEnabled() { return lobbyHopHelperEnabled; }
    public void setLobbyHopHelperEnabled(boolean value) { lobbyHopHelperEnabled = value; save(); }
    public boolean hypixelApiEnabled() { return hypixelApiEnabled; }
    public void setHypixelApiEnabled(boolean value) { hypixelApiEnabled = value; save(); }
    public boolean directHypixelApiEnabled() { return hypixelApiEnabled && !hypixelApiKey().isBlank(); }
    public String hypixelApiKey() { return hypixelApiKey == null ? "" : hypixelApiKey; }
    public String hypixelApiKeyDisplay() {
        String key = hypixelApiKey();
        if (key.isBlank()) return "Not set";
        if (key.length() <= 8) return "Set";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
    public void setHypixelApiKey(String value) { hypixelApiKey = value == null ? "" : value.trim(); save(); }
    public boolean chatCommandsEnabled() { return chatCommandsEnabled; }
    public void setChatCommandsEnabled(boolean value) { chatCommandsEnabled = value; save(); }
    public boolean c50ChatCommandEnabled() { return c50ChatCommandEnabled; }
    public void setC50ChatCommandEnabled(boolean value) { c50ChatCommandEnabled = value; save(); }
    public boolean c50PartyCommandsEnabled() { return c50PartyCommandsEnabled; }
    public void setC50PartyCommandsEnabled(boolean value) { c50PartyCommandsEnabled = value; save(); }
    public boolean c50GuildCommandsEnabled() { return c50GuildCommandsEnabled; }
    public void setC50GuildCommandsEnabled(boolean value) { c50GuildCommandsEnabled = value; save(); }
    public boolean c50AllChatCommandsEnabled() { return c50AllChatCommandsEnabled; }
    public void setC50AllChatCommandsEnabled(boolean value) { c50AllChatCommandsEnabled = value; save(); }
    public boolean c50PrivateCommandsEnabled() { return c50PrivateCommandsEnabled; }
    public void setC50PrivateCommandsEnabled(boolean value) { c50PrivateCommandsEnabled = value; save(); }
    public boolean ca50ChatCommandEnabled() { return ca50ChatCommandEnabled; }
    public void setCa50ChatCommandEnabled(boolean value) { ca50ChatCommandEnabled = value; save(); }
    public boolean ca50PartyCommandsEnabled() { return ca50PartyCommandsEnabled; }
    public void setCa50PartyCommandsEnabled(boolean value) { ca50PartyCommandsEnabled = value; save(); }
    public boolean ca50GuildCommandsEnabled() { return ca50GuildCommandsEnabled; }
    public void setCa50GuildCommandsEnabled(boolean value) { ca50GuildCommandsEnabled = value; save(); }
    public boolean ca50AllChatCommandsEnabled() { return ca50AllChatCommandsEnabled; }
    public void setCa50AllChatCommandsEnabled(boolean value) { ca50AllChatCommandsEnabled = value; save(); }
    public boolean ca50PrivateCommandsEnabled() { return ca50PrivateCommandsEnabled; }
    public void setCa50PrivateCommandsEnabled(boolean value) { ca50PrivateCommandsEnabled = value; save(); }
    public boolean tpsChatCommandEnabled() { return tpsChatCommandEnabled; }
    public void setTpsChatCommandEnabled(boolean value) { tpsChatCommandEnabled = value; save(); }
    public boolean tpsPartyCommandsEnabled() { return tpsPartyCommandsEnabled; }
    public void setTpsPartyCommandsEnabled(boolean value) { tpsPartyCommandsEnabled = value; save(); }
    public boolean tpsGuildCommandsEnabled() { return tpsGuildCommandsEnabled; }
    public void setTpsGuildCommandsEnabled(boolean value) { tpsGuildCommandsEnabled = value; save(); }
    public boolean tpsAllChatCommandsEnabled() { return tpsAllChatCommandsEnabled; }
    public void setTpsAllChatCommandsEnabled(boolean value) { tpsAllChatCommandsEnabled = value; save(); }
    public boolean tpsPrivateCommandsEnabled() { return tpsPrivateCommandsEnabled; }
    public void setTpsPrivateCommandsEnabled(boolean value) { tpsPrivateCommandsEnabled = value; save(); }
    public boolean cataChatCommandEnabled() { return c50ChatCommandEnabled && ca50ChatCommandEnabled; }
    public void setCataChatCommandEnabled(boolean value) { setC50ChatCommandEnabled(value); setCa50ChatCommandEnabled(value); }
    public boolean cataPartyCommandsEnabled() { return c50PartyCommandsEnabled && ca50PartyCommandsEnabled; }
    public void setCataPartyCommandsEnabled(boolean value) { setC50PartyCommandsEnabled(value); setCa50PartyCommandsEnabled(value); }
    public boolean cataGuildCommandsEnabled() { return c50GuildCommandsEnabled && ca50GuildCommandsEnabled; }
    public void setCataGuildCommandsEnabled(boolean value) { setC50GuildCommandsEnabled(value); setCa50GuildCommandsEnabled(value); }
    public boolean cataAllChatCommandsEnabled() { return c50AllChatCommandsEnabled && ca50AllChatCommandsEnabled; }
    public void setCataAllChatCommandsEnabled(boolean value) { setC50AllChatCommandsEnabled(value); setCa50AllChatCommandsEnabled(value); }
    public boolean cataPrivateCommandsEnabled() { return c50PrivateCommandsEnabled && ca50PrivateCommandsEnabled; }
    public void setCataPrivateCommandsEnabled(boolean value) { setC50PrivateCommandsEnabled(value); setCa50PrivateCommandsEnabled(value); }
    public boolean loadoutsAutoCloseEnabled() { return loadoutsAutoCloseEnabled; }
    public void setLoadoutsAutoCloseEnabled(boolean value) { loadoutsAutoCloseEnabled = value; save(); }
    public boolean loadoutsCloseOnlyOnChange() { return loadoutsCloseOnlyOnChange; }
    public void setLoadoutsCloseOnlyOnChange(boolean value) { loadoutsCloseOnlyOnChange = value; save(); }
    public boolean superpairsHelperEnabled() { return superpairsHelperEnabled; }
    public void setSuperpairsHelperEnabled(boolean value) { superpairsHelperEnabled = value; save(); }
    public boolean superpairsHelperDebugEnabled() { return superpairsHelperDebugEnabled; }
    public void setSuperpairsHelperDebugEnabled(boolean value) { superpairsHelperDebugEnabled = value; save(); }
    public int superpairsHelperX() { return superpairsHelperX; }
    public void setSuperpairsHelperX(int value) { superpairsHelperX = value; save(); }
    public int superpairsHelperY() { return superpairsHelperY; }
    public void setSuperpairsHelperY(int value) { superpairsHelperY = value; save(); }
    public int superpairsHelperScale() { return superpairsHelperScale; }
    public boolean customSoundsEnabled() { return customSoundsEnabled; }
    public void setCustomSoundsEnabled(boolean value) { customSoundsEnabled = value; save(); }
    public String customArrowHitSounds() { return customArrowHitSounds == null ? "" : customArrowHitSounds; }
    public void setCustomArrowHitSounds(String value) { customArrowHitSounds = normalizeCustomSoundList(value); save(); }
    public int customArrowHitVolumeTenths() { return customArrowHitVolumeTenths; }
    public void setCustomArrowHitVolumeTenths(int value) { customArrowHitVolumeTenths = Math.clamp(value, 0, 50); save(); }
    public int customArrowHitPitchHundredths() { return customArrowHitPitchHundredths; }
    public void setCustomArrowHitPitchHundredths(int value) { customArrowHitPitchHundredths = Math.clamp(value, 25, 300); save(); }
    public String customWitherShieldExpireSounds() {
        return customWitherShieldExpireSounds == null ? "" : customWitherShieldExpireSounds;
    }
    public void setCustomWitherShieldExpireSounds(String value) {
        customWitherShieldExpireSounds = normalizeCustomSoundList(value);
        save();
    }
    public int customWitherShieldExpireVolumeTenths() { return customWitherShieldExpireVolumeTenths; }
    public void setCustomWitherShieldExpireVolumeTenths(int value) {
        customWitherShieldExpireVolumeTenths = Math.clamp(value, 0, 50);
        save();
    }
    public int customWitherShieldExpirePitchHundredths() { return customWitherShieldExpirePitchHundredths; }
    public void setCustomWitherShieldExpirePitchHundredths(int value) {
        customWitherShieldExpirePitchHundredths = Math.clamp(value, 25, 300);
        save();
    }
    public int customArrowHitSoundVolumeTenths(String soundName) {
        return customSoundValue(customArrowHitSoundVolumeTenths, soundName, customArrowHitVolumeTenths, 0, 50);
    }
    public void setCustomArrowHitSoundVolumeTenths(String soundName, int value) {
        putCustomSoundValue(customArrowHitSoundVolumeTenths, soundName, value, 0, 50);
        save();
    }
    public int customArrowHitSoundPitchHundredths(String soundName) {
        return customSoundValue(customArrowHitSoundPitchHundredths, soundName, customArrowHitPitchHundredths, 25, 300);
    }
    public void setCustomArrowHitSoundPitchHundredths(String soundName, int value) {
        putCustomSoundValue(customArrowHitSoundPitchHundredths, soundName, value, 25, 300);
        save();
    }
    public int customWitherShieldExpireSoundVolumeTenths(String soundName) {
        return customSoundValue(customWitherShieldExpireSoundVolumeTenths, soundName, customWitherShieldExpireVolumeTenths, 0, 50);
    }
    public void setCustomWitherShieldExpireSoundVolumeTenths(String soundName, int value) {
        putCustomSoundValue(customWitherShieldExpireSoundVolumeTenths, soundName, value, 0, 50);
        save();
    }
    public int customWitherShieldExpireSoundPitchHundredths(String soundName) {
        return customSoundValue(customWitherShieldExpireSoundPitchHundredths, soundName, customWitherShieldExpirePitchHundredths, 25, 300);
    }
    public void setCustomWitherShieldExpireSoundPitchHundredths(String soundName, int value) {
        putCustomSoundValue(customWitherShieldExpireSoundPitchHundredths, soundName, value, 25, 300);
        save();
    }

    public static String[] defaultLoadoutKeybinds() {
        String[] defaults = new String[12];
        for (int i = 0; i < 9; i++) defaults[i] = "key:" + (49 + i) + ":0";
        defaults[9] = "key:48:0";
        defaults[10] = "key:45:0";
        defaults[11] = "key:61:0";
        return defaults;
    }

    public String loadoutKeybind(int index) {
        if (index < 0 || index >= loadoutKeybinds.length) return "";
        String keybind = loadoutKeybinds[index];
        return keybind == null ? "" : keybind;
    }

    public int loadoutKeybindCount() {
        return loadoutKeybinds.length;
    }

    public void setLoadoutKeybind(int index, String keybind) {
        if (index < 0 || index >= loadoutKeybinds.length) return;
        loadoutKeybinds[index] = keybind == null ? "" : keybind;
        save();
    }

    public void setSuperpairsHelperScale(int scale) {
        this.superpairsHelperScale = Math.clamp(scale, 25, 300);
        save();
    }

    public void normalize() {
        if (loadoutKeybinds == null) {
            loadoutKeybinds = defaultLoadoutKeybinds();
        } else {
            String[] normalized = defaultLoadoutKeybinds();
            for (int index = 0; index < Math.min(loadoutKeybinds.length, normalized.length); index++) {
                if (loadoutKeybinds[index] != null) normalized[index] = loadoutKeybinds[index];
            }
            loadoutKeybinds = normalized;
        }
        customArrowHitSoundVolumeTenths = normalizeCustomSoundValues(customArrowHitSoundVolumeTenths, 0, 50);
        customArrowHitSoundPitchHundredths = normalizeCustomSoundValues(customArrowHitSoundPitchHundredths, 25, 300);
        customWitherShieldExpireSoundVolumeTenths = normalizeCustomSoundValues(customWitherShieldExpireSoundVolumeTenths, 0, 50);
        customWitherShieldExpireSoundPitchHundredths = normalizeCustomSoundValues(customWitherShieldExpireSoundPitchHundredths, 25, 300);
    }

    private static String normalizeCustomSoundList(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        for (String part : value.replace('\n', ',').replace(';', ',').split(",")) {
            String name = normalizeCustomSoundName(part);
            if (name.isBlank()) continue;
            if (!result.isEmpty()) result.append(", ");
            result.append(name);
        }
        return result.toString();
    }

    private static int customSoundValue(Map<String, Integer> values, String soundName, int fallback, int min, int max) {
        String normalized = normalizeCustomSoundName(soundName);
        Integer value = normalized.isBlank() || values == null ? null : values.get(normalized);
        return Math.clamp(value == null ? fallback : value, min, max);
    }

    private static void putCustomSoundValue(Map<String, Integer> values, String soundName, int value, int min, int max) {
        String normalized = normalizeCustomSoundName(soundName);
        if (!normalized.isBlank()) values.put(normalized, Math.clamp(value, min, max));
    }

    private static Map<String, Integer> normalizeCustomSoundValues(Map<String, Integer> saved, int min, int max) {
        Map<String, Integer> result = new HashMap<>();
        if (saved == null) return result;
        for (Map.Entry<String, Integer> entry : saved.entrySet()) {
            String name = normalizeCustomSoundName(entry.getKey());
            Integer value = entry.getValue();
            if (!name.isBlank() && value != null) result.put(name, Math.clamp(value, min, max));
        }
        return result;
    }

    private static String normalizeCustomSoundName(String value) {
        if (value == null) return "";
        String name = value.trim().replace('\\', '/');
        if (name.length() >= 2 && (name.startsWith("\"") && name.endsWith("\"")
            || name.startsWith("'") && name.endsWith("'"))) {
            name = name.substring(1, name.length() - 1).trim();
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.length() >= 2 && (name.startsWith("\"") && name.endsWith("\"")
            || name.startsWith("'") && name.endsWith("'"))) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name.equals(".") || name.equals("..") ? "" : name;
    }
}
