package com.github.beng420.kung.config;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.config.category.SlayerConfig;
import com.github.beng420.kung.config.category.SplitsConfig;
import com.github.beng420.kung.feature.ConfigurableFeature;
import com.github.beng420.kung.feature.Feature;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.runtime.AppServices;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.Test;

public final class ConfigRegressionTest {
    private static KungConfig read(String json) {
        return KungConfig.read(new StringReader(json));
    }

    @Test
    public void defaultsAndLegacySettings() {
        KungConfig defaults = read("{}");
        equal(100, defaults.dungeon.scale(), "missing map uses defaults");
        equal(100, defaults.dungeon.textScale(), "existing configs keep their map text size");
        equal(false, defaults.dungeon.mimicEspEnabled(), "mimic esp defaults off");
        equal(85, defaults.splits.scale(), "missing splits use defaults");
        equal(false, defaults.splits.enabled(), "splits master stays disabled by default");
        equal(SplitsConfig.TimeFormat.MINUTES, defaults.splits.format(), "existing splits retain minute format");
        equal(true, defaults.splits.timeLost(), "existing splits retain time loss display");
        equal(true, defaults.misc.cataPartyCommandsEnabled(), "missing command channels use defaults");
        equal(true, defaults.misc.c50ChatCommandEnabled(), "c50 command defaults on");
        equal(true, defaults.misc.ca50ChatCommandEnabled(), "ca50 command defaults on");
        equal(true, defaults.misc.tpsChatCommandEnabled(), "tps command defaults on");
        equal(false, defaults.misc.loadoutsCloseOnlyOnChange(), "loadouts change-only close defaults off");
        KungConfig legacy = read("""
            {
              "dungeonMap": {"enabled": true, "x": 42, "debugMessages": false, "unopenedRoomAlpha": 0},
              "bloodRushHelper": {"enabled": true, "titleDurationTenths": 9},
              "splitsOverlay": {"x": 99},
              "dungeonChatFilter": {"blessings": false, "necron": false},
              "misc": {"ca50ChatCommandEnabled": false, "ca50GuildCommandsEnabled": false},
              "debug": {"tarantulaMessages": true},
              "slayer": {"tarantulaHelperDebugMessages": false, "eggSacPredictionEnabled": true}
            }
            """);
        equal(true, legacy.dungeon.enabled(), "map enabled retained");
        equal(42, legacy.dungeon.x(), "map position retained");
        equal(0, legacy.dungeon.unopenedRoomAlpha(), "zero transparency retained");
        equal(false, legacy.debug.dungeonMessages(), "legacy dungeon debug migrated");
        equal(false, legacy.debug.tarantulaMessages(), "legacy slayer debug precedence retained");
        equal(true, legacy.slayer.eggSacPredictionEnabled(), "prediction retained");
        equal(false, legacy.misc.cataChatCommandEnabled(), "ca50 alias migrated");
        equal(false, legacy.misc.c50ChatCommandEnabled(), "legacy ca50 command master also migrates to c50");
        equal(false, legacy.misc.ca50ChatCommandEnabled(), "legacy ca50 command master migrates to ca50");
        equal(false, legacy.misc.cataGuildCommandsEnabled(), "ca50 channel migrated");
        equal(false, legacy.misc.c50GuildCommandsEnabled(), "legacy ca50 guild channel also migrates to c50");
        equal(false, legacy.misc.ca50GuildCommandsEnabled(), "legacy ca50 guild channel migrates to ca50");
        equal(9, legacy.bloodRush.titleDurationTenths(), "blood rush timing retained");
        equal(99, legacy.splits.x(), "splits position retained");
        equal(false, legacy.chatFilter.necron(), "boss filter retained");
        KungConfig modern = read("""
            {"debug":{"dungeonMessages":true,"tarantulaMessages":false},
             "dungeonMap":{"debugMessages":false},
             "misc":{"cataChatCommandEnabled":false}}
            """);
        equal(true, modern.debug.dungeonMessages(), "modern dungeon debug takes precedence");
        equal(false, modern.debug.tarantulaMessages(), "modern tarantula debug retained");
        equal(false, modern.misc.cataChatCommandEnabled(), "modern command name retained");
        equal(false, modern.misc.c50ChatCommandEnabled(), "modern cata alias migrates to c50");
        equal(false, modern.misc.ca50ChatCommandEnabled(), "modern cata alias migrates to ca50");
    }

