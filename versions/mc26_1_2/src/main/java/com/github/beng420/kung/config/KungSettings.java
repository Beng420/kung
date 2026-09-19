package com.github.beng420.kung.config;

import com.github.beng420.kung.config.category.DungeonConfig.DragonDebuffScope;
import com.github.beng420.kung.config.category.DungeonConfig.PillarMaterial;
import com.github.beng420.kung.config.category.SlayerConfig.EggSacPredictionRenderMode;
import com.github.beng420.kung.config.category.SplitsConfig.PredictionMode;
import com.github.beng420.kung.config.category.SplitsConfig.PredictionSource;
import com.github.beng420.kung.config.category.SplitsConfig.TimeFormat;
import com.github.beng420.kung.feature.dungeon.DungeonKnownRoomCatalog;
import com.github.beng420.kung.feature.dungeon.DungeonRoomClassifier;
import com.github.beng420.kung.feature.dungeon.DungeonRoomDataSyncClient;
import com.github.beng420.kung.feature.misc.CustomSoundsFeature;
import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.runtime.KungDeveloperAccess;
import com.github.beng420.kung.update.KungUpdater;
import com.github.beng420.kung.util.HypixelSkyBlockProfileClient;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** Shared settings and callbacks; persistence remains owned by KungConfig. */
public final class KungSettings {
    private KungSettings() { }

    public static List<CategoryEntry> categories(Runnable openChangelog) {
        return categories(KungConfig.get(), openChangelog);
    }

    public static List<CategoryEntry> defaults() {
        return categories(KungConfig.read(new StringReader("{}")), () -> { });
    }

