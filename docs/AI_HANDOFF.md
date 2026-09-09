# AI Handoff

This file is the first stop for a new AI or human maintainer. It explains what this project is doing right now and which paths are safe to touch.

The active architecture queue and its definition of done live in `docs/FOUNDATION_TODO.md`.

## Current Direction

Kung is a Fabric client mod for passive Hypixel SkyBlock quality-of-life overlays and diagnostics.

Active development targets:

- `versions/mc26_1_2`

Reference only:

- `versions/mc1_21_11`
- `versions/mc26_2` (parked unless the user explicitly asks for it)

Do not port new work back to `mc1_21_11` or forward to `mc26_2` unless the user explicitly asks for it. Old `1.21.11` code and data may still be useful for comparison or room-data migration, but it is not the main development target.

## Hard Rules

- Do not change HUD or settings layouts just for cleanup.
- Keep features client-side and passive. Do not add macros, click automation, movement automation, packet manipulation, or hidden server interactions.
- Default to `mc26_1_2` only. Touch `mc26_2` only when the user asks for it.
- Prefer focused refactors around one feature at a time. The code is versioned by Minecraft module, so broad shared-code extraction needs extra care.
- All Kung feature master toggles must default to off for fresh installs. Subsettings inside a disabled feature may still have useful presets, but the feature itself must not become active until the user enables its master toggle. For chat commands specifically: `Util > Chat Commands` defaults off, individual commands default on, Party/Guild default on, and All Chat/DMs default off. For diagnostics specifically: `Debug > Debug Messages` defaults off, while its individual message-type toggles default on.

## Design References

Dungeon map work should take strong inspiration from:

- Stella's dungeon map for visual map behavior and player readability.
- FunnyMap's room recognition approach for robust room identification.
- Skyblocker's dungeon map for clean in-game map presentation.

Additional inspiration can come from Odin, Devonian, NoFrills, and Noamm Addons where they offer useful ideas for passive SkyBlock QoL, diagnostics, and HUD polish.

## Main Entry Points

