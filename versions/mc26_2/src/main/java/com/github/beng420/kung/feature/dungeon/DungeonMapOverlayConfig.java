package com.github.beng420.kung.feature.dungeon;

import com.github.beng420.kung.KungMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class DungeonMapOverlayConfig {
    public static final DungeonMapOverlayConfig INSTANCE = new DungeonMapOverlayConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean enabled = false;
    private int x = 8;
    private int y = 8;
    private int scale = 100;
    private int unopenedRoomAlpha = 12;
    private boolean showHeader = true;
    private boolean showLegend = false;
    private boolean playerTrackingEnabled = false;
    private boolean debugMessages = false;
    private boolean debugRoomCrypts = false;
    private boolean debugRoomMatches = false;
    private boolean localRoomDataEnabled = false;
    private boolean roomSyncEnabled = false;
    private boolean roomSyncUploadEnabled = false;
    private String roomSyncServerUrl = "";
    private String roomSyncToken = "";
    private boolean splitsEnabled = false;
    private int splitsX = 226;
    private int splitsY = 8;
    private int splitsScale = 85;
    private boolean debugInterfaceMessages = false;
    private boolean lobbyHopHelperEnabled = false;
    private boolean superpairsHelperEnabled = false;
    private boolean superpairsHelperDebugEnabled = false;
    private int superpairsHelperX = 6;
    private int superpairsHelperY = 34;
    private int superpairsHelperScale = 75;
    private boolean tarantulaHelperEnabled = false;
    private boolean eggSacPredictionRendererEnabled = false;
    private boolean eggSacPredictionEnabled = false;
    private EggSacPredictionRenderMode eggSacPredictionRenderMode = EggSacPredictionRenderMode.BOX;
    private boolean tarantulaHelperDebugMessages = false;
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

    public boolean playerTrackingEnabled() {
        return playerTrackingEnabled;
    }

    public void setPlayerTrackingEnabled(boolean playerTrackingEnabled) {
        this.playerTrackingEnabled = playerTrackingEnabled;
        save();
    }

    public boolean debugMessages() {
        return debugMessages;
    }

    public void setDebugMessages(boolean debugMessages) {
        this.debugMessages = debugMessages;
        save();
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

    public boolean roomSyncEnabled() {
        return roomSyncEnabled;
    }

    public void setRoomSyncEnabled(boolean roomSyncEnabled) {
        this.roomSyncEnabled = roomSyncEnabled;
    }

    public boolean roomSyncUploadEnabled() {
        return roomSyncUploadEnabled;
    }

    public void setRoomSyncUploadEnabled(boolean roomSyncUploadEnabled) {
        this.roomSyncUploadEnabled = roomSyncUploadEnabled;
    }

    public String roomSyncServerUrl() {
        return roomSyncServerUrl == null ? "" : roomSyncServerUrl;
    }

    public void setRoomSyncServerUrl(String roomSyncServerUrl) {
        this.roomSyncServerUrl = roomSyncServerUrl == null ? "" : roomSyncServerUrl.trim();
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

    public boolean lobbyHopHelperEnabled() {
        return lobbyHopHelperEnabled;
    }

    public void setLobbyHopHelperEnabled(boolean lobbyHopHelperEnabled) {
        this.lobbyHopHelperEnabled = lobbyHopHelperEnabled;
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
        if (!Files.exists(file)) {
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
                if (saved.dungeonMap.playerTrackingEnabled != null) {
                    setPlayerTrackingEnabled(saved.dungeonMap.playerTrackingEnabled);
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
                if (saved.dungeonMap.roomSyncServerUrl != null) {
                    setRoomSyncServerUrl(saved.dungeonMap.roomSyncServerUrl);
                }
                if (saved.dungeonMap.roomSyncToken != null) {
                    setRoomSyncToken(saved.dungeonMap.roomSyncToken);
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
            if (saved != null && saved.debug != null && saved.debug.interfaceMessages != null) {
                setDebugInterfaceMessages(saved.debug.interfaceMessages);
            }
            if (saved != null && saved.misc != null) {
                if (saved.misc.lobbyHopHelperEnabled != null) {
                    setLobbyHopHelperEnabled(saved.misc.lobbyHopHelperEnabled);
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

    private static Path configFile() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve("kung.json");
    }

    private record SavedConfig(
        SavedDungeonMap dungeonMap,
        SavedSplitsOverlay splitsOverlay,
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
                    config.playerTrackingEnabled,
                    config.debugMessages,
                    config.debugRoomCrypts,
                    config.debugRoomMatches,
                    config.localRoomDataEnabled,
                    config.roomSyncServerUrl,
                    config.roomSyncToken
                ),
                new SavedSplitsOverlay(
                    config.splitsEnabled,
                    config.splitsX,
                    config.splitsY,
                    config.splitsScale
                ),
                new SavedDebug(
                    config.debugInterfaceMessages
                ),
                new SavedMisc(
                    config.lobbyHopHelperEnabled,
                    config.superpairsHelperEnabled,
                    config.superpairsHelperDebugEnabled,
                    config.superpairsHelperX,
                    config.superpairsHelperY,
                    config.superpairsHelperScale
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
        Boolean playerTrackingEnabled,
        Boolean debugMessages,
        Boolean debugRoomCrypts,
        Boolean debugRoomMatches,
        Boolean localRoomDataEnabled,
        String roomSyncServerUrl,
        String roomSyncToken
    ) {
    }

    private record SavedSplitsOverlay(
        boolean enabled,
        int x,
        int y,
        int scale
    ) {
    }

    private record SavedDebug(Boolean interfaceMessages) {
    }

    private record SavedMisc(
        Boolean lobbyHopHelperEnabled,
        Boolean superpairsHelperEnabled,
        Boolean superpairsHelperDebugEnabled,
        Integer superpairsHelperX,
        Integer superpairsHelperY,
        Integer superpairsHelperScale
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
