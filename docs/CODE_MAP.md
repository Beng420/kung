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
| Mimic disappears / static trapped chests | `feature/dungeon/DungeonMimicEspFeature`, `DungeonMimicChestMemory`, `DungeonMimicStaticChests`, `DungeonStaticChestPattern` | `DungeonMimicPersistenceTest`, `DungeonStaticChestTest` | [Static chest filter](MIMIC_STATIC_CHESTS.md) |
| Boss-map artwork, bounds, position | `feature/dungeon/DungeonBossMapCatalog`, `DungeonBossMapSelection`, `DungeonMapFeature` | `DungeonBossMapCatalogTest`, `DungeonBossMapSelectionTest` | [Boss maps](BOSS_MAPS.md) |
| Splits, ideal time, TPS | `feature/dungeon/DungeonSplitTracker`, `DungeonSplitsOverlayFeature`; `util/ServerTickSequence`, `ServerTickPacketContext`, `ServerTpsTracker`, `KungDebugRecorder`; `mixin/ClientCommonPacketListenerMixin`, `ClientPacketListenerMixin` | `DungeonSplitTrackerTest`, `DungeonSplitClockConservationTest`, `DungeonSplitPacketTimingTest`, `DungeonSplitsOverlayTest`, `ServerTpsTrackerTest`, `ServerTickPacketContextTest`, `KungDebugRecorderTest` | [Current split contract](RUN_STATISTICS.md#splits), [timing evidence](SPLITS_TIMING_FIXES.md) |
| Score, secrets, deaths, end summary | `feature/dungeon/DungeonRunStats`, `DungeonSecretCounts`, `DungeonSecretCounter`, `DungeonDeathTracker`, `DungeonRoomProgress`, `DungeonRunSummaryLayout` | `DungeonStatisticsSourcesTest`, `DungeonScoreCalculatorTest`, `DungeonSecretCounterTest`, `DungeonDeathTrackerTest`, `DungeonRunSummaryTest`, `DungeonRoomProgressTest`, `DungeonRunSummaryLayoutTest` | [Statistics](RUN_STATISTICS.md) |
| Extra Score Messages (Mimic, Prince, Bat) | `config/KungConfigScreen`, `config/category/DungeonConfig`; `feature/dungeon/DungeonRunStats`, `DungeonWorkload`, `DungeonAnnouncements` | `DungeonExtraScoreMessagesTest`, `ConfigRegressionTest` | [Statistics and announcements](RUN_STATISTICS.md#room-credit-and-summary) |
| Superpairs | `feature/misc/SuperpairsHelperFeature`, `SuperpairsBoard`; `mixin/ClientPacketListenerMixin` | `SuperpairsBoardTest` | [Board rules and evidence](SUPERPAIRS_FIXES.md) |
| Custom audio / Wither Shield | `feature/misc/CustomSoundsFeature`, `CustomSoundPlayer`, `CustomSoundMixer`, `WitherShieldSoundTimer` | `CustomSoundPlayerTest`, `CustomSoundMixerTest`, `WitherShieldSoundTimerTest` | [Audio correction](FOLLOWUP_FIXES_2026_09_12.md#custom-sounds) |
| Cata calculator, !c50, !ca50 | `util/HypixelSkyBlockProfileClient`, `CatacombsAverageCalculator`; `feature/dungeon/CatacombsCalculatorScreen` | `CatacombsCalculatorTest` | [Profile and calculation rules](CATACOMBS_CALCULATOR_FIXES.md) |
| Config / HUD editor | `config/KungConfig`, `KungConfigScreen`, `KungHudEditorScreen`; `config/category`, `ui` | `ConfigRegressionTest`, `SettingEntryTest`, `UiControlModelTest` | [Development conventions](DEVELOPMENT.md#changing-code-and-comments) |
| Files, profile folders, migration | `runtime/KungPaths`, `KungFileLayout`; `KungClient`; `update/KungUpdater` | `KungFileLayoutTest`, `ConfigRegressionTest` | [File storage](FILE_STORAGE.md) |

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
