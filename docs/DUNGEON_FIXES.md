# Dungeon performance and feature fixes — 2026-09-11

Scope: the active Minecraft 26.1.2 module. Existing updater changes and the parked
26.2 module were left alone. The follow-up adds the requested map accessibility
controls and updates the splits overlay.

## Evidence

- The supplied traces repeatedly report Mimic searches across 215–225 loaded
  chunks, including full block-state searches every half-second. This was tied to
  the Dungeon Map toggle and did not depend on player count or map completion.
- Prince icons called `hasPrince` for every labelled room every frame. Each call
  checked the profile database file and normalized every template/hash name using
  regular expressions. The isolated 24-room workload measured about 6.6 ms/frame
  before the change and about 0.01 ms after warm-up with the index. Rendering now
  reads the Prince flag directly from the already matched room, removing these
  lookups from the HUD entirely. These are CPU microbenchmarks, not live FPS results.
- Room matching and Blood Rush path searches were repeated when only visit or
  clear state changed. Door title diagnostics also recomputed the same path.
- The countdown erased pre-run room observations. Updates with the same raw hash
  but a changed stable hash were rejected. The map-player observation timer could
  overflow its `Long.MIN_VALUE` sentinel and never start.
- Fairy-adjacent doors were excluded from Blood Rush using their render target
  color. This incorrectly dropped locked exits. Door detection also required an
  open sky block above the frame before recognizing even a locked special door.

- The follow-up trace `kung-trace-20260910-230215.log` and the Mimic code path
  exposed a second issue: current loaded candidates also determined persistent map
  knowledge, so leaving chunk range removed an already discovered room.
- Instance context had accumulated chat destinations, map/grid guesses and grace
  timers. The sidebar parser also needed to reconstruct Hypixel's team prefix and
  suffix instead of treating invisible score owners as the displayed location.
- M7 used the F7 split list, with no Relics or Dragons phases. The older supplied
  `kung-trace-20260910-190532.log` includes the actual Wither King dialogue needed
  for these transitions, including messages hidden by Kung's chat filter.
- In `kung-trace-20260910-234543.log`, F1 boss entry at 23:45:05 briefly reads
  server `m61` between updates of `m61e`. Per-packet sidebar reads falsely ended
  the run; there was no world transition. At 23:45:34 the run finishes with
  `Team Score: 191 (B)`, without the previously required `Defeated` banner.
- The same trace contains exact Floor I entry banners before each transfer;
  older M7 traces use `entered MM The Catacombs, Floor VII!`. Floor metadata is
  available before countdown even when the tab only says `Dungeon: Catacombs`.
- `kung-trace-20260911-001338.log` ends a solo M7 run with Team Score 11 (D)
  after 57.6 seconds, while still in instance `m64ar`. Its active Blood Open time
  became `--` because completion required the last boss phase. Preserve this
  interrupted phase's measured progress and the Boss Entry progress display.

## Changes

- Mimic discovery lives in `DungeonMimicChestScanner`: nearby block entities are
  checked promptly, the dungeon grid is traversed incrementally, and at most one
  full chunk fallback is performed per tick. Cached candidates survive partial
  scans, are revalidated against the loaded chunk, and are discarded on world changes.
  `DungeonMimicChestMemory` separately retains discovered positions for the run.
  Unloaded chunks and empty visible results no longer remove the map mark or count
  as a Mimic kill. Current loaded candidates still drive world highlights. Room
  reclassification/conflicting evidence may invalidate a mark; confirmed kills,
  map-generation changes, feature disable and instance exit clear its memory.
- World scans use bounded round-robin batches with a 2 ms cooperative budget.
  A column/chunk already being read can exceed that budget; work stops afterwards.
  Locked doors get a separate two-tick refresh. Known rooms keep being refreshed.
- Room matching is cached by actual scan and catalog revisions. The Blood Rush
  path is computed once per render plan. Catalog lookups no longer stat files;
  learning, settings changes, sync and explicit reload invalidate the cache.
- Scans, map reads and sync payload construction are gated by enabled consumers.
  Instance/party/lifecycle tracking stays available. The original entrance is kept
  for activation of the map partway through a run.
- Pre-run observations survive the countdown. Both raw and stable hashes are
  considered when accepting updates. Same-cell transitions into directly known
  rooms create session-only aliases keyed by both hashes. Conflicting aliases are
  discarded. No unverified room hashes were added to the bundled database.
- Door frame geometry no longer hides locked special doors. Unknown solid blocks
  do not count as opened doors. Known room templates cannot merge through a door.
