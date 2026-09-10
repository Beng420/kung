package com.github.beng420.kung.config;

import com.github.beng420.kung.KungMod;
import com.github.beng420.kung.config.category.BRHelperConfig;
import com.github.beng420.kung.config.category.DebugConfig;
import com.github.beng420.kung.config.category.DungeonChatFilterConfig;
import com.github.beng420.kung.config.category.DungeonConfig;
import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.config.category.SlayerConfig;
import com.github.beng420.kung.config.category.SplitsConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class KungConfig {
    public static final KungConfig INSTANCE = new KungConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("dungeonMap")
    public DungeonConfig dungeon = new DungeonConfig();
    @SerializedName("bloodRushHelper")
    public BRHelperConfig bloodRush = new BRHelperConfig();
    @SerializedName("dungeonChatFilter")
    public DungeonChatFilterConfig chatFilter = new DungeonChatFilterConfig();
    @SerializedName("splitsOverlay")
    public SplitsConfig splits = new SplitsConfig();
    public DebugConfig debug = new DebugConfig();
    public MiscConfig misc = new MiscConfig();
    public SlayerConfig slayer = new SlayerConfig();

    private transient Path explicitConfigFile;
    private transient boolean loading;

    private KungConfig() {
        bindCategories();
    }

    KungConfig(Path configFile) {
        this();
        explicitConfigFile = configFile;
    }

    public static KungConfig get() {
        return INSTANCE;
    }

    public void load() {
        Path file = configFile();
        boolean migrateLegacy = false;
        if (explicitConfigFile == null && !Files.exists(file) && Files.exists(legacyConfigFile())) {
            file = legacyConfigFile();
            migrateLegacy = true;
        }

        boolean exists = Files.exists(file);
        boolean loadedSuccessfully = !exists;
        loading = true;
        try {
            if (exists) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    KungConfig loaded = read(reader);
                    dungeon = loaded.dungeon;
                    bloodRush = loaded.bloodRush;
                    chatFilter = loaded.chatFilter;
                    splits = loaded.splits;
                    debug = loaded.debug;
                    misc = loaded.misc;
                    slayer = loaded.slayer;
                    loadedSuccessfully = true;
                }
            }
            bindCategories();
            normalize();
            applyBundledDefaults();
        } catch (IOException | RuntimeException exception) {
            KungMod.LOGGER.warn("Failed to load kung config.", exception);
        } finally {
            bindCategories();
            loading = false;
        }
        if (loadedSuccessfully && (!exists || migrateLegacy)) {
            save();
        }
    }

    static KungConfig read(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        KungConfig loaded = GSON.fromJson(json, KungConfig.class);
        loaded.loading = true;
        loaded.ensureCategories();
        // Older releases stored these switches in both the feature and debug sections.
        JsonObject dungeonJson = object(json, "dungeonMap");
        JsonObject slayerJson = object(json, "slayer");
        JsonObject debugJson = object(json, "debug");
        JsonObject miscJson = object(json, "misc");
        if (!hasValue(debugJson, "dungeonMessages") && hasValue(dungeonJson, "debugMessages")) {
            loaded.debug.setDungeonMessages(dungeonJson.get("debugMessages").getAsBoolean());
        }
        if (hasValue(slayerJson, "tarantulaHelperDebugMessages")) {
            loaded.debug.setTarantulaMessages(slayerJson.get("tarantulaHelperDebugMessages").getAsBoolean());
        }
        migrateLegacyChatCommandSettings(loaded, miscJson);
        loaded.normalize();
        loaded.loading = false;
        return loaded;
    }

    public void save() {
        if (loading) return;
        try {
            Path file = configFile();
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to save kung config.", exception);
        }
    }

    private void ensureCategories() {
        if (dungeon == null) dungeon = new DungeonConfig();
        if (bloodRush == null) bloodRush = new BRHelperConfig();
        if (chatFilter == null) chatFilter = new DungeonChatFilterConfig();
        if (splits == null) splits = new SplitsConfig();
        if (debug == null) debug = new DebugConfig();
        if (misc == null) misc = new MiscConfig();
        if (slayer == null) slayer = new SlayerConfig();
    }

    private void bindCategories() {
        dungeon.onChange(this::save);
        bloodRush.onChange(this::save);
        chatFilter.onChange(this::save);
        splits.onChange(this::save);
        debug.onChange(this::save);
        misc.onChange(this::save);
        slayer.onChange(this::save);
    }

    private void normalize() {
        ensureCategories();
        dungeon.setScale(dungeon.scale());
        dungeon.setTextScale(dungeon.textScale());
        dungeon.setUnopenedRoomAlpha(dungeon.unopenedRoomAlpha());
        dungeon.setFiveCryptPartyMessage(dungeon.fiveCryptPartyMessage());
        dungeon.setRoomSyncServerUrl(dungeon.roomSyncServerUrl());
        dungeon.setRoomSyncToken(dungeon.roomSyncToken());
        bloodRush.setTitleDurationTenths(bloodRush.titleDurationTenths());
        splits.setScale(splits.scale());
        splits.setFormat(splits.format());
        misc.setHypixelApiKey(misc.hypixelApiKey());
        misc.setSuperpairsHelperScale(misc.superpairsHelperScale());
        misc.setCustomArrowHitSounds(misc.customArrowHitSounds());
        misc.setCustomWitherShieldExpireSounds(misc.customWitherShieldExpireSounds());
        misc.setCustomArrowHitVolumeTenths(misc.customArrowHitVolumeTenths());
        misc.setCustomArrowHitPitchHundredths(misc.customArrowHitPitchHundredths());
        misc.setCustomWitherShieldExpireVolumeTenths(misc.customWitherShieldExpireVolumeTenths());
        misc.setCustomWitherShieldExpirePitchHundredths(misc.customWitherShieldExpirePitchHundredths());
        misc.normalize();
        slayer.setEggSacPredictionRenderMode(slayer.eggSacPredictionRenderMode());
    }

    private void applyBundledDefaults() {
        Properties defaults = new Properties();
        try (InputStream stream = KungConfig.class.getResourceAsStream("/kung-defaults.properties")) {
            if (stream != null) defaults.load(stream);
        } catch (IOException exception) {
            KungMod.LOGGER.warn("Failed to load bundled kung defaults.", exception);
        }
        if (dungeon.roomSyncServerUrl().isBlank()) {
            dungeon.setRoomSyncServerUrl(defaults.getProperty("roomSync.serverUrl", ""));
        }
        if (dungeon.roomSyncToken().isBlank()) {
            dungeon.setRoomSyncToken(defaults.getProperty("roomSync.token", ""));
        }
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent.has(name) && parent.get(name).isJsonObject()
            ? parent.getAsJsonObject(name) : new JsonObject();
    }

    private static boolean hasValue(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull();
    }

    private static void migrateLegacyChatCommandSettings(KungConfig loaded, JsonObject miscJson) {
        migrateLegacySharedCommandSetting(
            miscJson,
            "cataChatCommandEnabled",
            "c50ChatCommandEnabled",
            "ca50ChatCommandEnabled",
            loaded.misc::setC50ChatCommandEnabled,
            loaded.misc::setCa50ChatCommandEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "ca50ChatCommandEnabled",
            "c50ChatCommandEnabled",
            "ca50ChatCommandEnabled",
            loaded.misc::setC50ChatCommandEnabled,
            loaded.misc::setCa50ChatCommandEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "cataPartyCommandsEnabled",
            "c50PartyCommandsEnabled",
            "ca50PartyCommandsEnabled",
            loaded.misc::setC50PartyCommandsEnabled,
            loaded.misc::setCa50PartyCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "ca50PartyCommandsEnabled",
            "c50PartyCommandsEnabled",
            "ca50PartyCommandsEnabled",
            loaded.misc::setC50PartyCommandsEnabled,
            loaded.misc::setCa50PartyCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "cataGuildCommandsEnabled",
            "c50GuildCommandsEnabled",
            "ca50GuildCommandsEnabled",
            loaded.misc::setC50GuildCommandsEnabled,
            loaded.misc::setCa50GuildCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "ca50GuildCommandsEnabled",
            "c50GuildCommandsEnabled",
            "ca50GuildCommandsEnabled",
            loaded.misc::setC50GuildCommandsEnabled,
            loaded.misc::setCa50GuildCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "cataAllChatCommandsEnabled",
            "c50AllChatCommandsEnabled",
            "ca50AllChatCommandsEnabled",
            loaded.misc::setC50AllChatCommandsEnabled,
            loaded.misc::setCa50AllChatCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "ca50AllChatCommandsEnabled",
            "c50AllChatCommandsEnabled",
            "ca50AllChatCommandsEnabled",
            loaded.misc::setC50AllChatCommandsEnabled,
            loaded.misc::setCa50AllChatCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "cataPrivateCommandsEnabled",
            "c50PrivateCommandsEnabled",
            "ca50PrivateCommandsEnabled",
            loaded.misc::setC50PrivateCommandsEnabled,
            loaded.misc::setCa50PrivateCommandsEnabled
        );
        migrateLegacySharedCommandSetting(
            miscJson,
            "ca50PrivateCommandsEnabled",
            "c50PrivateCommandsEnabled",
            "ca50PrivateCommandsEnabled",
            loaded.misc::setC50PrivateCommandsEnabled,
            loaded.misc::setCa50PrivateCommandsEnabled
        );
    }

    private static void migrateLegacySharedCommandSetting(
        JsonObject miscJson,
        String legacyName,
        String c50Name,
        String ca50Name,
        java.util.function.Consumer<Boolean> c50Setter,
        java.util.function.Consumer<Boolean> ca50Setter
    ) {
        if (!hasValue(miscJson, legacyName)) {
            return;
        }
        boolean legacyValue = miscJson.get(legacyName).getAsBoolean();
        if (!hasValue(miscJson, c50Name)) {
            c50Setter.accept(legacyValue);
        }
        if (!hasValue(miscJson, ca50Name)) {
            ca50Setter.accept(legacyValue);
        }
    }

    public static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("kung");
    }

    private Path configFile() {
        return explicitConfigFile == null ? configDirectory().resolve("kung.json") : explicitConfigFile;
    }

    private static Path legacyConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("kung.json");
    }
}