    static List<CategoryEntry> categories(KungConfig config, Runnable openChangelog) {
        return List.of(
            new CategoryEntry("Dungeon", List.of(
                new FeatureEntry("Dungeon Map", config.dungeon::enabled,
                    () -> config.dungeon.setEnabled(!config.dungeon.enabled()),
                    List.of(
                        SettingEntry.stepper("Unopened Alpha", config.dungeon::unopenedRoomAlpha,
                            config.dungeon::setUnopenedRoomAlpha, 0, 28, 2),
                        SettingEntry.toggle("Legend", config.dungeon::showLegend,
                            () -> config.dungeon.setShowLegend(!config.dungeon.showLegend())),
                        SettingEntry.toggle("Boss Map", config.dungeon::showInBoss,
                            () -> config.dungeon.setShowInBoss(!config.dungeon.showInBoss())),
                        SettingEntry.toggle("Prince Icons", config.dungeon::princeIconsEnabled,
                            () -> config.dungeon.setPrinceIconsEnabled(!config.dungeon.princeIconsEnabled())),
                        SettingEntry.toggle("Mimic ESP", config.dungeon::mimicEspEnabled,
                            () -> config.dungeon.setMimicEspEnabled(!config.dungeon.mimicEspEnabled())),
                        SettingEntry.toggle("Force Paul", config.dungeon::forcePaulScoreEnabled,
                            () -> config.dungeon.setForcePaulScoreEnabled(!config.dungeon.forcePaulScoreEnabled())),
                        SettingEntry.toggle("Room Debug", config.dungeon::debugRoomMatches,
                            () -> config.dungeon.setDebugRoomMatches(!config.dungeon.debugRoomMatches())),
                        SettingEntry.toggle("Local Data", config.dungeon::localRoomDataEnabled,
                            () -> toggleLocalRoomData(config))
                    )
                ),
                new FeatureEntry("Ice Spray Highlight", config.dungeon::iceSprayHighlightEnabled,
                    () -> config.dungeon.setIceSprayHighlightEnabled(!config.dungeon.iceSprayHighlightEnabled()), List.of(
                        SettingEntry.slider("Box Size (%)", config.dungeon::iceSprayBoxSize,
                            config.dungeon::setIceSprayBoxSize, 100, 200, 5)
                            .withTooltip("100% = normal size; 200% = twice the width, height and depth.",
                                "Expands the highlight around the mob's center.")
                    ))
                    .withTooltip("Light-blue mob boxes with 20% fill for observed Ice Spray effects.",
                        "Includes other players' sprays. Overlapping or missing ice markers stay unknown."),
                new FeatureEntry("M7 Dragon Debuff", config.dungeon::dragonDebuffEnabled,
                    () -> config.dungeon.setDragonDebuffEnabled(!config.dungeon.dragonDebuffEnabled()), List.of(
                        SettingEntry.choice("Track", DragonDebuffScope.values(), config.dungeon::dragonDebuffScope,
                            config.dungeon::setDragonDebuffScope, DragonDebuffScope::label)
                            .withTooltip("All Dragons: show every dragon in the HUD and chat.",
                                "Nearest Statue: choose the closest statue among each wave's spawning dragons.",
                                "Uses your position at the first spawn and keeps that target until the next wave.")
                    ))
                    .withTooltip("Compact dragon times, early arrows and Ice Spray hits in the HUD and local chat.",
                        "Move and scale the HUD with /kung hud.",
                        "Hover the chat report for first/fifth hits, rate and hit ticks.",
                        "Arrow feedback counts for the first 40 server ticks at your nearest spawning statue.",
                        "Other dragons show -- for arrows. Each respawn starts fresh."),
                new FeatureEntry("M7 Dragon Helper", config.dungeon::m7DragonHelperEnabled,
                    () -> config.dungeon.setM7DragonHelperEnabled(!config.dungeon.m7DragonHelperEnabled()),
                    dragonHelperSettings(config, KungDeveloperAccess.allowed()))
                    .withTooltip("Dragon spawn markers, statue boxes and local count notifications in M7.",
                        "Statue boxes estimate the range; count results require server evidence."),
                new FeatureEntry("Colored F7/M7 Pillars", config.dungeon::coloredPillarsEnabled,
                    () -> config.dungeon.setColoredPillarsEnabled(!config.dungeon.coloredPillarsEnabled()), List.of(
                        SettingEntry.choice("Material", PillarMaterial.values(), config.dungeon::pillarMaterial,
                            config.dungeon::setPillarMaterial, PillarMaterial::label)
                    ))
                    .withTooltip("Show Storm's crush pillars in their matching colors in F7 and M7."),
                new FeatureEntry("Player Stats", config.dungeon::playerTrackingEnabled,
                    () -> config.dungeon.setPlayerTrackingEnabled(!config.dungeon.playerTrackingEnabled()),
                    List.of()
                ),
                new FeatureEntry("Death Messages", config.dungeon::deathMessagesEnabled,
                    () -> config.dungeon.setDeathMessagesEnabled(!config.dungeon.deathMessagesEnabled()),
                    List.of(
                        SettingEntry.group("Announce To Party").withChildren(List.of(
                            SettingEntry.toggle("Share Total", config.dungeon::deathMessagesShareTotalEnabled,
                                () -> config.dungeon.setDeathMessagesShareTotalEnabled(!config.dungeon.deathMessagesShareTotalEnabled())),
                            SettingEntry.toggle("Share Individual", config.dungeon::deathMessagesShareIndividualEnabled,
                                () -> config.dungeon.setDeathMessagesShareIndividualEnabled(!config.dungeon.deathMessagesShareIndividualEnabled()))
                        ))
                    )
                ),
                new FeatureEntry("Extra Score Messages", config.dungeon::extraScoreMessagesEnabled,
                    () -> config.dungeon.setExtraScoreMessagesEnabled(!config.dungeon.extraScoreMessagesEnabled()),
                    List.of(
                        SettingEntry.toggle("Mimic", config.dungeon::mimicMessageEnabled,
                            () -> config.dungeon.setMimicMessageEnabled(!config.dungeon.mimicMessageEnabled())),
                        SettingEntry.toggle("Prince", config.dungeon::princeMessageEnabled,
                            () -> config.dungeon.setPrinceMessageEnabled(!config.dungeon.princeMessageEnabled())),
                        SettingEntry.toggle("Bat", config.dungeon::batMessageEnabled,
                            () -> config.dungeon.setBatMessageEnabled(!config.dungeon.batMessageEnabled()))
                    )
                ),
                new FeatureEntry(
                    "Crypts",
                    () -> config.dungeon.debugRoomCrypts()
                        || config.dungeon.cryptProgressPartyMessageEnabled()
                        || config.dungeon.fiveCryptTitleEnabled()
                        || config.dungeon.fiveCryptPartyMessageEnabled(),
                    null,
                    List.of(
                        SettingEntry.toggle("Room Crypts", config.dungeon::debugRoomCrypts,
                            () -> config.dungeon.setDebugRoomCrypts(!config.dungeon.debugRoomCrypts())),
                        SettingEntry.toggle("Announce Progress", config.dungeon::cryptProgressPartyMessageEnabled,
                            () -> config.dungeon.setCryptProgressPartyMessageEnabled(!config.dungeon.cryptProgressPartyMessageEnabled())),
                        SettingEntry.toggle("5 Crypt Title", config.dungeon::fiveCryptTitleEnabled,
                            () -> config.dungeon.setFiveCryptTitleEnabled(!config.dungeon.fiveCryptTitleEnabled())),
                        SettingEntry.toggle("5 Crypts Msg", config.dungeon::fiveCryptPartyMessageEnabled,
                            () -> config.dungeon.setFiveCryptPartyMessageEnabled(!config.dungeon.fiveCryptPartyMessageEnabled())),
                        SettingEntry.text("Crypt Msg", config.dungeon::fiveCryptPartyMessage,
                            config.dungeon::setFiveCryptPartyMessage)
                    )
                ),
                new FeatureEntry("Blood rush helper", config.bloodRush::enabled,
                    () -> config.bloodRush.setEnabled(!config.bloodRush.enabled()),
                    List.of(
                        SettingEntry.slider("Title Time", config.bloodRush::titleDurationTenths,
                            config.bloodRush::setTitleDurationTenths, 1, 50, 1)
                    )
                ),
                new FeatureEntry("Dungeon Chat Filter", config.chatFilter::enabled,
                    () -> config.chatFilter.setEnabled(!config.chatFilter.enabled()),
                    List.of(
                        SettingEntry.toggle("Blessings", config.chatFilter::blessings,
                            () -> config.chatFilter.setBlessings(!config.chatFilter.blessings())),
                        SettingEntry.toggle("Loot Spam", config.chatFilter::lootSpam,
                            () -> config.chatFilter.setLootSpam(!config.chatFilter.lootSpam())),
                        SettingEntry.toggle("Watcher", config.chatFilter::watcher,
                            () -> config.chatFilter.setWatcher(!config.chatFilter.watcher())),
                        SettingEntry.toggle("Boss Messages", config.chatFilter::bossMessages,
                            () -> config.chatFilter.setBossMessages(!config.chatFilter.bossMessages())).withChildren(List.of(
                            SettingEntry.toggle("Bonzo", config.chatFilter::bonzo,
                                () -> config.chatFilter.setBonzo(!config.chatFilter.bonzo())),
                            SettingEntry.toggle("Scarf", config.chatFilter::scarf,
                                () -> config.chatFilter.setScarf(!config.chatFilter.scarf())),
                            SettingEntry.toggle("Professor", config.chatFilter::professor,
                                () -> config.chatFilter.setProfessor(!config.chatFilter.professor())),
                            SettingEntry.toggle("Thorn", config.chatFilter::thorn,
                                () -> config.chatFilter.setThorn(!config.chatFilter.thorn())),
                            SettingEntry.toggle("Livid", config.chatFilter::livid,
                                () -> config.chatFilter.setLivid(!config.chatFilter.livid())),
                            SettingEntry.toggle("Sadan", config.chatFilter::sadan,
                                () -> config.chatFilter.setSadan(!config.chatFilter.sadan())),
                            SettingEntry.toggle("Maxor", config.chatFilter::maxor,
                                () -> config.chatFilter.setMaxor(!config.chatFilter.maxor())),
                            SettingEntry.toggle("Storm", config.chatFilter::storm,
                                () -> config.chatFilter.setStorm(!config.chatFilter.storm())),
                            SettingEntry.toggle("Goldor", config.chatFilter::goldor,
                                () -> config.chatFilter.setGoldor(!config.chatFilter.goldor())),
                            SettingEntry.toggle("Necron", config.chatFilter::necron,
                                () -> config.chatFilter.setNecron(!config.chatFilter.necron())),
                            SettingEntry.toggle("Wither King", config.chatFilter::witherKing,
                                () -> config.chatFilter.setWitherKing(!config.chatFilter.witherKing()))
                        ))
                    )
                ),
                new FeatureEntry("Splits Overlay", config.splits::enabled,
                    () -> config.splits.setEnabled(!config.splits.enabled()),
                    List.of(
                        SettingEntry.choice("Format", TimeFormat.values(), config.splits::format,
                            config.splits::setFormat, TimeFormat::label),
                        SettingEntry.toggle("Time Prediction", config.splits::timePrediction,
                            () -> config.splits.setTimePrediction(!config.splits.timePrediction())).withChildren(List.of(
                                SettingEntry.choice("Update", PredictionMode.values(), config.splits::predictionMode,
                                    config.splits::setPredictionMode, PredictionMode::label),
                                SettingEntry.choice("Source", PredictionSource.values(), config.splits::predictionSource,
                                    config.splits::setPredictionSource, PredictionSource::label)
                                    .withTooltip("PB uses personal best splits. AVG uses the last 20 finished runs on this floor. Reset AVG in /kung splits.")
                            )),
                        SettingEntry.toggle("Time Lost", config.splits::timeLost,
                            () -> config.splits.setTimeLost(!config.splits.timeLost())),
                        SettingEntry.toggle("Run End Chat", config.splits::runEndChat,
                            () -> config.splits.setRunEndChat(!config.splits.runEndChat()))
                            .withTooltip("Print all splits in your local chat when the run ends.")
                    )
                )
            )),
            new CategoryEntry("Garden", List.of(
                new FeatureEntry("6th Visitor Alarm", config.visitorAlarm::enabled,
                    () -> config.visitorAlarm.setEnabled(!config.visitorAlarm.enabled()),
                    List.of(
                        SettingEntry.slider("Volume", config.visitorAlarm::volume,
                            config.visitorAlarm::setVolume, 1, 100, 1),
                        SettingEntry.dynamicLabel(() -> "Accept / decline or chat mute"),
                        SettingEntry.dynamicLabel(() -> "5 visitors + expired timer")
                    )
                ),
                new FeatureEntry("Feast Progress", config.feast::enabled,
                    () -> config.feast.setEnabled(!config.feast.enabled()),
                    List.of(
                        SettingEntry.toggle("Show in Hub Farm", config.feast::showInHubFarm,
                            () -> config.feast.setShowInHubFarm(!config.feast.showInHubFarm()))
                            .withTooltip("Also show Feast Progress in the Hub farm area during an active Feast."),
                        SettingEntry.text("Kernel Timeout (s)", () -> Integer.toString(config.feast.kernelTimeoutSeconds()),
                            config.feast::setKernelTimeoutText)
                            .withTooltip("Seconds without harvesting before Avg Kernels/h pauses.",
                                "Range: 10-300 seconds. Default: 60 seconds.")
                    )
                ).withTooltip("Open the Feast menu to sync milestone progress.",
                    "Kernels sync from the scoreboard or Grand Bakery.",
                    "Avg Kernels/h is saved per profile and stays visible after pauses or restarts.",
                    "Every 5 farming minutes: 80% previous rate + 20% measured rate.",
                    "Without a saved rate, the first estimate takes 5 farming minutes.")
            )),
            new CategoryEntry("Hunting", List.of(
                new FeatureEntry("Safari Uniques", config.safari::enabled,
                    () -> config.safari.setEnabled(!config.safari.enabled()),
                    List.of(
                        SettingEntry.dynamicLabel(() -> "Red: missing uniques"),
                        SettingEntry.dynamicLabel(() -> "Gray: caught this run"),
                        SettingEntry.dynamicLabel(() -> "Includes Loot Share")
                    )
                )
            )),
            new CategoryEntry("Slayer", List.of(
                new FeatureEntry("Tarantula Helper", config.slayer::tarantulaHelperEnabled,
                    () -> config.slayer.setTarantulaHelperEnabled(!config.slayer.tarantulaHelperEnabled()),
                    List.of(
                        SettingEntry.toggle("Where Is My Boss", config.slayer::eggSacPredictionRendererEnabled,
                            () -> config.slayer.setEggSacPredictionRendererEnabled(!config.slayer.eggSacPredictionRendererEnabled())),
                        SettingEntry.toggle("Egg Sac Prediction", config.slayer::eggSacPredictionEnabled,
                            () -> config.slayer.setEggSacPredictionEnabled(!config.slayer.eggSacPredictionEnabled())),
                        SettingEntry.choice("Prediction Mode", EggSacPredictionRenderMode.values(),
                            config.slayer::eggSacPredictionRenderMode, config.slayer::setEggSacPredictionRenderMode,
                            EggSacPredictionRenderMode::label)
                    )
                )
            )),
            new CategoryEntry("Util", List.of(
                new FeatureEntry("Bow Draw Indicator", config.misc::bowDrawIndicatorEnabled,
                    () -> config.misc.setBowDrawIndicatorEnabled(!config.misc.bowDrawIndicatorEnabled()), new BowDrawThresholdSettings(config.misc))
                    .withTooltip("Shows estimated bow power while drawing on Hypixel, paced by server ticks.",
                        "Add or remove custom tick markers below. 3t: minimum shot. 20t: full power.",
                        "Last Draw keeps the stopped value visible for 0.2 seconds between shots.",
                        "Power rises between markers; network delay can shift the estimate.",
                        "Move and resize in /kung hud. Shortbows do not charge."),
                new FeatureEntry("Hitboxes", config.hitboxes::enabled,
                    () -> config.hitboxes.setEnabled(!config.hitboxes.enabled()), new HitboxSettings(config.hitboxes))
                    .withTooltip("Show colored hitboxes for selected entity types.", "Right-click to add entities and edit their colors."),
                new FeatureEntry("Lobby Hop Helper", config.misc::lobbyHopHelperEnabled,
                    () -> config.misc.setLobbyHopHelperEnabled(!config.misc.lobbyHopHelperEnabled()),
                    List.of()
                ),
                new FeatureEntry("Hypixel API", config.misc::hypixelApiEnabled,
                    () -> config.misc.setHypixelApiEnabled(!config.misc.hypixelApiEnabled()),
                    List.of(
                        SettingEntry.dynamicLabel(HypixelSkyBlockProfileClient.INSTANCE::statusMessage),
                        SettingEntry.text("API Key", config.misc::hypixelApiKey,
                            config.misc::setHypixelApiKey)
                    )
                ),
                new FeatureEntry("Chat Commands", config.misc::chatCommandsEnabled,
                    () -> config.misc.setChatCommandsEnabled(!config.misc.chatCommandsEnabled()),
                    List.of(
                        SettingEntry.toggle("!c50", config.misc::c50ChatCommandEnabled,
                            () -> config.misc.setC50ChatCommandEnabled(!config.misc.c50ChatCommandEnabled())).withChildren(List.of(
                            SettingEntry.toggle("Party", config.misc::c50PartyCommandsEnabled,
                                () -> config.misc.setC50PartyCommandsEnabled(!config.misc.c50PartyCommandsEnabled())),
                            SettingEntry.toggle("Guild", config.misc::c50GuildCommandsEnabled,
                                () -> config.misc.setC50GuildCommandsEnabled(!config.misc.c50GuildCommandsEnabled())),
                            SettingEntry.toggle("All Chat", config.misc::c50AllChatCommandsEnabled,
                                () -> config.misc.setC50AllChatCommandsEnabled(!config.misc.c50AllChatCommandsEnabled())),
                            SettingEntry.toggle("DMs", config.misc::c50PrivateCommandsEnabled,
                                () -> config.misc.setC50PrivateCommandsEnabled(!config.misc.c50PrivateCommandsEnabled()))
                        )),
                        SettingEntry.toggle("!ca50", config.misc::ca50ChatCommandEnabled,
                            () -> config.misc.setCa50ChatCommandEnabled(!config.misc.ca50ChatCommandEnabled())).withChildren(List.of(
                            SettingEntry.toggle("Party", config.misc::ca50PartyCommandsEnabled,
                                () -> config.misc.setCa50PartyCommandsEnabled(!config.misc.ca50PartyCommandsEnabled())),
                            SettingEntry.toggle("Guild", config.misc::ca50GuildCommandsEnabled,
                                () -> config.misc.setCa50GuildCommandsEnabled(!config.misc.ca50GuildCommandsEnabled())),
                            SettingEntry.toggle("All Chat", config.misc::ca50AllChatCommandsEnabled,
                                () -> config.misc.setCa50AllChatCommandsEnabled(!config.misc.ca50AllChatCommandsEnabled())),
                            SettingEntry.toggle("DMs", config.misc::ca50PrivateCommandsEnabled,
                                () -> config.misc.setCa50PrivateCommandsEnabled(!config.misc.ca50PrivateCommandsEnabled()))
                        )),
                        SettingEntry.toggle("!tps", config.misc::tpsChatCommandEnabled,
                            () -> config.misc.setTpsChatCommandEnabled(!config.misc.tpsChatCommandEnabled())).withChildren(List.of(
                            SettingEntry.toggle("Party", config.misc::tpsPartyCommandsEnabled,
                                () -> config.misc.setTpsPartyCommandsEnabled(!config.misc.tpsPartyCommandsEnabled())),
                            SettingEntry.toggle("Guild", config.misc::tpsGuildCommandsEnabled,
                                () -> config.misc.setTpsGuildCommandsEnabled(!config.misc.tpsGuildCommandsEnabled())),
                            SettingEntry.toggle("All Chat", config.misc::tpsAllChatCommandsEnabled,
                                () -> config.misc.setTpsAllChatCommandsEnabled(!config.misc.tpsAllChatCommandsEnabled())),
                            SettingEntry.toggle("DMs", config.misc::tpsPrivateCommandsEnabled,
                                () -> config.misc.setTpsPrivateCommandsEnabled(!config.misc.tpsPrivateCommandsEnabled()))
                        ))
                    )
                ),
                new FeatureEntry("Chat Emotes", config.misc::chatEmotesEnabled,
                    () -> config.misc.setChatEmotesEnabled(!config.misc.chatEmotesEnabled()),
                    List.of(
                        SettingEntry.dynamicLabel(() -> ":iman: = \u2672"),
                        SettingEntry.dynamicLabel(() -> ":ironman: = \u2672")
                    )
                ),
                new FeatureEntry("Custom Sounds", config.misc::customSoundsEnabled,
                    () -> config.misc.setCustomSoundsEnabled(!config.misc.customSoundsEnabled()),
                    customSoundSettings(config)
                ),
                new FeatureEntry("Loadouts Auto Close", config.misc::loadoutsAutoCloseEnabled,
                    () -> config.misc.setLoadoutsAutoCloseEnabled(!config.misc.loadoutsAutoCloseEnabled()),
                    loadoutAutoCloseSettings(config)
                ),
                new FeatureEntry("Superpairs Helper", config.misc::superpairsHelperEnabled,
                    () -> config.misc.setSuperpairsHelperEnabled(!config.misc.superpairsHelperEnabled()),
                    List.of(
                        SettingEntry.toggle("Debug", config.misc::superpairsHelperDebugEnabled,
                            () -> config.misc.setSuperpairsHelperDebugEnabled(!config.misc.superpairsHelperDebugEnabled()))
                            .withTooltip("Adds packet details to /kung log, without extra overlay text.")
                    )
                ).withTooltip("Lists enchantments first; individual Enchanting XP rewards are hidden.",
                    "Unseen pairs have neither card revealed. Known singles are excluded.",
                    "Up to marks an upper bound because hidden fields may contain bonuses.",
                    "1/2 and 2/2 mean cards seen, not confirmed claimed rewards.")
            )),
            new CategoryEntry("Debug", List.of(
                new FeatureEntry(
                    KungUpdater.INSTANCE::buttonLabel,
                    KungUpdater.INSTANCE::isUpdateAvailable,
                    KungUpdater.INSTANCE::canInstallUpdate,
                    () -> KungUpdater.INSTANCE.installLatestAsync(Minecraft.getInstance()),
                    List.of(
                        SettingEntry.dynamicLabel(() -> "Installed: v" + KungUpdater.currentVersion()),
                        SettingEntry.dynamicLabel(KungUpdater.INSTANCE::latestVersionLabel),
                        SettingEntry.button("Changelogs", "Open", openChangelog)
                    )
                ).pinnedOpen().withTooltip("Download and verify the latest compatible update from GitHub.",
                    "Close Minecraft to apply it automatically, then start the game again."),
                new FeatureEntry("Debug Messages", config.debug::enabled,
                    () -> config.debug.setEnabled(!config.debug.enabled()),
                    List.of(
                        SettingEntry.toggle("Context", config.debug::contextMessages,
                            () -> config.debug.setContextMessages(!config.debug.contextMessages())),
                        SettingEntry.toggle("Dungeon", config.debug::dungeonMessages,
                            () -> config.debug.setDungeonMessages(!config.debug.dungeonMessages())),
                        SettingEntry.toggle("Interface", config.debug::interfaceMessages,
                            () -> config.debug.setInterfaceMessages(!config.debug.interfaceMessages())),
                        SettingEntry.toggle("Tarantula", config.debug::tarantulaMessages,
                            () -> config.debug.setTarantulaMessages(!config.debug.tarantulaMessages())).withChildren(List.of(
                            SettingEntry.toggle("Slayer Spawned", config.slayer::tarantulaDebugSlayerSpawned,
                                () -> config.slayer.setTarantulaDebugSlayerSpawned(!config.slayer.tarantulaDebugSlayerSpawned())),
                            SettingEntry.toggle("Slayer Pos", config.slayer::tarantulaDebugSlayerPosition,
                                () -> config.slayer.setTarantulaDebugSlayerPosition(!config.slayer.tarantulaDebugSlayerPosition())),
                            SettingEntry.toggle("Phase Change", config.slayer::tarantulaDebugSlayerPhaseChange,
                                () -> config.slayer.setTarantulaDebugSlayerPhaseChange(!config.slayer.tarantulaDebugSlayerPhaseChange())),
                            SettingEntry.toggle("Slayer Dead", config.slayer::tarantulaDebugSlayerDead,
                                () -> config.slayer.setTarantulaDebugSlayerDead(!config.slayer.tarantulaDebugSlayerDead())),
                            SettingEntry.toggle("Egg Sac Start", config.slayer::tarantulaDebugEggSacPhaseStart,
                                () -> config.slayer.setTarantulaDebugEggSacPhaseStart(!config.slayer.tarantulaDebugEggSacPhaseStart())),
                            SettingEntry.toggle("Egg Sac Done", config.slayer::tarantulaDebugEggSacPhaseDone,
                                () -> config.slayer.setTarantulaDebugEggSacPhaseDone(!config.slayer.tarantulaDebugEggSacPhaseDone()))
                        ))
                    )
                ),
                new FeatureEntry("Room Sync", config.dungeon::roomSyncEnabled,
                    () -> toggleRoomSync(config),
                    List.of(
                        SettingEntry.dynamicLabel(() -> "Status: " + DungeonRoomDataSyncClient.INSTANCE.menuStatus()),
                        SettingEntry.text("Server", config.dungeon::roomSyncServerUrl,
                            config.dungeon::setRoomSyncServerUrl),
                        SettingEntry.text("Token", config.dungeon::roomSyncToken,
                            config.dungeon::setRoomSyncToken),
                        SettingEntry.toggle("Upload", config.dungeon::roomSyncUploadEnabled,
                            () -> config.dungeon.setRoomSyncUploadEnabled(!config.dungeon.roomSyncUploadEnabled())),
                        SettingEntry.button("Pull", "Rooms",
                            () -> DungeonRoomDataSyncClient.INSTANCE.pullAsync(Minecraft.getInstance())),
                        SettingEntry.button("Ping", "Check",
                            () -> DungeonRoomDataSyncClient.INSTANCE.pingAsync(Minecraft.getInstance()))
                    )
                )
            ))
        );
    }

