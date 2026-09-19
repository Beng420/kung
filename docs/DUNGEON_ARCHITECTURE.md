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

Floor selection is separate: `HypixelDungeonFloorState` observes original non-overlay
`ALLOW_GAME` entry metadata before display filtering and may carry it across a
transfer for 30 seconds, but it still requires independently
confirmed Catacombs context. Current structured floor fields win. Entrance (0)
differs from unknown (-1). Map items, coordinates, countdowns and warp chat do not
prove membership. `positionKnown` only gates use of world coordinates.
Boss-name inference in run statistics supplies a floor number, not normal/master
mode. Splits accept exact shared metadata or structured SYSTEM floor fields; common
boss dialogue selects the phase layout without enabling PB/AVG comparisons or
saves. Wither King dialogue can independently confirm M7. Missing mode stays
unknown, including after failed transfers or summons without a new entry banner.

`DungeonEventRouter.observeInstanceChanged()` synchronizes the dungeon state.
Preparing a run scans rooms and records floor/roster metadata before the timer;
it does not show the live splits HUD. The accepted `Starting in 1 second.` signal
starts it. Room observations survive countdown; `DungeonRunStats.resetForCountdown()`
preserves prepared roster/classes and API secret baselines. A full reset belongs
to instance replacement.
Before the first split, Mort's exact map-greeting message confirms the playable
start and rebases both split clocks once, excluding the countdown. Missing Mort
keeps the countdown fallback; duplicate/late greetings cannot rewrite completed
phases or manual runs. This timing adjustment does not reset instance/run statistics.
The HUD editor can still preview splits before a run.

Dungeon messages are observed once through Fabric's `ALLOW_GAME` / `ALLOW_CHAT`
events before display cancellation or modification. Kung's own chat filter only
controls visibility; it no longer forwards hidden messages separately. The shared
tracker retains instance/workload gates and game/chat/actionbar source distinctions.
An optional `SkyblockerDungeonScoreMixin` forwards accepted bonus-kill events that
may arrive through Skyblocker's internal sync instead of chat. Existing dungeon,
started-run and statistics-workload gates apply; per-run bonus flags deduplicate
the reports without announcements or contributor credit. No polling, new sync
connection or cross-instance retained state is introduced.

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
  New map visibility/connectors and full-world-cell observations also advance the
  matching revision; repeated observations and player/checkmark changes do not.
- Match strict templates first, then known cell hints. Soft matches cannot absorb
  unknown neighboring cores. Pre-run observations survive countdown and can become
  aliases of a later known room; see [room data](ROOM_DATA.md).
  Preload identity aliases stop for each visible/fully loaded cell, but that gate
  must not disable connections between observed cells. Missing map connector pixels
  do not prove a wall; explicit narrow map doors still constrain even strict matches.
  Full-world evidence checks at most nine chunk presences per scanned room cell in
  the existing batch budget, without loading chunks or adding block scans.
  Render fallback identity hints use the same preload gate.
  Preserve a directly recognized observation when puzzle/block changes produce
  unknown hashes. A new direct known hash can replace it; preload-only observations
  receive no such protection. Instance reset clears both kinds of evidence.
- Render logical `roomOwners` joined by `internalDoors`, not each raw match as an
  independent shape. This keeps Layers connected. Cache the layout by render-plan
  identity, keep groups at most four cells and respect boundaries. Render-only
  merges must never be persisted as catalogue matches or used for room learning.
  Both owner-union passes enforce the four-cell limit before expanding visited/
  cleared/completed state. Narrow map connectors are external doors even when
  both sides have identical room metadata; broad connectors can still join
  fragments of one room. See [Bridges merge](ROOM_DATA.md#bridges-owner-merge--2026-09-18-222334).
- `DungeonRoomPrediction` supplies a separate cached render-only list: at least two
  exact cell hashes may imply every remaining unseen cell of a room only when all
  compatible template rotations/mirrors leave one footprint. This includes two of
  four cells completing a 2x2 or 1x4 room. Complete and larger compatible variants
  count as alternatives. Conflicting cores, explicit doors, visible cells and fully
  loaded empty cells constrain candidates. Becoming fully loaded advances matching
  inputs even when an empty cell's hashes do not change; that visibility survives
  chunk unloads. Reuse predictions across player/checkmark updates and repeated
  observations. `DungeonRoomRenderLayout` alone adds
  their cells/connectors and the viewport includes them. Predicted cells never enter
  scan points, learning matches, logical owners, score/clear credit or live room sync.
- `DungeonMapItems` caches the last real map data per client level. Checkmark
  anchoring accepts PLAYER/BLUE_MARKER when FRAME is absent. Inspect `map-check`
  and `map-change` before assuming a drawing defect.
  The bounded map reader also recognizes Trap from a majority of exact palette
  value 62 in the room interior; normal rooms use 63, so base color alone is unsafe.
  Type evidence lasts for the current snapshot and invalidates the plan once.
  Existing identity matches/hints win. Without identity, only the render layout
  supplies a generic `Trap` label with unknown secret/crypt totals; it never adds
  a catalog match or learning hint. Completion still comes from the map checkmark.

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

The server's Blood Door opened message ends automatic Blood Rush titles for the
run. Completion cancels a pending initial title/fast-door scan request and stops
progress updates; later world scans cannot resume remaining-door announcements.
New-run scheduling and instance exit reset this state. Explicit debug titles are
unchanged.

### Late door scans after Blood opened — 2026-09-19, 17:08:10

The supplied trace confirms Blood open and Kung's completion title at 17:07:15.592.
Watcher dialogue follows immediately. At 17:07:35.556–.759, three Wither-door scans
catch up, while the stored Blood Door stays locked until 17:07:55.357. There is no
intervening lifecycle reset. The same profile's Minecraft log confirms late
`3+ doors left`, `2+ doors left`, and `1+ door left` messages; it does not contain
the reported literal `Blood next` for that interval. Path visibility remains
incomplete in this trace.

`observeProgress` previously checked completion only in its zero-remaining branch.
Positive counts still emitted titles after `bloodRushDoneShown` was set. The
completion guard now covers the entire automatic progress path. Two regressions
fail before the fix and pass afterward: late scans with both exact/inexact
estimates, and completion while the initial title is still pending. They also
cover duplicate completion, ignoring pre-run messages and rearming the next run.
The reason the world scans arrived late is not established by this trace; the
fix uses the already confirmed server event without expanding scan budgets.

## Mimic lifetime

`DungeonMimicChestMemory` retains room evidence across distance and chunk unloads.
Empty loaded scans do not prove a kill. Conflicting evidence, reclassification,
confirmed kill, map reset, feature disable or instance exit can update/clear it.
Loaded candidates control the 2D waypoint; remembered evidence controls the map.
Since 0.3.3, Mimic ESP has no world-render callback or 3D chest box. Its HUD waypoint,
candidate filters, kill tracking and room memory remain active under the same toggle.
The exact static-chest filter, rotation units, cache invalidation and 512-block-read
budget are specified in [MIMIC_STATIC_CHESTS.md](MIMIC_STATIC_CHESTS.md).