- Fairy entrance selection prefers the path from Start and stays consistent when
  the exit opens. Locked exits count independently of local/remote display colors.
- Exact door titles require a continuous, recognized path supported by map visibility
  or physical scans. Matching raw/path totals alone are insufficient. Counts and
  wording are isolated in `BloodRushDoorEstimate`.

## Instance detection

`HypixelInstanceTracker` consumes applied sidebar, team, objective, tab and
player-info packets. Hooks run at `TAIL` on the client thread, after vanilla has
updated its state. `SkyBlockSidebar` joins the real team prefix/suffix and restricts
instance evidence to owners refreshed in the current world. The first server
position packet separately establishes when entrance/grid coordinates are usable.
Sidebar updates are coalesced at the end of the client tick, so an intermediate
prefix/suffix rewrite cannot create a server change within that packet batch.
Login/Respawn/disconnect invalidation remains immediate.

`HypixelLocation` accepts exact Catacombs floor lines and explicit location fields.
Dungeon Hub and Kuudra's Hollow are distinct non-dungeon kinds. Buffs, boss names,
party entries, social chat, transfer messages and sent commands cannot establish
the current location.

`HypixelInstanceState` keeps confirmed evidence within one world epoch. Login,
Respawn, configuration-start, disconnect, a changed server id or the client-world
identity fallback invalidate the previous instance immediately. Missing UI rows
within the same epoch preserve a confirmed location; a newly supplied different
location replaces it. Separate sidebar/tab change tracking prevents unrelated
packets from restoring an unchanged stale field. Server ids are read from current
structured sidebar rows, never paired with a previous world's location. A changed
server row clears all other old evidence before a fresh location is accepted.

Shared context changes immediately reach `DungeonStateTracker` through
`DungeonEventRouter`. Dungeon membership no longer depends on map possession,
grid coordinates, waiting periods or an earlier countdown. Boss rooms outside the
map grid remain inside a confirmed Catacombs instance. Leaving clears the map and
stops its consumers; a new Catacombs instance starts with fresh observations.
Debug context messages still require a complete location/server pair and their
normal debug toggle.

`HypixelDungeonFloorState` holds floor metadata independently of location. Exact
server GAME entry banners describe a subsequent transfer, expire after 30 seconds,
and apply only after Catacombs is independently confirmed. Multiple world hooks
before confirmation preserve the pending metadata; another destination or
disconnect discards it. Structured current floor fields override a hint. Entrance
is explicitly known floor 0, while unknown is -1. No entry message can activate
the map or establish dungeon membership.

## Map accessibility and splits

The hidden screen-size clamp was removed from map rendering, so `/kung hud`
scrolling and the new Map Scale slider apply the saved 25–300% scale consistently.
A separate Text Scale slider supports 50–200% and scales all map-overlay text:
room names, secrets, `?` markers, debug labels, Prince glyphs, footer and legend.
Footer spacing and editor bounds include the larger text. Overlapping HUD boxes
use the same target selection for clicking and scrolling.

Splits remain hidden during pre-run preparation and start on the accepted countdown.
Known floor/master metadata prepares the phase list before start and is retained
when run statistics reset. The HUD editor previews the known floor. Automatic
phase changes are ordered and idempotent, preventing delayed/repeated messages
from rewinding progress. Unknown phase boundaries display `--` instead of an
invented duration. Real elapsed time and server-tick time are separate and freeze
at completion or abort. Server-tick mutations also run on the client thread.

M7 now proceeds through Maxor, Storm, Terminals, Goldor, Necron, Relics, Wither King
dialogue and Dragons after the three clear splits. Necron's death starts
Relics; the first Wither King introduction begins its dialogue split; the combat
announcement begins Dragons. The victory dialogue completes Dragons (Necron on F7);
there is no separate Victory phase. Total alone continues until the final server
`Defeated ...` or `Team Score: ...` result ends the run. Late M7 recognition preserves the Relics
boundary. Lower floors explicitly name their final combat phases, including Bonzo
Phase 2, Scarf, Final Guardian, Thorn, Livid and Sadan.