    static List<SettingEntry> dragonHelperSettings(KungConfig config, boolean developer) {
        List<SettingEntry> settings = new ArrayList<>(List.of(
            SettingEntry.toggle("Spawn Markers", config.dungeon::dragonSpawnMarkersEnabled,
                () -> config.dungeon.setDragonSpawnMarkersEnabled(!config.dungeon.dragonSpawnMarkersEnabled())),
            SettingEntry.toggle("Statue Boxes", config.dungeon::dragonStatueBoxesEnabled,
                () -> config.dungeon.setDragonStatueBoxesEnabled(!config.dungeon.dragonStatueBoxesEnabled()))
                .withTooltip("Shows estimated dragon counting areas at the statues.",
                    "Green means the origin is inside the estimate; red means outside."),
            SettingEntry.toggle("Count Notifications", config.dungeon::dragonCountNotificationsEnabled,
                () -> config.dungeon.setDragonCountNotificationsEnabled(!config.dungeon.dragonCountNotificationsEnabled()))
                .withTooltip("Local notifications for server-confirmed dragon counts.")
        ));
        if (developer) {
            settings.add(SettingEntry.toggle("Developer Diagnostics", config.dungeon::devDragonDiagnosticsEnabled,
                () -> config.dungeon.setDevDragonDiagnosticsEnabled(!config.dungeon.devDragonDiagnosticsEnabled()))
                .withTooltip("Extra local dragon capture diagnostics for Beng114 only."));
        }
        return List.copyOf(settings);
    }