    @Test
    public void invalidValues() {
        KungConfig config = read("""
            {
              "dungeonMap":{"scale":0,"textScale":900,"unopenedRoomAlpha":900,"roomSyncServerUrl":" localhost/ ",
                            "roomSyncToken":null,"fiveCryptPartyMessage":null},
              "bloodRushHelper":{"titleDurationTenths":100},
              "splitsOverlay":{"scale":900},
              "slayer":{"eggSacPredictionRenderMode":"unknown"},
              "misc":{"loadoutKeybinds":[null,"mouse:4"],"hypixelApiKey":null,
                      "superpairsHelperScale":0,"customArrowHitSounds":null,
                      "loadoutsCloseOnlyOnChange":true,
                      "customArrowHitSoundVolumeTenths":{"ping.wav":90,"bad.wav":null},
                      "customWitherShieldExpireSoundPitchHundredths":null},
              "debug":null,"dungeonChatFilter":null
            }
            """);
        equal(25, config.dungeon.scale(), "map scale clamped");
        equal(200, config.dungeon.textScale(), "map text scale clamped");
        equal(28, config.dungeon.unopenedRoomAlpha(), "alpha clamped");
        equal("http://localhost:8765", config.dungeon.roomSyncServerUrl(), "server normalized");
        equal("", config.dungeon.roomSyncToken(), "null token normalized");
        equal(50, config.bloodRush.titleDurationTenths(), "title duration clamped");
        equal(300, config.splits.scale(), "splits scale clamped");
        equal(SlayerConfig.EggSacPredictionRenderMode.BOX, config.slayer.eggSacPredictionRenderMode(), "unknown mode defaulted");
        equal(false, config.misc.directHypixelApiEnabled(), "null API key handled");
        equal(12, config.misc.loadoutKeybindCount(), "partial keybind array expanded");
        equal("key:49:0", config.misc.loadoutKeybind(0), "null keybind uses default");
        equal("mouse:4", config.misc.loadoutKeybind(1), "custom keybind retained");
        equal(true, config.misc.loadoutsCloseOnlyOnChange(), "loadouts change-only close retained");
        equal(50, config.misc.customArrowHitSoundVolumeTenths("ping.wav"), "per-sound volume clamped");
        equal(100, config.misc.customWitherShieldExpireSoundPitchHundredths("ping.wav"), "missing map uses fallback");
        equal(false, config.debug.enabled(), "null category defaulted");
        equal(true, config.chatFilter.blessings(), "null filter defaulted");
        equal(12, read("{\"misc\":{\"loadoutKeybinds\":null}}").misc.loadoutKeybindCount(), "null keybind array handled");
    }

    @Test
    public void splitTimeFormatHandlesInvalidSettings() {
        for (String value : new String[] {"null", "\"unknown\""}) {
            KungConfig config = read("{\"splitsOverlay\":{\"format\":" + value + "}}");
            equal(SplitsConfig.TimeFormat.MINUTES, config.splits.format(), "invalid format falls back to Minutes");
            equal("Minutes", config.splits.formatLabel(), "invalid format has a safe UI label");
        }
        SplitsConfig splits = new SplitsConfig();
        splits.cycleFormat();
        equal(SplitsConfig.TimeFormat.SECONDS, splits.format(), "choice selects Seconds");
        splits.cycleFormat();
        equal(SplitsConfig.TimeFormat.MINUTES, splits.format(), "choice returns to Minutes");
        splits.setFormat(null);
        equal(SplitsConfig.TimeFormat.MINUTES, splits.format(), "null setter uses Minutes");
    }

