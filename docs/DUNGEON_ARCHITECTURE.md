# Dungeon architecture

Current contracts extracted from the handoff on 2026-09-12. Use the
[code map](CODE_MAP.md) for source/test entry points and
[development guide](DEVELOPMENT.md#diagnostics) for traces.
Historical reproductions remain in [DUNGEON_FIXES.md](DUNGEON_FIXES.md).

## Instance and run lifecycle

`HypixelInstanceTracker` is the shared location authority. Applied packet hooks
in `ClientPacketListenerMixin` run at TAIL on the client thread. Score/team/sidebar
changes are coalesced at the end of the tick: Hypixel can rebuild a server row as
`m61e -> m61 -> m61e` during an F1 boss teleport. Treating that intermediate row as
a transfer used to discard both overlays. Login, respawn, configuration-start,
disconnect and world replacement invalidate old evidence immediately.

`SkyBlockSidebar` reads team prefix/suffix text, not synthetic score-owner names.
Only owners refreshed in the current world may establish a location. Sidebar and
tab evidence have independent revisions; a new server row cannot reuse an old
location row, and unrelated packets cannot revive the other source's stale state.
`HypixelInstanceState` advances an epoch on world/server changes. Temporarily absent
UI rows may retain confirmed context only within that epoch; explicit other
locations replace it. Kuudra and Dungeon Hub are not Catacombs.

Floor selection is separate: `HypixelDungeonFloorState` may carry exact GAME entry
metadata across a transfer for 30 seconds, but it still requires independently
confirmed Catacombs context. Current structured floor fields win. Entrance (0)
differs from unknown (-1). Map items, coordinates, countdowns and warp chat do not
prove membership. `positionKnown` only gates use of world coordinates.

`DungeonEventRouter.observeInstanceChanged()` synchronizes the dungeon state.
Preparing a run scans rooms and records floor/roster metadata before the timer;
it does not show the live splits HUD. The accepted `Starting in 1 second.` signal
starts it. Room observations survive countdown; `DungeonRunStats.resetForCountdown()`
preserves prepared roster/classes and API secret baselines. A full reset belongs
to instance replacement.
The HUD editor can still preview splits before a run.

Boss teleports within the instance retain clear-map and run state. Boss-map
selection is presentation only; see [boss maps](BOSS_MAPS.md). Completion signals
(`Defeated ... in ...` or the exact Team Score result pattern) are idempotent.
Final stats/splits remain until instance exit. Leaving flushes an already scheduled
completed summary before reset; leaving by itself does not declare victory.
Team Score may precede the `Defeated ... in ...` banner. Splits freeze at the first
completion signal, but retain the final-phase sample for five seconds so a matching
victory can confirm its PB without changing either clock or repeating completion.
Reset/new countdown clears this pending confirmation; ordinary instance gating still
applies. See [split PB ordering](RUN_STATISTICS.md#score-before-victory-pb-correction--2026-09-15).

## Map pipeline and budgets

`DungeonScanRecorder` -> `DungeonMapSnapshot` -> `DungeonRoomRepository` /
`DungeonKnownRoomCatalog` -> `DungeonLiveMapWriter.MatchRenderPlan` ->
`DungeonRoomRenderLayout` -> `DungeonMapFeature`.

- Scan in bounded batches with a cooperative 2 ms budget; locked-door checks run
  every two ticks. Keep scheduling separate from rendering. Do not restore
  recurring full-grid scans, file reads or catalogue normalization per frame.
- Gate expensive work by its consumers. Player Tracking and Blood Rush may need
  scans with the map disabled; Splits alone does not need world scans.
  Preserve mid-run enable/disable behavior and shared lightweight context.
- Cache catalogue matches separately from visit/checkmark changes. Reuse core
  observations only with a nonzero stable hash; core-only observations need
  rescanning. A stable-hash change must refresh recognition even if core is unchanged.
- Match strict templates first, then known cell hints. Soft matches cannot absorb
  unknown neighboring cores. Pre-run observations survive countdown and can become
  aliases of a later known room; see [room data](ROOM_DATA.md).
- Render logical `roomOwners` joined by `internalDoors`, not each raw match as an
  independent shape. This keeps Layers connected. Cache the layout by render-plan
  identity, keep groups at most four cells and respect boundaries. Render-only
  merges must never be persisted as catalogue matches or used for room learning.
- `DungeonMapItems` caches the last real map data per client level. Checkmark
  anchoring accepts PLAYER/BLUE_MARKER when FRAME is absent. Inspect `map-check`
  and `map-change` before assuming a drawing defect.

Map Scale and Text Scale are independent controls in `/kung hud` and the optional
OneConfig HUD editor. Both editors share Kung's stored position/scale; the native
adapter subtracts the scaled border/text margin from box coordinates. Its map
preview is only a labeled bounds box and must not trigger room learning or scans.
Text scale must
cover labels, secrets, unknown/debug markers, Prince glyphs, footer and legend,
including their measured bounds. Do not silently clamp map size to the window.

## Player markers

Use a fixed five-slot dungeon roster from identified tab/class rows and self.
`HypixelPartyTracker` may resolve names to UUIDs but its global membership/history
must not add dungeon participants. The local player occupies slot 0 / FRAME; BLUE_MARKER
decorations map sequentially to slots 1–4. Missing identity means skip and trace,
not guess from nearby entities, ranks or anonymous decorations.

Resolve parsed names before trusting raw tab-row UUIDs: Hypixel can use fake rows.
Loaded entities can improve position, rotation and skin for an established roster
identity. Remember the last real skin per UUID; resolve class by UUID with a
remembered-name fallback. Class rows need not start with a level prefix.
Inspect `player-slots` for identity sources and `player-markers` for slot/class/
skin decisions before changing colors or UUID caches.

## Blood Rush

The initial title is scheduled from the countdown. Until an uninterrupted,
recognized START-to-BLOOD path is supported by map visibility or physical scans,
report an observed lower bound with `+`; never substitute a magic fallback count.
Equal raw/path totals alone do not establish an exact count. Map-visible unknown
rooms matter even if they have not been physically scanned yet.

The lower bound includes observed non-start Wither/Blood doors, opened doors and
locked Fairy exits. Exclude START-touching doors and the established Fairy entrance;
Fairy's render color does not exempt its locked exit. A locked door becoming OPEN,
or losing its coal/red blocks, updates `openedLockedDoors()` and triggers progress
even when the rendered path count stays unchanged.

## Mimic lifetime

`DungeonMimicChestMemory` retains room evidence across distance and chunk unloads.
Empty loaded scans do not prove a kill. Conflicting evidence, reclassification,
confirmed kill, map reset, feature disable or instance exit can update/clear it.
Loaded candidates control the 2D waypoint; remembered evidence controls the map.
Since 0.3.3, Mimic ESP has no world-render callback or 3D chest box. Its HUD waypoint,
candidate filters, kill tracking and room memory remain active under the same toggle.
The exact static-chest filter, rotation units, cache invalidation and 512-block-read
budget are specified in [MIMIC_STATIC_CHESTS.md](MIMIC_STATIC_CHESTS.md).