    private static void toggleRoomSync(KungConfig config) {
        config.dungeon.setRoomSyncEnabled(!config.dungeon.roomSyncEnabled());
        DungeonKnownRoomCatalog.reload();
    }

    private static List<SettingEntry> customSoundSettings(KungConfig config) {
        List<SettingEntry> settings = new ArrayList<>();
        settings.add(SettingEntry.dynamicLabel(CustomSoundsFeature::soundsFolderStatus));
        settings.add(SettingEntry.button("Scan Folder", "Refresh", CustomSoundsFeature::refreshSoundIndex));
        settings.add(SettingEntry.text("Arrow Hit Files", config.misc::customArrowHitSounds,
            config.misc::setCustomArrowHitSounds));
        settings.add(SettingEntry.slider("Arrow Default Volume", config.misc::customArrowHitVolumeTenths,
            config.misc::setCustomArrowHitVolumeTenths, 0, 50, 1));
        settings.add(SettingEntry.slider("Arrow Default Pitch", config.misc::customArrowHitPitchHundredths,
            config.misc::setCustomArrowHitPitchHundredths, 25, 300, 5));
        settings.add(SettingEntry.button("Test Arrow", "Play", CustomSoundsFeature::playArrowHit));
        for (String soundName : CustomSoundsFeature.configuredSoundNames(config.misc.customArrowHitSounds())) {
            settings.add(soundSettings("Arrow: " + soundName,
                () -> config.misc.customArrowHitSoundVolumeTenths(soundName),
                value -> config.misc.setCustomArrowHitSoundVolumeTenths(soundName, value),
                () -> config.misc.customArrowHitSoundPitchHundredths(soundName),
                value -> config.misc.setCustomArrowHitSoundPitchHundredths(soundName, value),
                () -> CustomSoundsFeature.playArrowHitSound(soundName)));
        }
        settings.add(SettingEntry.text("Wither End Files", config.misc::customWitherShieldExpireSounds,
            config.misc::setCustomWitherShieldExpireSounds));
        settings.add(SettingEntry.slider("Wither Default Volume", config.misc::customWitherShieldExpireVolumeTenths,
            config.misc::setCustomWitherShieldExpireVolumeTenths, 0, 50, 1));
        settings.add(SettingEntry.slider("Wither Default Pitch", config.misc::customWitherShieldExpirePitchHundredths,
            config.misc::setCustomWitherShieldExpirePitchHundredths, 25, 300, 5));
        settings.add(SettingEntry.button("Test Wither", "Play", CustomSoundsFeature::playWitherShieldExpire));
        for (String soundName : CustomSoundsFeature.configuredSoundNames(config.misc.customWitherShieldExpireSounds())) {
            settings.add(soundSettings("Wither: " + soundName,
                () -> config.misc.customWitherShieldExpireSoundVolumeTenths(soundName),
                value -> config.misc.setCustomWitherShieldExpireSoundVolumeTenths(soundName, value),
                () -> config.misc.customWitherShieldExpireSoundPitchHundredths(soundName),
                value -> config.misc.setCustomWitherShieldExpireSoundPitchHundredths(soundName, value),
                () -> CustomSoundsFeature.playWitherShieldExpireSound(soundName)));
        }
        return List.copyOf(settings);
    }