- `KungClient`: client initialization. Wires config, trackers, overlays, commands, updater, and feature helpers.
- `KungCommands`: slim `/kung` root registration. Command trees are split into user, debug, room-sync, and isolated room-learning groups; execution lives in `KungCommandActions`.
- `KungConfig`: modular JSON-backed config root with typed category objects under `config/category`.
- `ConfigurableFeature`: feature base class that selects one typed config category.
- `FeatureRegistry`: initialization registry for configurable user-facing features.
- `KungConfigScreen`: current in-game settings UI. Keep its column layout stable. Do not put HUD position or HUD scale controls in the normal Mod Menu screen; keep those values saved in config for compatibility, but edit them through `/kung hud`.
- `KungHudEditorScreen`: drag-position editor for HUD elements.
- `ChatCommandsFeature`: dispatches separate chat commands for `!c50`, `!ca50`, and `!tps`. Keep their config toggles separate per command and per channel; legacy `cata...`/`ca50...` saved settings are migrated by `KungConfig`.
- `LoadoutsAutoCloseFeature`: `Close Only On Change` keeps the Loadouts menu open when Hypixel replies that the selected loadout is already equipped. In that mode hotkeys wait for the equip confirmation instead of closing the screen immediately.
- `ServerTpsTracker`: records lightweight server tick timestamps from the existing ping-packet hook. `!tps` replies as `Current: <current> (max/min/avg) <max>/<min>/<avg>` in party, guild, all chat, or DMs according to its channel toggles.
- `KungDebugRecorder`: always-on in-memory ringbuffer for diagnosing live Hypixel behavior. It records key packet hooks, incoming non-Kung chat/game messages, context/parser state changes, dungeon lifecycle decisions, door-title readiness, and party whitelist changes. Use `/kung log copy`, `/kung log tail <lines>`, `/kung log save`, and `/kung log clear` after a bad run instead of relying on memory or screenshots only.
- `HypixelPartyTracker`: global background tracker for Hypixel party membership. It listens to chat/game messages and periodically remembers online player UUIDs. Keep it non-configurable and always active; dungeon rendering should consume this shared party whitelist instead of maintaining its own party-only state.
- `HypixelInstanceTracker`: global background tracker for SkyBlock/Hypixel context. This modpack is intended for Hypixel, so it does not gate context tracking on the server address. It reads sidebar title/lines plus tab header/footer once per client tick, also consumes tab-list packets, incoming chat/game text, and the client's own sent commands, and exposes `tracking`, `dungeonHub`, `catacombs`, `dungeonRunContext`, `instanceLine`, and `serverId`. Dungeon lifecycle and Lobby Hop must use this shared context instead of separate scoreboard/tab parsing. It emits exactly one visible `[Kung Context]` message per resolved location/server pair: `Entered <instance/island> (<serverId>)`. Do not emit server-only or instance-only context messages. Current scoreboard/sidebar/tab context is preferred for `serverId`; transfer chat like `Sending to server mini...` may only seed a pending transfer id, and that id may become current only after a subsequent Login/Respawn world-change packet or client-level identity change. Hub-to-hub switches should announce again because the server id changes.
- Pending warp destinations must not be paired with raw pre-transfer chat server ids. If `/is` or a warp message sets `pendingInstanceLine`, wait until the current scoreboard/sidebar/tab context provides a server id, or until a pending transfer id has been promoted by a subsequent Login/Respawn world-change packet or client-level identity change. Pending destinations are short-lived and single-use; clear them after a complete `<instance> (<serverId>)` announcement so they cannot be reused for a later dungeon/lobby transition.
- Treat server/world changes as the hard "new instance" anchor. `ClientboundLoginPacket`/`ClientboundRespawnPacket` call `HypixelInstanceTracker.observeWorldChangePacket()`, and the tracker also compares `client.level` identity each client tick as a reactive fallback for transfers where the packet hook is missed or too early. This immediately clears old instance flags and cached tab/sidebar context while preserving pending warp destinations. Do not add fixed settling timers here; after the world-change event, the next tick should read only the current world/tab/scoreboard context.
- Hypixel can send a Login packet followed immediately by multiple Respawn/level-change events for one transfer. If `Sending to server mini...` has been promoted to the current transfer server, do not clear it on duplicate world-change events with no new transfer message; keep it briefly so the first real scoreboard/tab context can announce `Entered <instance> (<serverId>)`.
- The same world-change packets also route through `DungeonEventRouter.observeWorldChangePacket()`, and the dungeon tracker mirrors the same `client.level` identity fallback. During an active dungeon session without a pending run summary, this clears cached render state and restarts the scan recorder so a new Catacombs server/run cannot keep the old map.
- Dungeon detection must let strong in-world evidence override stale context text. If the player has the dungeon map item and is inside the dungeon grid, treat that as a dungeon instance candidate even if `HypixelInstanceTracker` still says `Dungeon Hub`; otherwise entering Catacombs can be missed and `Starting in 1 second.` will not reset the map for a new run.
- Hypixel server ids can have multiple trailing letters, e.g. `mini24BS` or `mini82CH`; do not restrict server id parsing to a single suffix letter.

## Dungeon Map Flow

The dungeon map is the highest-risk area.

1. `DungeonScanRecorder` scans room, door, and separator points from the world.
2. `DungeonMapSnapshot` stores observed scan points and visited/cleared/completed state.
3. `DungeonRoomRepository` is the runtime boundary around the finished catalog; `KnownDungeonRoomRepository` delegates to `DungeonKnownRoomCatalog` storage and matching.
4. `DungeonLiveMapWriter.MatchRenderPlan` turns repository matches and hints into render ownership, room types, doors, visited state, and external door coloring. The old unused PNG/HTML diagnostic writer was removed.
5. `DungeonMapFeature` renders the in-game HUD.
6. `DungeonRunStats` aggregates scoreboard/chat/actionbar observations. Score math, player state, API enrichment, and outgoing announcements live in dedicated classes.

Current 26er scanning notes:

- Block scans intentionally keep running during a dungeon run. Do not re-add an early "scan complete" pause unless room matching is proven stable across 26.
- Player rendering must follow the Skyblocker/Stella dungeon-map model: keep a fixed five-slot dungeon player list from the dungeon tablist, force the local player into slot `0`, map the `FRAME` decoration to slot `0`, and map `BLUE_MARKER` decorations sequentially to slots `1..4`. If a slot/UUID is missing, skip and log it; never draw anonymous guessed teammate markers. Loaded player entities may improve position/rotation/skin, but must not decide identity.
- Player rendering should use the global `HypixelPartyTracker` party whitelist plus dungeon tab-list class lines. Do not fall back to rank/VIP/MVP-style guessing, nearest-player guessing, or nameless anonymous map markers, because that reintroduces random NPCs and mobs on the map. Trace category `player-markers` logs slot/decorations, resolved/drawn counts, rejection reasons, and short drawn UUIDs.
- Dungeon player slot registration must resolve parsed player names to UUIDs before trusting the raw tab-row UUID: exact loaded player entity name, observed online/party UUID, remembered name UUID, party fallback, raw tab-row UUID, then synthetic UUID. Hypixel/Fabric can expose dungeon tab player lines on nameless/fake `PlayerInfo` rows; blindly using that row UUID caused teammates such as `yrkuna` to render as unknown/white or with another player's cached skin. Trace category `player-slots` logs name/UUID source overrides and the current fixed five-slot list.
- Class-border debugging must be visible in `player-markers`: the summary includes `slots=[index:name:uuid:class]`, and each drawn/rejected marker detail includes `slot`, `slotName`, `slotClass`, resolved `class`, `classSource`, `classColor`, and whether a loaded entity was used. A white border should correspond to `class=UNKNOWN classSource=unknown classColor=#FFE9EDF2`; use that to decide whether tab-list class parsing, UUID/name mapping, or slot assignment failed.
- Player skins should use the last real loaded/tab skin per UUID before falling back to Minecraft's default skin. Nameless map decorations can outlive the loaded player object, so without this cache teammates may temporarily draw as Steve/Alex.
- Player class colors must resolve by UUID first and by remembered player name second. The map marker UUID and the tab-list/class parse can temporarily land on different player stat rows, so a name fallback is required to avoid known dungeon players getting the white unknown-class border. Tab class parsing must tolerate player lines that do not start with a `[level]` prefix.
- Trace category `map-change` is the authoritative map snapshot mutation journal. It logs resets, point additions/replacements, first observed room points, player-grid movement, start room, traversed doors, map-player/map-visible rooms, clear/completed state changes, and opened Wither/Blood door transitions. Use this before changing render code when a door/exit appears late or a room state flips unexpectedly.
- The run-start `x doors` title is scheduled from the `Starting in 1 second.` lifecycle chat signal. The count is the best known Blood Rush door total: the conservative lower bound comes from every observed non-start Wither/Blood door, including doors already recorded as opened; an exact count is allowed only when all map-visible rooms are recognized or when the visible map data contains an uninterrupted START-to-BLOOD path. Doors touching START are excluded so the entrance/start door cannot inflate the title.
- After the initial door title is shown, Kung treats scanned Wither/Blood door transitions as the authoritative progress trigger. If a previously locked special door becomes `OPEN` or stops being a locked special door because its coal/red blocks disappeared, `DungeonMapSnapshot.openedLockedDoors()` records it and Blood Rush shows the remaining title from the latest estimate. Do not require the rendered path count to drop before showing a door-fell title.
- The run-start door title must not use magic fallback counts like `5+ doors`. While the title is pending, the scanner temporarily uses a fast interval. If the exact conditions above are not met yet but some non-start Wither/Blood doors are visible or already opened, show the conservative lower bound as `<n>+ doors`. The `+` means more doors may still appear because Blood/Rush recognition or visible map rooms are incomplete.
- Door-title readiness must consider the map item, not only physically scanned rooms. `DungeonMapSnapshot.mapVisibleRooms()` records rooms visible on the dungeon map item; map-visible `UNKNOWN` placeholders may be replaced by later known local or remote room identity. A previous bug showed exact counts too early because no unknown scanned rooms existed yet; another bug kept exact counts unavailable because known remote room data could not replace earlier map-visible `UNKNOWN` entries.
- Singleplayer title debugging exists: `/kung test title <doors>` shows the title immediately, while `/kung test dungeonstart <doors>` schedules the same delayed title path used by the run-start signal with a forced door count.
- Dungeon lifecycle detection is deliberately not chat-only. Starting/active run context uses `HypixelInstanceTracker.INSTANCE.catacombs()` plus conservative dungeon fallbacks (`dungeonRunContext`, or dungeon map item while inside the dungeon grid), so the scoreboard/tab instance text remains the preferred source of truth but a temporarily missing Catacombs line does not collapse the map. The map overlay must remain visible for the active sticky dungeon instance even before the map item is received; `hasDungeonMap` is a data source, not a render gate. Ongoing chat/stat/death processing additionally requires an active run. Generic instance end must not send run stats unless a real finish signal already scheduled the summary; this prevents false starts and lobby chat deaths from producing summaries. `/kung instance` prints the current shared Hypixel context for debugging. Visible `[Kung Dungeon]` messages announce `Run started`, `Run ended`, and `Left dungeons`.
- `HypixelInstanceTracker` must not parse arbitrary chat as the current instance. Friends lists (`name is in SkyBlock - ... Catacombs`) and party-finder messages (`name entered The Catacombs...`) are about other players and must not become `instanceLine`. Chat may provide safe warp destinations like "your SkyBlock island"; own sent commands such as `/is`, `/hub`, `/dh`, and `/warp dungeon_hub` may seed a pending destination. Chat must not provide the current `serverId`.
- The player's own `name entered The Catacombs...` message is a special early Catacombs pending destination. It must require a newly promoted transfer server before announcing so it cannot pair `The Catacombs` with the old Dungeon Hub server id. After a pending destination becomes the final `instanceLine`, recompute the final instance flags immediately; otherwise logs can show `instance=The Catacombs` while `catacombs=false`, which delays dungeon detection until later map/chat evidence.
- Safe dungeon lifecycle text like `Starting in 1 second.` may seed a pending `The Catacombs` context destination; this is not a generic chat-based dungeon detector, it is only to prevent stale `Dungeon Hub` context during an already-starting run.
- Fallback dungeon context must reset `missingRunTicks`. A previous bug allowed map-item/grid fallback to start the instance but only Catacombs text reset the missing timer, so Kung repeatedly logged `Left dungeons` mid-run and restarted tracking every few seconds. This caused map flicker and cleared the scheduled door title. If a dungeon instance is already active and the player is still inside the dungeon grid, treat that as `stickyGrid` context unless a known non-dungeon instance is present.
- Do not clear `pendingRunStartSignalTick` before `startDungeonInstance(...)` consumes it. The countdown chat can arrive one tick before Catacombs context becomes visible; clearing it first leaves `realRunStarted=false`, which allows the pre-run missing-context grace timer to end the dungeon during an active run.
- Do not end a dungeon instance merely because `client.level` is a different Java object than at instance start. Hypixel/Fabric can replace the client world object around dungeon entry/spawn while the player is still in the run; an old `activeLevel` identity check caused `Run started`/door title to work while the map immediately disappeared.
- After a real run-start signal (`Starting in 1 second.` accepted or consumed from pending), keep the dungeon map sticky even if Catacombs/scoreboard/map fallback context temporarily disappears. Report one detailed `[Kung Error] Dungeon context missing; keeping map visible ...` line per context-loss window, including ticks/server/instance/map/grid diagnostics, instead of hiding the map or flooding chat. Only explicit non-dungeon instances such as Hub/Private Island/Garden or no client world should end that sticky run.
- Dungeon clear/checkmark state depends on the real Hypixel dungeon map item. In 26.1.2 the filled-map stack can appear unstable for normal inventory lookup; `DungeonMapItems` caches the last map data per client level once seen so checkmarks keep updating. `DungeonMapCheckmarkReader` must also anchor from `PLAYER`/`BLUE_MARKER` decorations when `FRAME` is absent. Trace category `map-check` logs `map=null`, `anchor=null ...`, or observed visible/cleared/completed counts.
- Runtime exceptions during dungeon tick, dungeon chat/game message handling, and map rendering are reported in chat as `[Kung Error] ...` while the map/run is active, with throttling to avoid chat floods. This is intentional diagnostic behavior until lifecycle detection is proven stable.
- `[Kung Context]`, `[Kung Dungeon]`, interface, and tarantula diagnostic chat messages are diagnostics. Keep them behind `Debug > Debug Messages`, and keep generic `Left dungeons` throttled so a bad detector state cannot flood chat through ChatPatch-style repeat grouping.
- The run-start countdown can arrive before the first successful dungeon instance tick. Cache a recent `Starting in 1 second.` signal briefly and consume it when the instance starts; otherwise the `x doors` title can be skipped when Kung recognizes Catacombs one second late.
- Context announcements must never be re-consumed as context input. `HypixelInstanceTracker` ignores `[Kung ...]` messages and records `lastAnnounced...` before calling `sendSystemMessage`; otherwise Fabric's message callbacks can recursively observe Kung's own debug message and crash with a `StackOverflowError`.
- The dungeon footer tracks `M` for Mimic, `P` for Prince kill, and `B` for Bat bonus score. Follow Skyblocker/Devonian-style detection: accept party-chat assists like `Mimic dead!`, `Prince dead!`, and `Bat dead!`; accept Hypixel's exact `A Prince falls. +1 Bonus Score` and `A Bat has been slain. +1 Bonus Score`; use entity death only for Mimic's baby-zombie/no-armor check. Do not mark Bat score from arbitrary bat despawns. Any Kung-originated server chat message, including party/public/guild variants, should start with `[Kung]`.
- At 5 crypts, Kung can send a configurable party message. The setting stores only the message body; `[Kung]` is always added by `prefixedServerChatMessage(...)` and must not be required in the text field.
- Footer secret display is `found - remaining for 300 - max`, not `found - target total - max`. Treat tab/scoreboard `Secrets: found/available` as truth for the found count before deriving from `Secrets Found: %`; deriving found from percent and an estimated total caused small drift. Trace category `score-calc` logs found/target/remaining/max, server secret source, score estimates, failed puzzle count, crypts, and bonus flags whenever the calculation changes.
- Tab-list puzzle parsing must count only actual failed states (`x`, `X`, `✖`) as `failedPuzzles`. Do not count `✦` as a failure; doing so made the projected 300-score secret target collapse to the maximum secret count.
- Room core reuse requires a non-zero stable hash now. Core-only matches are rescanned so the mod can learn stable 26 hashes.
- Room matching is core-first after strict template matches: every known cell hash can produce a visible room label, then adjacent cells with the same room metadata are grouped only when no visible door separates them.
- Soft template matching is conservative. A non-exact component must still have a known hint for the same room; unknown neighboring cores are not absorbed into a room shape.
- `DungeonStateTracker.renderPlan()` triggers stable-hash auto-learning through `DungeonRoomRepository` and refreshes the plan once after a successful learn.

