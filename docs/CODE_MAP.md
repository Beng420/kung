# Code map

Paths below are relative to the active module's
[Java package](../versions/mc26_1_2/src/main/java/com/github/beng420/kung).
Tests use the same package under
[src/test/java](../versions/mc26_1_2/src/test/java/com/github/beng420/kung).
Names omit `.java`; test names are initial targets, not exhaustive coverage.

| Task | Start in source | Start in tests | Contract / evidence |
| --- | --- | --- | --- |
| Instance, floor, boss entry, reset | `skyblock/HypixelInstanceTracker`, `HypixelInstanceState`, `HypixelDungeonFloorState`; `feature/dungeon/DungeonEventRouter` | `DungeonLifecycleTest`, `HypixelDungeonFloorStateTest`, `SkyBlockSidebarTest` | [Lifecycle](DUNGEON_ARCHITECTURE.md#instance-and-run-lifecycle) |
| Map FPS, scan scheduling, cache invalidation | `feature/dungeon/DungeonStateTracker`, `DungeonScanRecorder`, `DungeonLiveMapWriter` | `DungeonPerformanceTest`, `DungeonRuntimeRegressionTest` | [Map pipeline](DUNGEON_ARCHITECTURE.md#map-pipeline-and-budgets) |
| Room hashes, pre-run recognition, data | `feature/dungeon/DungeonRoomRepository`, `DungeonKnownRoomCatalog`, `DungeonRoomClassifier` | `DungeonRoomRepositoryTest`, `DungeonRuntimeRegressionTest`, `RareDungeonRoomCatalogTest` | [Room data](ROOM_DATA.md) |
| Disconnected rooms, wrong doors or labels | `feature/dungeon/DungeonMapSnapshot`, `DungeonRoomRenderLayout`, `DungeonMapFeature` | `DungeonRoomRenderLayoutTest`, `DungeonMapLayoutTest`, `RareDungeonRoomRenderingTest` | [Ownership](DUNGEON_ARCHITECTURE.md#map-pipeline-and-budgets) |
| Player icons, classes, skins | `feature/dungeon/DungeonPlayerStats`, `DungeonMapFeature`; `skyblock/HypixelPartyTracker` | `DungeonRunSummaryTest` (roster parsing); live marker trace | [Markers](DUNGEON_ARCHITECTURE.md#player-markers) |
| Blood Rush / Fairy doors | `feature/dungeon/BloodRushHelperFeature`, `BloodRushDoorEstimate`, `DungeonMapSnapshot` | `BloodRushHelperReliabilityTest`, `DungeonRuntimeRegressionTest` | [Door evidence](DUNGEON_ARCHITECTURE.md#blood-rush) |
| Mimic late / disappears / static trapped chests / kill messages | `feature/dungeon/DungeonMimicEspFeature`, `DungeonMimicChestScanner`, `DungeonMimicChestMemory`, `DungeonMimicStaticChests`, `DungeonStaticChestPattern`, `DungeonRunStats` | `DungeonMimicPersistenceTest`, `DungeonStaticChestTest`, `DungeonExtraScoreMessagesTest` | [Static chest filter and live trace](MIMIC_STATIC_CHESTS.md) |
| Boss-map artwork, bounds, position | `feature/dungeon/DungeonBossMapCatalog`, `DungeonBossMapSelection`, `DungeonMapFeature` | `DungeonBossMapCatalogTest`, `DungeonBossMapSelectionTest` | [Boss maps](BOSS_MAPS.md) |
| Splits, personal bests, phase/PB chat messages, finish prediction, ideal time, TPS | `feature/dungeon/DungeonSplitTracker`, `DungeonSplitMessages`, `DungeonSplitsOverlayFeature`, `DungeonLifecycleSignals`; `config/category/SplitsConfig`; `message/KungMessages`; `util/ServerTickSequence`, `ServerTickPacketContext`, `ServerTpsTracker`, `KungDebugRecorder`; `mixin/ClientCommonPacketListenerMixin`, `ClientPacketListenerMixin` | `DungeonSplitTrackerTest`, `DungeonSplitMessagesTest`, `DungeonSplitPersonalBestsTest`, `DungeonSplitCompletionOrderTest`, `config/SplitPersonalBestsConfigTest`, `DungeonSplitClockConservationTest`, `DungeonSplitPacketTimingTest`, `DungeonSplitsOverlayTest`, `ServerTpsTrackerTest`, `ServerTickPacketContextTest`, `KungDebugRecorderTest` | [Current split contract](RUN_STATISTICS.md#splits), [score-before-victory PB correction](RUN_STATISTICS.md#score-before-victory-pb-correction--2026-09-15), [timing evidence](SPLITS_TIMING_FIXES.md) |
| Score, secrets, deaths, end summary | `feature/dungeon/DungeonRunStats`, `DungeonScoreCalculator`, `DungeonPuzzleProgress`, `DungeonSecretCounts`, `DungeonSecretCounter`, `DungeonDeathTracker`, `DungeonRoomProgress`, `DungeonRunSummaryLayout` | `DungeonScoreReadinessTest`, `DungeonPuzzleProgressTest`, `DungeonStatisticsSourcesTest`, `DungeonRunRosterRegressionTest`, `DungeonScoreCalculatorTest`, `DungeonSecretCounterTest`, `DungeonDeathTrackerTest`, `DungeonRunSummaryTest`, `DungeonRoomProgressTest`, `DungeonRunSummaryLayoutTest` | [Score and unfinished rooms](RUN_STATISTICS.md#score-and-unfinished-rooms), [statistics](RUN_STATISTICS.md), [0.3.1 evidence](RUN_STATISTICS_FIXES.md) |
| Extra Score Messages (Mimic, Prince, Bat) | `config/KungConfigScreen`, `config/category/DungeonConfig`; `feature/dungeon/DungeonRunStats`, `DungeonWorkload`, `DungeonAnnouncements` | `DungeonExtraScoreMessagesTest`, `ConfigRegressionTest` | [Statistics and announcements](RUN_STATISTICS.md#room-credit-and-summary) |
| Superpairs | `feature/misc/SuperpairsHelperFeature`, `SuperpairsBoard`; `mixin/ClientPacketListenerMixin` | `SuperpairsBoardTest` | [Board rules and evidence](SUPERPAIRS_FIXES.md) |
| Custom audio / Wither Shield | `feature/misc/CustomSoundsFeature`, `CustomSoundPlayer`, `CustomSoundMixer`, `WitherShieldSoundTimer` | `CustomSoundPlayerTest`, `CustomSoundMixerTest`, `WitherShieldSoundTimerTest` | [Audio correction](FOLLOWUP_FIXES_2026_09_12.md#custom-sounds) |
| Chat emotes / sent history | `feature/misc/ChatEmotesFeature`; `config/category/MiscConfig`, `config/KungConfigScreen` | `ChatEmotesFeatureTest`, `ConfigRegressionTest` | [Emotes and history](CHAT_EMOTES.md) |
| Garden/Hub Feast donations / milestone claims / Kernels / farming rate / persistence / seasonal HUD | `feature/garden/FeastOverlayFeature`, `FeastProgress`, `FeastContext`, `FeastSession`, `FeastKernels`, `FeastKernelRate`, `GardenCropTracker`, `FeastMilestoneClaims`, `FeastPersistence`, `FeastStateStore`; `skyblock/SkyBlockMayorTracker`; `config/category/FeastConfig`; `mixin/MultiPlayerGameModeMixin`, `ClientPacketListenerMixin` | `FeastProgressTest`, `FeastContextTest`, `FeastSessionTest`, `FeastKernelsTest`, `FeastKernelRateTest`, `FeastMilestoneClaimsTest`, `FeastPersistenceTest`, `FeastMessageRoutingTest`, `SkyBlockFeastMayorTest`, `ConfigRegressionTest` | [Feast Progress](FEAST_PROGRESS.md) |
| Garden sixth visitor / repeating alarm / replacing chat reminder / click mute / hidden countdown / crop and pest reductions | `feature/garden/VisitorAlarmFeature`, `VisitorAlarmState`, `GardenCropTracker`, `VisitorQueue`, `VisitorOffer`, `VisitorTimerCompatibility`; `config/category/VisitorAlarmConfig`; `mixin/MultiPlayerGameModeMixin`, `ChatComponentAccessor`; `message/KungMessages`; `command/UserCommandGroup`, `KungCommandActions` | `VisitorAlarmTest`, `VisitorCropTrackerTest`, `VisitorPestTimingTest`, `VisitorOfferTest`, `VisitorAlarmReminderTest`, `VisitorAlarmCommandTest`, `KungMessageReplacementTest`, `ConfigRegressionTest` | [6th Visitor Alarm](VISITOR_ALARM.md) |
| Cata calculator, !c50, !ca50 | `util/HypixelSkyBlockProfileClient`, `CatacombsAverageCalculator`; `feature/dungeon/CatacombsCalculatorScreen` | `CatacombsCalculatorTest` | [Profile and calculation rules](CATACOMBS_CALCULATOR_FIXES.md) |
| Config / menu appearance / hover help / HUD editor / OneConfig and Mod Menu entrypoint | `config/KungConfig`, `KungConfigScreen`, `KungHudEditorScreen`; `compat/KungModMenu`; `config/category`, `ui/UiMenuFont`, `UiShapes`, `UiTheme`, `UiTooltip`, `UiHoverDelay`; `mixin/FontAccessor` | `ConfigRegressionTest`, `SettingEntryTest`, `UiControlModelTest`, `UiMenuFontTest`; packaged entrypoint/dependency inspection and live menu navigation | [Development conventions](DEVELOPMENT.md#changing-code-and-comments), [optional menu integration](DEVELOPMENT.md#optional-oneconfig--mod-menu-integration) |
| Critter Safari missing uniques / four-region HUD | `feature/safari/SafariOverlayFeature`, `SafariSession`, `SafariCritters`; `config/category/SafariConfig` | `SafariSessionTest`, `SafariOverlayTest`, `SafariConfigTest` | [Critter Safari](CRITTER_SAFARI.md) |
| Files, profile folders, migration | `runtime/KungPaths`, `KungFileLayout`; `KungClient`; `update/KungUpdater` | `KungFileLayoutTest`, `ConfigRegressionTest` | [File storage](FILE_STORAGE.md) |
| Safe update installation / launcher ownership / Hypixel popup / polling / changelogs / GitHub | `update/KungUpdater`, `KungUpdateDownload`, `KungUpdateInstaller`, `KungUpdateProcess`, `KungUpdateEnvironment`, `KungUpdateCheckSchedule`, `KungUpdateNotification`, `KungUpdateToast`, `KungUpdateToastState`, `KungReleaseNotes`, `KungReleaseHistory`, `KungReleaseNotesPopup`; `command/UserCommandGroup`, `KungCommandActions`; `config/KungConfigScreen` | `KungUpdateDownloadTest`, `KungUpdateInstallerTest`, `KungUpdateProcessTest`, `KungUpdateEnvironmentTest`, `KungUpdateCheckScheduleTest`, `KungUpdateNotificationTest`, `KungUpdateToastStateTest`, `KungReleaseNotesTest`, `KungReleaseHistoryTest` | [Updates](UPDATES.md) |

## Wiring and shared services

`KungClient` wires `runtime/AppServices`, `runtime/ServiceRegistry` and
`feature/FeatureRegistry`. Configurable features derive from `ConfigurableFeature`.
`command/KungCommands` registers command groups; ordinary actions use
`KungCommandActions`, while Mimic commands use the dedicated chest service.
`message/KungMessages` formats local output; `HypixelChatSender` owns outgoing chat.
`util/KungDebugRecorder` owns the trace ring buffer.

## Reading older evidence

[DUNGEON_FIXES.md](DUNGEON_FIXES.md) and
[FOLLOWUP_FIXES_2026_09_12.md](FOLLOWUP_FIXES_2026_09_12.md) are dated investigations.
Their build counts and live-check status describe those changes at that time.
[MIMIC_STATIC_CHESTS_PLAN.md](MIMIC_STATIC_CHESTS_PLAN.md) preserves research;
the implemented contract is in the separate static-chest document above.