    private static SettingEntry soundSettings(String name, IntSupplier volume, IntConsumer setVolume,
                                              IntSupplier pitch, IntConsumer setPitch, Runnable play) {
        return SettingEntry.group(name).withChildren(List.of(
            SettingEntry.slider("Volume", volume, setVolume, 0, 50, 1),
            SettingEntry.slider("Pitch", pitch, setPitch, 25, 300, 5),
            SettingEntry.button("Test", "Play", play)
        ));
    }

    private static List<SettingEntry> loadoutAutoCloseSettings(KungConfig config) {
        List<SettingEntry> settings = new ArrayList<>();
        settings.add(SettingEntry.toggle("Close Only On Change", config.misc::loadoutsCloseOnlyOnChange,
            () -> config.misc.setLoadoutsCloseOnlyOnChange(!config.misc.loadoutsCloseOnlyOnChange())));
        for (int index = 0; index < 12; index++) {
            int loadoutIndex = index;
            settings.add(SettingEntry.keybind(
                "Loadout " + (index + 1),
                () -> LoadoutsAutoCloseFeature.keybindDisplay(config.misc.loadoutKeybind(loadoutIndex)),
                () -> config.misc.loadoutKeybind(loadoutIndex),
                keybind -> config.misc.setLoadoutKeybind(loadoutIndex, keybind)
            ));
        }
        return List.copyOf(settings);
    }