Useful room diagnostics:

- Settings: `Dungeon Map > Room Debug`
- Command: `/kung room debug`
- Command: `/kung roomdata` copies current `roomGrid`, `coreHash`, `stableCoreHash`, first-run hash, known hints, and current match to the clipboard.

If the map shows wrong room names, do not guess. Use `Room Debug` or `/kung room debug` to capture the matched room name, cell coordinates, core hash, and stable hash before editing data.

## Room Data

Bundled data lives under:

- `versions/mc26_1_2/src/main/resources/kung-dungeon-scans`
- `versions/mc26_2/src/main/resources/kung-dungeon-scans`

Important files:

- `known-rooms.json`: canonical bundled room database, including room metadata such as `secrets`, `crypts`, and `prince`.
- `known-room-types.properties`: core-hash to room-type hints.
- `known-room-preloads.jsonl`: observed transitions from preload/empty-ish room hashes to known room hashes.

Active 26er modules should not bundle `known-rooms.jsonl`. The code can still import old JSONL files when migrating archived/profile data, but normal 26er learning and undo are JSON-based.

As of the last merge, the 26er canonical JSON has no cross-room hash conflicts and no room names with conflicting type/secrets metadata. Blaze is a canonical puzzle room with `secrets=1`; wiki-listed puzzle rooms without secrets, including Ice Path, use `secrets=0`. Prince room classification lives in `known-rooms.json` as a boolean `prince` field; the Java fallback list is only for migrating older room files that do not have that field yet.