    @Test
    public void persistence() throws Exception {
        Path directory = Files.createTempDirectory("kung-config-test-");
        Path file = directory.resolve("kung.json");
        try {
            KungConfig config = new KungConfig(file);
            config.load();
            config.dungeon.setX(77);
            config.dungeon.setScale(250);
            config.dungeon.setTextScale(175);
            config.dungeon.setMimicEspEnabled(true);
            config.bloodRush.setEnabled(true);
            config.chatFilter.setNecron(false);
            config.splits.setY(123);
            config.splits.setFormat(SplitsConfig.TimeFormat.SECONDS);
            config.splits.setTimeLost(false);
            config.debug.setTarantulaMessages(false);
            config.slayer.setEggSacPredictionEnabled(true);
            config.misc.setLoadoutKeybind(2, "mouse:3");
            config.misc.setCustomArrowHitSounds("\"C:/sounds/ping.wav\", 'bell.wav'");
            String saved = Files.readString(file);
            KungConfig roundTrip = read(saved);
            equal(77, roundTrip.dungeon.x(), "map setter persists");
            equal(250, roundTrip.dungeon.scale(), "map enlargement beyond old cap persists");
            equal(175, roundTrip.dungeon.textScale(), "map text size persists independently");
            equal(true, roundTrip.dungeon.mimicEspEnabled(), "mimic esp setter persists");
            equal(true, roundTrip.bloodRush.enabled(), "blood rush setter persists");
            equal(false, roundTrip.chatFilter.necron(), "filter setter persists");
            equal(123, roundTrip.splits.y(), "splits setter persists");
            equal(SplitsConfig.TimeFormat.SECONDS, roundTrip.splits.format(), "splits Seconds format persists");
            equal(false, roundTrip.splits.timeLost(), "hidden split time loss persists");
            equal(false, roundTrip.debug.tarantulaMessages(), "debug setter persists");
            equal(true, roundTrip.slayer.eggSacPredictionEnabled(), "slayer setter persists");
            equal("mouse:3", roundTrip.misc.loadoutKeybind(2), "keybind setter persists");
            equal("ping.wav, bell.wav", roundTrip.misc.customArrowHitSounds(), "quoted sound paths normalized");
            equal(false, saved.contains("tarantulaHelperDebugMessages"), "duplicate legacy debug key removed");
            equal(false, saved.contains("onChange"), "save callbacks are not serialized");
            equal(false, saved.contains("explicitConfigFile"), "test path is not serialized");
            config.load();
            config.dungeon.setX(88);
            equal(88, read(Files.readString(file)).dungeon.x(), "reloaded category saves to its owner");
            Files.writeString(file, "{broken");
            config.load();
            equal(88, config.dungeon.x(), "failed reload retains active settings");
            equal("{broken", Files.readString(file), "failed load does not overwrite source");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void featureLifecycle() {
        CountingFeature feature = new CountingFeature();
        AppServices services = AppServices.create(KungConfig.get(), new DungeonStateTracker());
        MiscConfig previous = KungConfig.get().misc;
        try {
            feature.initialize(services);
            feature.initialize(services);
            equal(1, feature.initializations, "feature registers events once");
            equal(true, feature.initialized(), "feature exposes initialized lifecycle state");
            MiscConfig replacement = new MiscConfig();
            replacement.setLobbyHopHelperEnabled(true);
            KungConfig.get().misc = replacement;
            equal(true, feature.isEnabled(), "feature follows replaced config");
            replacement.setLobbyHopHelperEnabled(false);
            equal(false, feature.isEnabled(), "feature follows live toggle");
            feature.reset();
            equal(1, feature.resets, "feature reset hook runs while initialized");
            feature.shutdown();
            equal(false, feature.initialized(), "feature shutdown clears lifecycle state");
        } finally {
            KungConfig.get().misc = previous;
        }
    }

    private static void equal(Object expected, Object actual, String description) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class CountingFeature extends ConfigurableFeature<MiscConfig> implements Feature {
        private int initializations;
        private int resets;

        private CountingFeature() { super(config -> config.misc); }
        @Override protected void onInitialize() { initializations++; }
        @Override protected void onReset() { resets++; }
        @Override public boolean isEnabled() { return config().lobbyHopHelperEnabled(); }
    }
}
