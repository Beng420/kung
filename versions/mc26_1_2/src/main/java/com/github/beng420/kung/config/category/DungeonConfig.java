package com.github.beng420.kung.config.category;

import java.net.URI;
import java.net.URISyntaxException;

public final class DungeonConfig extends ConfigCategory {
    private boolean enabled = false;
    private int x = 8;
    private int y = 8;
    private int scale = 100;
    private int textScale = 100;
    private int unopenedRoomAlpha = 12;
    private boolean showHeader = true;
    private boolean showLegend = false;
    private boolean showInBoss = true;
    private boolean princeIconsEnabled = true;
    private boolean mimicEspEnabled = false;
    private boolean forcePaulScoreEnabled = false;
    private boolean playerTrackingEnabled = false;
    private boolean deathMessagesEnabled = false;
    private boolean deathMessagesShareTotalEnabled = false;
    private boolean deathMessagesShareIndividualEnabled = false;
    private boolean debugRoomCrypts = false;
    private boolean debugRoomMatches = false;
    private boolean localRoomDataEnabled = false;
    private boolean fiveCryptPartyMessageEnabled = false;
    private String fiveCryptPartyMessage = "We got all 5 crypts (✿◠‿◠)";
    private boolean cryptProgressPartyMessageEnabled = false;
    private boolean fiveCryptTitleEnabled = false;

    // Room Sync Settings
    private boolean roomSyncEnabled = false;
    private boolean roomSyncUploadEnabled = false;
    private String roomSyncServerUrl = "";
    private String roomSyncToken = "";

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; save(); }
    public int x() { return x; }
    public void setX(int value) { x = value; save(); }
    public int y() { return y; }
    public void setY(int value) { y = value; save(); }
    public int scale() { return scale; }
    public int textScale() { return textScale; }
    public int unopenedRoomAlpha() { return unopenedRoomAlpha; }
    public boolean showHeader() { return showHeader; }
    public void setShowHeader(boolean value) { showHeader = value; save(); }
    public boolean showLegend() { return showLegend; }
    public void setShowLegend(boolean value) { showLegend = value; save(); }
    public boolean showInBoss() { return showInBoss; }
    public void setShowInBoss(boolean value) { showInBoss = value; save(); }
    public boolean princeIconsEnabled() { return princeIconsEnabled; }
    public void setPrinceIconsEnabled(boolean value) { princeIconsEnabled = value; save(); }
    public boolean mimicEspEnabled() { return mimicEspEnabled; }
    public void setMimicEspEnabled(boolean value) { mimicEspEnabled = value; save(); }
    public boolean forcePaulScoreEnabled() { return forcePaulScoreEnabled; }
    public void setForcePaulScoreEnabled(boolean value) { forcePaulScoreEnabled = value; save(); }
    public boolean playerTrackingEnabled() { return playerTrackingEnabled; }
    public void setPlayerTrackingEnabled(boolean value) { playerTrackingEnabled = value; save(); }
    public boolean deathMessagesEnabled() { return deathMessagesEnabled; }
    public void setDeathMessagesEnabled(boolean value) { deathMessagesEnabled = value; save(); }
    public boolean deathMessagesShareTotalEnabled() { return deathMessagesShareTotalEnabled; }
    public void setDeathMessagesShareTotalEnabled(boolean value) { deathMessagesShareTotalEnabled = value; save(); }
    public boolean deathMessagesShareIndividualEnabled() { return deathMessagesShareIndividualEnabled; }
    public void setDeathMessagesShareIndividualEnabled(boolean value) { deathMessagesShareIndividualEnabled = value; save(); }
    public boolean debugRoomCrypts() { return debugRoomCrypts; }
    public void setDebugRoomCrypts(boolean value) { debugRoomCrypts = value; save(); }
    public boolean debugRoomMatches() { return debugRoomMatches; }
    public void setDebugRoomMatches(boolean value) { debugRoomMatches = value; save(); }
    public boolean localRoomDataEnabled() { return localRoomDataEnabled; }
    public void setLocalRoomDataEnabled(boolean value) { localRoomDataEnabled = value; save(); }
    public boolean fiveCryptPartyMessageEnabled() { return fiveCryptPartyMessageEnabled; }
    public void setFiveCryptPartyMessageEnabled(boolean value) { fiveCryptPartyMessageEnabled = value; save(); }
    public String fiveCryptPartyMessage() { return getFiveCryptPartyMessage(); }
    public boolean cryptProgressPartyMessageEnabled() { return cryptProgressPartyMessageEnabled; }
    public void setCryptProgressPartyMessageEnabled(boolean value) { cryptProgressPartyMessageEnabled = value; save(); }
    public boolean fiveCryptTitleEnabled() { return fiveCryptTitleEnabled; }
    public void setFiveCryptTitleEnabled(boolean value) { fiveCryptTitleEnabled = value; save(); }
    public boolean roomSyncEnabled() { return roomSyncEnabled; }
    public void setRoomSyncEnabled(boolean value) { roomSyncEnabled = value; save(); }
    public boolean roomSyncUploadEnabled() { return roomSyncUploadEnabled; }
    public void setRoomSyncUploadEnabled(boolean value) { roomSyncUploadEnabled = value; save(); }
    public String roomSyncServerUrl() { return roomSyncServerUrl == null ? "" : roomSyncServerUrl; }
    public String roomSyncToken() { return roomSyncToken == null ? "" : roomSyncToken; }

    public void setScale(int scale) {
        this.scale = Math.clamp(scale, 25, 300);
        save();
    }

    public void setTextScale(int textScale) {
        this.textScale = Math.clamp(textScale, 50, 200);
        save();
    }

    public void setUnopenedRoomAlpha(int alpha) {
        this.unopenedRoomAlpha = Math.clamp(alpha, 0, 28);
        save();
    }

    public String getFiveCryptPartyMessage() {
        return (fiveCryptPartyMessage == null || fiveCryptPartyMessage.isBlank())
            ? "We got all 5 crypts (✿◠‿◠)"
            : fiveCryptPartyMessage;
    }

    public void setFiveCryptPartyMessage(String message) {
        String normalized = message == null ? "" : message.strip();
        if (normalized.startsWith("[Kung]")) {
            normalized = normalized.substring("[Kung]".length()).strip();
        }
        this.fiveCryptPartyMessage = normalized.isBlank() ? "We got all 5 crypts (✿◠‿◠)" : normalized;
        save();
    }

    public void setRoomSyncServerUrl(String serverUrl) {
        this.roomSyncServerUrl = normalizeRoomSyncServerUrl(serverUrl);
        save();
    }

    public void setRoomSyncToken(String token) {
        this.roomSyncToken = token == null ? "" : token.trim();
        save();
    }

    public static String normalizeRoomSyncServerUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return "";

        boolean addedScheme = !normalized.contains("://");
        if (addedScheme) normalized = "http://" + normalized;

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!addedScheme) return normalized;

        try {
            URI uri = new URI(normalized);
            if (uri.getHost() == null || uri.getPort() >= 0) return normalized;
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), 8765, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (URISyntaxException exception) {
            return normalized;
        }
    }
}
