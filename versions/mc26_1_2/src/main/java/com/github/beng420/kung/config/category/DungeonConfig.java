package com.github.beng420.kung.config.category;

import com.github.beng420.kung.runtime.KungDeveloperAccess;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DungeonConfig extends ConfigCategory {
    private boolean enabled = false;
    private int x = 8;
    private int y = 8;
    private int scale = 100;
    private int textScale = 100;
    private int unopenedRoomAlpha = 12;
    private boolean showHeader = true;
    private boolean showInBoss = true;
    private boolean princeIconsEnabled = true;
    private Map<String, Boolean> princeRoomOverrides = new HashMap<>();
    private boolean mimicEspEnabled = false;
    private boolean iceSprayHighlightEnabled = false;
    private int iceSprayBoxSize = 100;
    /** Pre-merge master of M7 Dragon Debuff; only read to seed witherDragonsEnabled. */
    private boolean dragonDebuffEnabled = false;
    private boolean dragonDebuffTrackerEnabled = true;
    private DragonDebuffScope dragonDebuffScope = DragonDebuffScope.ALL_DRAGONS;
    private int dragonDebuffX = 8;
    private int dragonDebuffY = 230;
    private int dragonDebuffScale = 85;
    /** Pre-merge master of M7 Dragon Helper; only read to seed witherDragonsEnabled. */
    private boolean m7DragonHelperEnabled = false;
    /** Null until toggled, so a config from before the merge keeps whichever dragon feature it had on. */
    private Boolean witherDragonsEnabled;
    private boolean dragonFlightPathsEnabled = true;
    private boolean dragonSpawnMarkersEnabled = true;
    private boolean dragonStatueBoxesEnabled = true;
    private boolean dragonCountNotificationsEnabled = true;
    private boolean devDragonDiagnosticsEnabled = false;
    private boolean coloredPillarsEnabled = false;
    private boolean wishAlertEnabled = false;
    private int wishAlertLowHealthPercent = 20;
    private DragonMarkerMode dragonMarkerMode = DragonMarkerMode.CORE;
    private DragonPart dragonTrailPart = DragonPart.NECK;
    private Set<DragonPart> dragonCoreParts = EnumSet.of(DragonPart.BOX);
    private DragonAimMode dragonAimMode = DragonAimMode.AUTO;
    private boolean devWishAlertAnyClassEnabled = false;
    private PillarMaterial pillarMaterial = PillarMaterial.WOOL;
    private boolean forcePaulScoreEnabled = false;
    private boolean playerTrackingEnabled = false;
    private boolean runStatsReportEnabled = true;
    private boolean playerStatsReportEnabled = true;
    private boolean deathMessagesEnabled = false;
    private boolean deathMessagesShareTotalEnabled = false;
    private boolean deathMessagesShareIndividualEnabled = false;
    private boolean extraScoreMessagesEnabled = false;
    private boolean mimicMessageEnabled = true;
    private boolean princeMessageEnabled = true;
    private boolean batMessageEnabled = true;
    private boolean debugRoomCrypts = false;
    private boolean debugRoomMatches = false;
    private boolean localRoomDataEnabled = false;
    private String roomDataProjectDirectory = "";
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
    public boolean showInBoss() { return showInBoss; }
    public void setShowInBoss(boolean value) { showInBoss = value; save(); }
    public boolean princeIconsEnabled() { return princeIconsEnabled; }
    public void setPrinceIconsEnabled(boolean value) { princeIconsEnabled = value; save(); }
    public Boolean roomPrinceOverride(String roomKey) {
        return princeRoomOverrides == null ? null : princeRoomOverrides.get(roomKey);
    }
    public void setRoomPrinceOverride(String roomKey, boolean prince) {
        if (princeRoomOverrides == null) princeRoomOverrides = new HashMap<>();
        princeRoomOverrides.put(roomKey, prince);
        save();
    }
    public boolean mimicEspEnabled() { return mimicEspEnabled; }
    public void setMimicEspEnabled(boolean value) { mimicEspEnabled = value; save(); }
    public boolean iceSprayHighlightEnabled() { return iceSprayHighlightEnabled; }
    public void setIceSprayHighlightEnabled(boolean value) { iceSprayHighlightEnabled = value; save(); }
    public int iceSprayBoxSize() { return Math.clamp(iceSprayBoxSize, 100, 200); }
    public void setIceSprayBoxSize(int value) { iceSprayBoxSize = Math.clamp(value, 100, 200); save(); }
    public boolean witherDragonsEnabled() {
        return witherDragonsEnabled != null ? witherDragonsEnabled : dragonDebuffEnabled || m7DragonHelperEnabled;
    }
    public void setWitherDragonsEnabled(boolean value) { witherDragonsEnabled = value; save(); }
    public boolean dragonDebuffTrackerEnabled() { return dragonDebuffTrackerEnabled; }
    public void setDragonDebuffTrackerEnabled(boolean value) { dragonDebuffTrackerEnabled = value; save(); }
    /** Debuff tracking in effect: the Wither Dragons master plus its own switch. */
    public boolean dragonDebuffEnabled() { return witherDragonsEnabled() && dragonDebuffTrackerEnabled; }
    /** The HUD editor's switch: turning the HUD on also turns the master on, or nothing would show. */
    public void setDragonDebuffEnabled(boolean value) {
        dragonDebuffTrackerEnabled = value;
        if (value) witherDragonsEnabled = true;
        save();
    }
    public DragonDebuffScope dragonDebuffScope() { return dragonDebuffScope == null ? DragonDebuffScope.ALL_DRAGONS : dragonDebuffScope; }
    public void setDragonDebuffScope(DragonDebuffScope value) { dragonDebuffScope = value == null ? DragonDebuffScope.ALL_DRAGONS : value; save(); }
    public int dragonDebuffX() { return dragonDebuffX; }
    public int dragonDebuffY() { return dragonDebuffY; }
    public int dragonDebuffScale() { return Math.clamp(dragonDebuffScale, 25, 300); }
    public void setDragonDebuffX(int value) { dragonDebuffX = value; save(); }
    public void setDragonDebuffY(int value) { dragonDebuffY = value; save(); }
    public void setDragonDebuffScale(int value) { dragonDebuffScale = Math.clamp(value, 25, 300); save(); }
    /** The helper has no switch of its own any more; Wither Dragons turns it on for the developer account. */
    public boolean m7DragonHelperEnabled() { return witherDragonsEnabled(); }
    public boolean dragonFlightPathsEnabled() { return dragonFlightPathsEnabled; }
    public void setDragonFlightPathsEnabled(boolean value) { dragonFlightPathsEnabled = value; save(); }
    public boolean dragonSpawnMarkersEnabled() { return dragonSpawnMarkersEnabled; }
    public void setDragonSpawnMarkersEnabled(boolean value) { dragonSpawnMarkersEnabled = value; save(); }
    public boolean dragonStatueBoxesEnabled() { return dragonStatueBoxesEnabled; }
    public void setDragonStatueBoxesEnabled(boolean value) { dragonStatueBoxesEnabled = value; save(); }
    public boolean dragonCountNotificationsEnabled() { return dragonCountNotificationsEnabled; }
    public void setDragonCountNotificationsEnabled(boolean value) { dragonCountNotificationsEnabled = value; save(); }
    public boolean devDragonDiagnosticsEnabled() { return devDragonDiagnosticsEnabled && KungDeveloperAccess.allowed(); }
    public void setDevDragonDiagnosticsEnabled(boolean value) { devDragonDiagnosticsEnabled = value; save(); }
    public DragonMarkerMode dragonMarkerMode() { return dragonMarkerMode; }
    public void setDragonMarkerMode(DragonMarkerMode value) {
        dragonMarkerMode = value == null ? DragonMarkerMode.CORE : value;
        save();
    }
    public DragonPart dragonTrailPart() { return dragonTrailPart == null ? DragonPart.NECK : dragonTrailPart; }
    public void setDragonTrailPart(DragonPart value) { dragonTrailPart = value; save(); }
    public DragonAimMode dragonAimMode() { return dragonAimMode == null ? DragonAimMode.AUTO : dragonAimMode; }
    public void setDragonAimMode(DragonAimMode value) { dragonAimMode = value; save(); }
    public boolean dragonCorePart(DragonPart part) { return dragonCoreParts != null && dragonCoreParts.contains(part); }
    public void toggleDragonCorePart(DragonPart part) {
        if (dragonCoreParts == null) dragonCoreParts = EnumSet.noneOf(DragonPart.class);
        if (!dragonCoreParts.remove(part)) dragonCoreParts.add(part);
        save();
    }
    public boolean wishAlertEnabled() { return wishAlertEnabled; }
    public boolean devWishAlertAnyClassEnabled() { return devWishAlertAnyClassEnabled; }
    public void setDevWishAlertAnyClassEnabled(boolean value) {
        devWishAlertAnyClassEnabled = value;
        save();
    }
    public void setWishAlertEnabled(boolean value) { wishAlertEnabled = value; save(); }
    public int wishAlertLowHealthPercent() { return Math.clamp(wishAlertLowHealthPercent, 0, 100); }
    public void setWishAlertLowHealthPercent(int value) { wishAlertLowHealthPercent = Math.clamp(value, 0, 100); save(); }
    public boolean coloredPillarsEnabled() { return coloredPillarsEnabled; }
    public void setColoredPillarsEnabled(boolean value) { coloredPillarsEnabled = value; save(); }
    public PillarMaterial pillarMaterial() { return pillarMaterial == null ? PillarMaterial.WOOL : pillarMaterial; }
    public void setPillarMaterial(PillarMaterial value) { pillarMaterial = value == null ? PillarMaterial.WOOL : value; save(); }
    public boolean forcePaulScoreEnabled() { return forcePaulScoreEnabled; }
    public void setForcePaulScoreEnabled(boolean value) { forcePaulScoreEnabled = value; save(); }
    public boolean playerTrackingEnabled() { return playerTrackingEnabled; }
    public void setPlayerTrackingEnabled(boolean value) { playerTrackingEnabled = value; save(); }
    public boolean runStatsReportEnabled() { return runStatsReportEnabled; }
    public void setRunStatsReportEnabled(boolean value) { runStatsReportEnabled = value; save(); }
    public boolean playerStatsReportEnabled() { return playerStatsReportEnabled; }
    public void setPlayerStatsReportEnabled(boolean value) { playerStatsReportEnabled = value; save(); }
    public boolean deathMessagesEnabled() { return deathMessagesEnabled; }
    public void setDeathMessagesEnabled(boolean value) { deathMessagesEnabled = value; save(); }
    public boolean deathMessagesShareTotalEnabled() { return deathMessagesShareTotalEnabled; }
    public void setDeathMessagesShareTotalEnabled(boolean value) { deathMessagesShareTotalEnabled = value; save(); }
    public boolean deathMessagesShareIndividualEnabled() { return deathMessagesShareIndividualEnabled; }
    public void setDeathMessagesShareIndividualEnabled(boolean value) { deathMessagesShareIndividualEnabled = value; save(); }
    public boolean extraScoreMessagesEnabled() { return extraScoreMessagesEnabled; }
    public void setExtraScoreMessagesEnabled(boolean value) { extraScoreMessagesEnabled = value; save(); }
    public boolean mimicMessageEnabled() { return mimicMessageEnabled; }
    public void setMimicMessageEnabled(boolean value) { mimicMessageEnabled = value; save(); }
    public boolean princeMessageEnabled() { return princeMessageEnabled; }
    public void setPrinceMessageEnabled(boolean value) { princeMessageEnabled = value; save(); }
    public boolean batMessageEnabled() { return batMessageEnabled; }
    public void setBatMessageEnabled(boolean value) { batMessageEnabled = value; save(); }
    public boolean debugRoomCrypts() { return debugRoomCrypts; }
    public void setDebugRoomCrypts(boolean value) { debugRoomCrypts = value; save(); }
    public boolean debugRoomMatches() { return debugRoomMatches; }
    public void setDebugRoomMatches(boolean value) { debugRoomMatches = value; save(); }
    public boolean localRoomDataEnabled() { return localRoomDataEnabled; }
    public void setLocalRoomDataEnabled(boolean value) { localRoomDataEnabled = value; save(); }
    public String roomDataProjectDirectory() { return roomDataProjectDirectory == null ? "" : roomDataProjectDirectory; }
    public void setRoomDataProjectDirectory(String value) {
        roomDataProjectDirectory = value == null ? "" : value.strip();
        save();
    }
    public boolean fiveCryptPartyMessageEnabled() { return fiveCryptPartyMessageEnabled; }
    public void setFiveCryptPartyMessageEnabled(boolean value) { fiveCryptPartyMessageEnabled = value; save(); }
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
        this.unopenedRoomAlpha = Math.clamp(alpha, 0, 100);
        save();
    }

    public String fiveCryptPartyMessage() {
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

    public enum PillarMaterial {
        WOOL("Wool"), GLASS("Glass"), TERRACOTTA("Terracotta");

        private final String label;
        PillarMaterial(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** Which prefire aim point to show; Auto picks the weapon from the dungeon class. */
    public enum DragonAimMode {
        AUTO("Auto (Class)"), LAST_BREATH("Last Breath"), TERMINATOR("Terminator"), OFF("Off");

        private final String label;
        DragonAimMode(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum DragonMarkerMode {
        CORE("Core"), SKELETON("Skeleton");

        private final String label;
        DragonMarkerMode(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** A dragon hitbox part; part is its slot in EnderDragon.getSubEntities(), -1 for the overall box centre. */
    public enum DragonPart {
        BOX("Box Centre", -1), HEAD("Head", 0), NECK("Neck", 1), BODY("Body", 2),
        TAIL_1("Tail 1", 3), TAIL_2("Tail 2", 4), TAIL_3("Tail 3", 5), WING_1("Wing 1", 6), WING_2("Wing 2", 7);

        private final String label;
        private final int part;
        DragonPart(String label, int part) { this.label = label; this.part = part; }
        public String label() { return label; }
        public int part() { return part; }
    }

    public enum DragonDebuffScope {
        ALL_DRAGONS("All Dragons"), NEAREST_STATUE("Nearest Statue");

        private final String label;
        DragonDebuffScope(String label) { this.label = label; }
        public String label() { return label; }
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