- `Blaze|PUZZLE|1`: core/stable pairs `1286919098/759798534` and `-1261755590/994913400`

Rooms still reported as only core/unstable by `tools/analyze-room-data.mjs` need more in-game observations before they are fully reliable.

Bundled Jar room data is always loaded. Local learned room data and local room type/preload hints are an opt-in development/override layer behind the `Local Data` setting, so stale profile files cannot silently affect normal player matching.

- `%APPDATA%/ModrinthApp/profiles/<profile>/kung-dungeon-scans`

The current 26.1.2 play profile is `Dungeons 26.1.2`. Older saved traces may still live under `Here We Go Again (2)/kung-debug`; use those only as archived diagnostics.
The explicit 26.1.2 Gradle task `syncLocalDungeonRoomData` copies local `known-rooms.json`, `known-room-preloads.jsonl`, and `known-room-types.properties` from that profile into `versions/mc26_1_2/src/main/resources/kung-dungeon-scans`. Normal builds do not run this task automatically, so local room data is never baked into a Jar as a cleanup side effect. The profile can be overridden with `-PkungProfileDir=...` or `KUNG_PROFILE_DIR`.

Room learning command notes:

- `/kung room learn <name> [secrets] [type] [crypts]` learns only the current room cell.
- `/kung room look <name> [secrets] [type] [crypts]` learns the adjacent room cell in the player's look direction; it does not require aiming at a block.
- `/kung room learnmulti <name> [secrets] [type] [crypts]` intentionally learns a connected multi-cell room. It refuses one-cell results, refuses more than 4 cells, stops at visible doors, and will not cross into cells already known as another room.
- Room names can be multi-word without quotes, e.g. `/kung room learn shadow assassin`. If quotes are used anyway, the parser strips one wrapping quote pair before saving, so `"shadow assassin"` is stored/displayed as `shadow assassin`.
- `/kung room delete <name> [secrets] [type]` removes the room from the local canonical JSON and writes a `deletedRooms` tombstone. Name-only delete suppresses every local/bundled room entry with that name; metadata delete only suppresses that exact `name/type/secrets` key. Re-learning the same room later keeps the tombstone but adds fresh local hashes after it.
- Room learning also records the first non-empty hash observed for the same room cell during the run when it differs from the current hash. This helps preload/early-scan hashes get attached to the right room without repeated manual scans.
- The room catalog rejects suspicious variants while loading: more than 4 cells, too wide/tall, or disconnected. This prevents corrupted local data from drawing a room over the whole dungeon.
- Core-only fallback groups never create more than 4 cells. If bad data would connect too many same-named cells, Kung splits them into one-cell labels instead of one large rendered room.
- Deathmite is `NORMAL` with `secrets=6`. Bad multi-cell variants were removed, but legitimate 1x3/1x4 Deathmite variants should be re-learned with `learnmulti`, not normal `learn`.

