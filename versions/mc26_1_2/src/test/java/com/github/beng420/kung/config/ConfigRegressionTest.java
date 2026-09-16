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
        equal(false, defaults.dungeon.extraScoreMessagesEnabled(), "extra score messages default off for existing configs too");
        equal(true, defaults.dungeon.mimicMessageEnabled(), "mimic subsetting defaults on");
        equal(true, defaults.dungeon.princeMessageEnabled(), "prince subsetting defaults on");
        equal(true, defaults.dungeon.batMessageEnabled(), "bat subsetting defaults on");
        equal(85, defaults.splits.scale(), "missing splits use defaults");
        equal(false, defaults.splits.enabled(), "splits master stays disabled by default");
        equal(SplitsConfig.TimeFormat.MINUTES, defaults.splits.format(), "existing splits retain minute format");
        equal(true, defaults.splits.timeLost(), "existing splits retain time loss display");
        equal(true, defaults.misc.cataPartyCommandsEnabled(), "missing command channels use defaults");
        equal(true, defaults.misc.c50ChatCommandEnabled(), "c50 command defaults on");
        equal(true, defaults.misc.ca50ChatCommandEnabled(), "ca50 command defaults on");
        equal(true, defaults.misc.tpsChatCommandEnabled(), "tps command defaults on");
        equal(false, defaults.misc.chatEmotesEnabled(), "chat emotes default off for existing configs too");
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
    public void visitorAlarmDefaultsOffAndNormalizesVolume() {
        equal(false, read("{}").visitorAlarm.enabled(), "new alarm defaults off");
        equal(false, read("{\"visitorAlarm\":null}").visitorAlarm.enabled(), "null category defaults off");
        equal(70, read("{}").visitorAlarm.volume(), "audible default volume");
        KungConfig loaded = read("{\"visitorAlarm\":{\"enabled\":true,\"volume\":999}}");
        equal(true, loaded.visitorAlarm.enabled(), "enabled setting retained");
        equal(100, loaded.visitorAlarm.volume(), "volume upper bound");
        equal(1, read("{\"visitorAlarm\":{\"volume\":0}}").visitorAlarm.volume(), "volume lower bound");
    }

    @Test
    public void feastSettingsDefaultOffAndPreserveHudCoordinatesOnReload() {
        equal(false, read("{}").feast.enabled(), "existing configurations keep the new overlay off");
        equal(true, read("{}").feast.showInHubFarm(), "Hub farm subsetting defaults on");
        equal(60, read("{}").feast.kernelTimeoutSeconds(), "old configurations receive a one-minute timeout");
        equal(10, read("{\"feast\":{\"kernelTimeoutSeconds\":0}}").feast.kernelTimeoutSeconds(), "timeout lower bound on load");
        equal(300, read("{\"feast\":{\"kernelTimeoutSeconds\":999}}").feast.kernelTimeoutSeconds(), "timeout upper bound on load");
        equal(false, read("{\"feast\":{\"showInHubFarm\":false}}").feast.showInHubFarm(), "Hub farm can be disabled");
        equal(false, read("{\"feast\":null}").feast.enabled(), "null category uses defaults");
        KungConfig config = read("""
            {"feast":{"enabled":true,"x":321,"y":123,"scale":900},"dungeonMap":{"x":42}}
            """);
        equal(true, config.feast.enabled(), "feature enabled retained");
        equal(321, config.feast.x(), "HUD x retained");
        equal(123, config.feast.y(), "HUD y retained");
        equal(300, config.feast.scale(), "HUD scale clamped");
        equal(42, config.dungeon.x(), "existing HUD placement retained");
        equal(25, read("{\"feast\":{\"scale\":0}}").feast.scale(), "minimum HUD scale");
    }

    @Test
    public void kernelTimeoutTextAcceptsSecondsAndRetainsTheValueForInvalidInput() {
        var config = read("{}");
        var field = SettingEntry.text("Kernel Timeout (s)", () -> Integer.toString(config.feast.kernelTimeoutSeconds()),
            config.feast::setKernelTimeoutText);
        field.setText(" 75 ");
        equal("75", field.textValue(), "integer seconds accepted");
        for (String text : new String[] {"", "abc", "1.5", "999999999999999999999", null}) {
            field.setText(text);
            equal("75", field.textValue(), "invalid input keeps current timeout");
        }
        field.setText("5");
        equal("10", field.textValue(), "small input clamps to ten seconds");
        field.setText("900");
        equal("300", field.textValue(), "large input clamps to five minutes");
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
            config.dungeon.setExtraScoreMessagesEnabled(true);
            config.dungeon.setMimicMessageEnabled(false);
            config.dungeon.setPrinceMessageEnabled(false);
            config.dungeon.setBatMessageEnabled(false);
            config.bloodRush.setEnabled(true);
            config.chatFilter.setNecron(false);
            config.splits.setY(123);
            config.splits.setFormat(SplitsConfig.TimeFormat.SECONDS);
            config.splits.setTimeLost(false);
            config.feast.setEnabled(true);
            config.feast.setShowInHubFarm(false);
            config.feast.setKernelTimeoutText("75");
            config.feast.setX(321);
            config.feast.setY(123);
            config.feast.setScale(150);
            config.visitorAlarm.setEnabled(true);
            config.visitorAlarm.setVolume(35);
            config.debug.setTarantulaMessages(false);
            config.slayer.setEggSacPredictionEnabled(true);
            config.misc.setLoadoutKeybind(2, "mouse:3");
            config.misc.setChatEmotesEnabled(true);
            config.misc.setCustomArrowHitSounds("\"C:/sounds/ping.wav\", 'bell.wav'");
            String saved = Files.readString(file);
            KungConfig roundTrip = read(saved);
            equal(true, roundTrip.visitorAlarm.enabled(), "visitor alarm persisted");
            equal(35, roundTrip.visitorAlarm.volume(), "visitor volume persisted");
            equal(77, roundTrip.dungeon.x(), "map setter persists");
            equal(250, roundTrip.dungeon.scale(), "map enlargement beyond old cap persists");
            equal(175, roundTrip.dungeon.textScale(), "map text size persists independently");
            equal(true, roundTrip.dungeon.mimicEspEnabled(), "mimic esp setter persists");
            equal(true, roundTrip.dungeon.extraScoreMessagesEnabled(), "extra score master persists");
            equal(false, roundTrip.dungeon.mimicMessageEnabled(), "mimic message switch persists");
            equal(false, roundTrip.dungeon.princeMessageEnabled(), "prince message switch persists");
            equal(false, roundTrip.dungeon.batMessageEnabled(), "bat message switch persists");
            equal(true, roundTrip.bloodRush.enabled(), "blood rush setter persists");
            equal(false, roundTrip.chatFilter.necron(), "filter setter persists");
            equal(123, roundTrip.splits.y(), "splits setter persists");
            equal(SplitsConfig.TimeFormat.SECONDS, roundTrip.splits.format(), "splits Seconds format persists");
            equal(false, roundTrip.splits.timeLost(), "hidden split time loss persists");
            equal(true, roundTrip.feast.enabled(), "Feast toggle persists");
            equal(false, roundTrip.feast.showInHubFarm(), "Hub farm toggle persists");
            equal(75, roundTrip.feast.kernelTimeoutSeconds(), "timeout text persists as integer seconds");
            equal(321, roundTrip.feast.x(), "Feast x persists");
            equal(123, roundTrip.feast.y(), "Feast y persists");
            equal(150, roundTrip.feast.scale(), "Feast scale persists");
            equal(false, roundTrip.debug.tarantulaMessages(), "debug setter persists");
            equal(true, roundTrip.slayer.eggSacPredictionEnabled(), "slayer setter persists");
            equal("mouse:3", roundTrip.misc.loadoutKeybind(2), "keybind setter persists");
            equal(true, roundTrip.misc.chatEmotesEnabled(), "chat emotes switch persists");
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