    private static void toggleLocalRoomData(KungConfig config) {
        config.dungeon.setLocalRoomDataEnabled(!config.dungeon.localRoomDataEnabled());
        DungeonRoomClassifier.reload();
        DungeonKnownRoomCatalog.reload();
    }

    public record CategoryEntry(String name, List<FeatureEntry> features) { }

    public record FeatureEntry(
        Supplier<String> nameSupplier,
        BooleanSupplier enabledSupplier,
        BooleanSupplier clickableSupplier,
        Runnable toggle,
        Supplier<List<SettingEntry>> settingsSupplier,
        boolean alwaysExpanded,
        List<String> tooltip,
        boolean actionOnly
    ) {
        public FeatureEntry(Supplier<String> name, BooleanSupplier enabled, BooleanSupplier clickable, Runnable action,
                            List<SettingEntry> settings, boolean alwaysExpanded, List<String> tooltip, boolean actionOnly) {
            this(name, enabled, clickable, action, () -> settings, alwaysExpanded, tooltip, actionOnly);
        }

        FeatureEntry(Supplier<String> name, BooleanSupplier enabled, BooleanSupplier clickable, Runnable action,
                     List<SettingEntry> settings) {
            this(name, enabled, clickable, action, settings, false, List.of(), true);
        }

        FeatureEntry(String name, BooleanSupplier enabled, Runnable toggle, List<SettingEntry> settings) {
            this(() -> name, enabled, () -> toggle != null, toggle, settings, false, List.of(), false);
        }

        FeatureEntry(String name, BooleanSupplier enabled, Runnable toggle, Supplier<List<SettingEntry>> settings) {
            this(() -> name, enabled, () -> toggle != null, toggle, settings, false, List.of(), false);
        }

        FeatureEntry pinnedOpen() {
            return new FeatureEntry(nameSupplier, enabledSupplier, clickableSupplier, toggle, settingsSupplier, true, tooltip, actionOnly);
        }

        FeatureEntry withTooltip(String... lines) {
            return new FeatureEntry(nameSupplier, enabledSupplier, clickableSupplier, toggle, settingsSupplier, alwaysExpanded, List.of(lines), actionOnly);
        }

        public List<SettingEntry> settings() { return settingsSupplier.get(); }

        public String name() {
            return nameSupplier.get();
        }

        public boolean enabled() {
            return enabledSupplier != null && enabledSupplier.getAsBoolean();
        }

        public boolean clickable() {
            return toggle != null && clickableSupplier != null && clickableSupplier.getAsBoolean();
        }
    }
}