## Feature Map

- Dungeon map and score: `feature/dungeon`
- Superpairs helper: `feature/misc/SuperpairsHelperFeature`
- Lobby hop helper: `feature/misc/LobbyHopHelperFeature`
- Tarantula helper: `feature/slayer/TarantulaHelperFeature`
- Screen/interface tracking: `feature/screen/ScreenTracker`
- Update installer: `update/KungUpdater`

## Build Commands

Use Java 25.

When running inside Codex's managed sandbox, run the main build with network/escalated permissions directly. Do not first try the sandboxed Gradle command if the environment is network-restricted; it predictably fails while downloading the Gradle distribution with `Permission denied: getsockopt`. Ask for the build permission once and execute the real build path immediately.

```powershell
./gradlew.bat :versions:mc26_1_2:build
```

Only build `mc26_2` when the user explicitly asks to resume or port 26.2 work:

```powershell
./gradlew.bat :versions:mc26_2:build
```

Do not copy a built jar into a Modrinth profile while Minecraft/Java is running.

## Maintenance Tools

- `tools/convert-known-rooms.mjs`: merges canonical JSON, archived legacy JSONL, or directories containing those files into canonical `known-rooms.json`.
- `tools/analyze-room-data.mjs`: audits room data for duplicate/conflicting hashes and suspicious low-confidence entries.
- `tools/check-26-sync.mjs`: compares key 26.1.2 and 26.2 files for drift; use only when the parked 26.2 port is explicitly resumed.

## Safe Cleanup Targets

Good cleanup:

- Extract debug formatting or pure parsing into small package-private helpers.
- Improve docs and command descriptions.
- Add diagnostics that are behind config toggles.
- Reduce duplicated constants inside the same feature.
- Add scripts that read project data and report risks.

Risky cleanup:

- Moving code into a shared module before checking Minecraft API differences.
- Reworking `KungConfigScreen` layout or row sizing.
- Changing dungeon matching thresholds without screenshots/debug output.
- Editing bundled room data without hash evidence from `/kung room debug`.