The overlay uses compact colored phase names, right-aligned real times with server
times in parentheses, Boss Entry and Total rows, and a sample in the HUD editor.
A red `-10.0s` suffix shows each settled phase's positive real-minus-server time.
An optional Time Lost row below Total updates only at phase boundaries and run
end. Active phases never generate changing loss suffixes. Zero/negative differences
and unknown phase boundaries do not invent loss suffixes. The Format setting
switches real/server durations between `1m 30.12s` and `90.12s`; Time Lost toggles
both suffixes and the additional row. Disabling loss removes its reserved column
and row from HUD editor bounds. Defaults preserve Minutes and enabled loss.
At a wipe/abort, `stoppedCurrentSplit()` stores the measured unfinished phase
separately from completed phases. All result values and floor names remain frozen
and visible until the dungeon instance is left; starting a new run clears them.
Splits attach before the vanilla player list, matching the map's draw order, so
container backgrounds dim both and F1 hides both.
The design and existing floor boundaries were checked against the official
[Odin Splits HUD](https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/skyblock/Splits.kt)
and [SplitsManager](https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/SplitsManager.kt).
The M7 extension also uses the supplied trace dialogue and
[Odin KingRelics](https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/boss/KingRelics.kt),
which treats Necron's death line as the Relics boundary. Kung's chat filter still
forwards hidden boss messages into the tracker.

## Run statistics

The exact Team Score result line now completes runs without a Defeated banner.
Repeated result messages cannot duplicate the summary. A completed run's pending
summary is flushed on exit before its state is reset, without waiting for a final
API request or observing players from the next world. Old API callbacks cannot
mutate a new run. The local chat output uses `addClientSystemMessage` and a remembered
roster, so it does not require a LocalPlayer during disconnect. The first countdown
preserves the prepared party roster and API secret baselines while resetting run
counters; genuine instance resets still clear them and invalidate pending requests.

Player Tracking requests bounded room scanning even when the map is disabled.
First-clear witnesses determine estimated minimum/maximum rooms. Late recognition
merges room cells without double-counting, and Start/Fairy/Blood are excluded.
The summary displays each tracked party player's run secrets and estimated room
range, plus total Party Secrets (server counter when available, otherwise observed
player totals). Optional P/M/B suffixes require explicit named claims or an actual
observed Mimic damage-source player. A global Prince/Bat event or generic party
`Mimic dead!` message does not identify a killer and produces no player suffix.

## Sounds

The built-in 5-Crypt and Lobby Hop notification plings now use listener-relative
`SimpleSoundInstance.forUI` playback, so movement does not leave the source behind.
Imported custom sound files already use direct audio-device playback without a
world position, distance attenuation or Doppler effect; that path and its
volume/pitch controls remain intact.

## Validation and live follow-up

The 26.1.2 Gradle build passed with 138 tests, zero failures, errors and skipped tests.
The produced artifact is `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.8.jar`. Regression
coverage includes hash-only updates, preloads and conflicts, scan scheduling,
feature demand, mid-run entrance preservation, room boundaries, Fairy exits,
unknown solid door blocks, incomplete paths and title wording. Follow-up coverage
includes current-world location parsing and transfer invalidation, real sidebar
text, Kuudra exclusion, Mimic memory across missing visible candidates, map/text
scale bounds, full M7 progression, duplicate messages, late floor metadata, unknown
split boundaries and frozen completion/abort clocks.
Further coverage includes the F1 sidebar rewrite and Team Score completion,
pre-run floor selection/hidden HUD, floor hints scoped to a confirmed transfer,
countdown baseline retention, disconnected roster, room attribution merges and
named P/M/B claims.
The latest tests cover the logged M7 wipe, retained interrupted-phase values,
frozen floor metadata, phase-boundary-only loss updates, Minutes/Seconds formatting,
saved settings/default migration, and editor bounds with loss disabled.

`tools/audit-dungeon-traces.mjs <kung-debug directory>` reads trace hashes and
compares them with bundled room data without writing to the profile.

Fresh saved logs include `dungeon-performance` aggregates for `map-render`,
`room-scan` and `mimic-scan` (sample count, average and maximum milliseconds), plus
`room-preload` evidence when a new session alias is observed. Test in a real dungeon
with Map, Prince icons and Mimic enabled, including pre-run, Fairy exit opening,
fully revealed map and hot activation. Live Minecraft FPS cannot be established
from the offline build or historical traces.

The follow-up still needs live verification: leave a discovered Mimic room beyond
chunk range and return; enter/leave Catacombs via warps and disconnects; enter Kuudra;
resize map/text in the HUD editor; finish a complete M7 including P5; and trigger
sounds while moving quickly. Also complete F1 through Bonzo and check that both
overlays stay visible, splits begin with the selected floor, and Run Stats appears
after Team Score, including when immediately leaving. The build and tests do not establish live packet
ordering, visual readability on the friend's display, sound-device behavior or FPS.
