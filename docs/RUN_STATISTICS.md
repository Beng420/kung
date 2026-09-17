# Run statistics and splits

Current contracts as of 2026-09-13. Source and regression entry points:
[CODE_MAP.md](CODE_MAP.md). Instance preparation, countdown and exit behavior:
[dungeon lifecycle](DUNGEON_ARCHITECTURE.md#instance-and-run-lifecycle).

## Statistics sources

| Observation | Meaning / owner |
| --- | --- |
| Shared tab `Secrets Found: n` | Party count; no player attribution |
| Party `Secrets Found: n%` | Party percentage; no player attribution |
| Party `Secrets: n/total` | Exact party count and denominator |
| Actionbar `n/total Secrets` | Current room observation |
| Verified remote personal report | Sender's own count, with personal source, current dungeon roster and timestamp |
| API achievement delta | Fallback only with a valid run baseline |

`DungeonSecretCounts` parses source-specific values; `DungeonSecretCounter`
prioritizes an explicit personal measurement, then verified self-report, then valid API delta. Exact
snapshots can correct stale higher counts. Missing API values (-1), callbacks from
an obsolete run and a baseline arriving after the final fetch cannot create credit.
Never attribute a party delta to self, associate adjacent tab rows with a player,
or infer personal counts from arbitrary chat. Unknown counts display `?`.
The integer `Secrets Found: n` is also shared: 0.3.0 incorrectly treated it as
personal and could display the entire party total beside the local player.
No current tab parser supplies a personal count. API baselines are requested
while preparing the instance, before countdown, and preserved across countdown.

Party totals use exact server counts, a compatible percentage plus complete room
metadata, or a complete set of personal counts. An incomplete set is unknown.
Catalogue totals count each logical clearable room owner once, are unavailable
while any observed clearable owner lacks metadata, and are cached by render-plan
identity. A candidate denominator must reproduce the displayed percentage after
integer counting and one-decimal rounding. Map and summary share this logic.

`DungeonDeathTracker` accepts anchored system death messages only for already-known
players. Death lines never create identities; forwarded/social chat and actionbar
text are not deaths. Reconcile exact Team Deaths counters without double-counting
events; merge aliases on UUID remapping. The invented player `Party` was a parser bug.

Server Score takes precedence over estimates; final Team Score is authoritative
over later ordinary Score fields. Footer secrets mean **found - remaining for
300 - max**, not target total. The secret target is planning guidance assuming
the remaining rooms and solvable puzzles are finished; reaching zero remaining
secrets alone does not mean the displayed score has reached 300.

## Score and unfinished rooms

The map's score retains the Blood/boss completion forecast, but must not spend
that credit on unfinished clear rooms. Discovered or visited Puzzle, Yellow,
Trap and ordinary rooms remain outstanding until clear evidence arrives.
White checkmarks suffice for ordinary room credit, independently of secrets;
puzzle completion uses a green map check or successful tab state. The map reader
also classifies a red puzzle cross as `CLEARED`, so that state alone cannot prove
a solved puzzle. The old late-run shortcut that filled all remaining cells when
every room had been visited is removed. All unfinished clear cells cap the
room projection, including when tab completed cells already equal the denominator.

Each unfinished puzzle subtracts **10 skill points**, separately from the room's
missing share of the 80 skill / 60 exploration room points. The former fixed
14-point deduction for failed puzzles was not a separate puzzle penalty.
`✦` and `?` are unfinished, `x`/`X`/`✖` are failed, and `✓`/`✔` are solved.
The `Puzzles: (n)` header includes undiscovered puzzles. Repeated snapshots are
deduplicated by puzzle name; temporarily absent rows retain evidence, explicit
new states replace old states, and a run reset clears it. Map and tab counts
are reconciled rather than added. If tab confirms every puzzle solved, it can
release stale puzzle map cells; partial tab totals cannot identify which still
open map room was solved.

An explicit tab room total takes priority. Otherwise `Completed Rooms` and
the rounded server `Cleared: n%` (including `Cleared: n% (n)`) derive the room-cell
denominator; map cells are the fallback. A completed count alone cannot establish
the total size of a dungeon. Score uses cells consistently, while player room
totals continue to use logical owners. Map clear/puzzle counts are cached by
render-plan identity, with no new world scans. `score-calc` records unfinished
clear cells, unfinished/failed puzzle counts, the separate penalty and projected
cells alongside source selection, secret target and bonuses.

The penalty and Blood/boss accounting were cross-checked against the public
[Noamm score calculation](https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/utils/dungeons/map/handlers/ScoreCalculation.kt)
and [Skyblocker DungeonScore](https://github.com/SkyblockerMod/Skyblocker/blob/main/src/main/java/de/hysky/skyblocker/skyblock/dungeon/DungeonScore.java)
on 2026-09-13. These are client implementations, not server source.

Regression cases cover enough secrets with an open puzzle, tab completion ahead
of the map, unknown puzzle map types, pending/failed states without duplicate
penalties, open Yellow/Trap/ordinary rooms, white clear checks, Blood/boss credit,
the parenthesized sidebar format and authoritative final scores. Live comparison
at boss entry and the final Team Score remains required; local tests do not
establish server timing or the exact score of the user's reported run.
The 2026-09-13 Java-25 build passes **327 tests**, including twelve new puzzle/
score regressions. In the all-secrets/open-puzzle fixture, the old formula would
show 302; the corrected estimate is 292, then 307 after the puzzle is confirmed.

## Room credit and summary

`Dungeon > Player Stats` gates the entire end-of-run chat summary: heading, rooms,
score, party secrets, crypts and player rows. A disabled switch also consumes a
pending summary without waiting for final API fetches, including the instance-exit
flush. Checking again at output time covers disabling the feature after completion.
Shared statistics remain available to the map and other enabled consumers.

`DungeonRoomProgress` counts logical owners, including white checkmarks, while
excluding Start/Fairy/Blood. Secret-completion markers and tab cells are not room
totals. `DungeonRoomClearAttribution` retains first-clear witnesses and merges
late-recognized cells without duplicate credit. Player room ranges remain visible
without an extra `estimated min-max` label.

`DungeonRunSummaryLayout` measures names using the active font and the one-pixel
spacing font to align Secret columns. Summary output uses the remembered roster
and client chat component, so completion can flush even after LocalPlayer disappears.
The roster admits self and identified dungeon class rows only, up to five players.
Global party history and general online-player caches never supply extra run
participants. UUID remapping preserves one canonical row and its counters/slot;
instance reset clears membership, while countdown and result display retain it.

Missing personal counters get a short availability explanation in the summary.
API-off/no-key state is distinguished from enabled API data that was not received;
neither state creates zero counters. `run-statistics` traces record the actual
summary roster, each count/source and API availability without credentials.
The sync protocol requires `secretsSource=API_DELTA` or `PERSONAL` for incoming
personal counts. Untagged reports from older clients (which may contain the party
total) are not trusted. Only one's own measured/API count is published, never a
forwarded report. The companion sync server must be updated to retain this field;
room/door synchronization remains compatible. Negative/missing personal counts
stay unknown through the server. See [0.3.1 evidence](RUN_STATISTICS_FIXES.md).

Global Mimic/Prince/Bat bonus evidence does not identify a contributor. Append P/M/B
to a player only for explicit named claims or an observed Mimic damage-source player.
Exact server Prince/Bat bonus lines and recognized party assists may set global
flags; arbitrary bat despawns may not. Outgoing Kung messages keep the `[Kung]`
prefix; configurable announcement text stores the body only.

`Dungeon > Extra Score Messages` controls Kung's Mimic, Prince and Bat kill
announcements with a master switch and independent per-bonus switches. The master
defaults off, including when loading older configs; the three subsettings default
on. Disabling announcements does not suppress bonus tracking, score calculation
or contributor attribution. Recognized party reports still do not trigger echoes.
The feature requests the shared statistics workload even with the map disabled,
without enabling room scans. Settings are persisted in `DungeonConfig`.

### Prince report punctuation — 2026-09-17

`kung-trace-20260917-221608.log` records
`Party > [VIP] gemothic: Prince Killed` at 22:11:02.054. The old pattern required
an exclamation mark after `Killed`, leaving `prince=false`. At boss entry the
estimate was 299: skill 100, exploration 91 (60 rooms + 31 secrets), speed 100,
bonus 8 (five crypts, Mimic and Bat). The 45/57 secrets and 78.9% server value
were consistent; no rounding correction is needed.

`Killed` now accepts an optional `!` for Prince, Mimic and the existing Bat/
BatScore/Bat Score aliases, as `dead` already did. Reports remain idempotent and
do not trigger an outgoing announcement. A regression using the captured inputs
reproduces 299 before the Prince report and 300 after it; the planned secret
target changes from 46 to 45, leaving zero required secrets. The final server
Team Score of 302 still overrides the estimate. Its other two points are not
explained by this punctuation fix and must not be invented in the forecast.

Focused score/message tests pass; verify the accepted report and map footer in
the next live run. This change adds no scans or new chat output.

## Splits

- `DungeonSplitTracker` transitions are ordered and idempotent. Late boss messages
  cannot rewind progress. Missing boundaries leave segment times unknown while
  preserving totals. Missing floor metadata cannot downgrade an established M7 run.
- M7 continues through Relics, Wither King dialogue and Dragons. Final boss dialogue
  ends combat; Total runs until the completion banner. There is no Victory phase.
  Wipe/abort freezes the unfinished measurement without pretending it completed.
- While Splits Overlay is enabled, each accurately completed phase contributes its
  real elapsed duration to a personal best. Values live in
  `config/kung/kung.json` under `splitsOverlay.personalBests`, as milliseconds keyed
  by `Entrance`, `F1`–`F7` or `M1`–`M7`, then the existing phase name (for example,
  `F6` → `Blood Open`). These are independent phase records across runs, not the
  phases of a single fastest run. Normal/master floors never share records.
  The existing game-profile config owns these values; there is no bundled PB data.
- `/kung splits` opens **Split Personal Bests**, with Entrance/F1–F7 and M1–M7
  selectors and that floor's canonical phase list. Each row shows the saved PB
  (`--` when absent), a time field, **Save**, and **Clear**. Input uses `mm:ss.mmm`
  (for example `01:23.456`); minutes may exceed two digits. Typing/paste rejects
  letters and misplaced separators; Save requires complete digits, seconds 00–59,
  a positive duration and no millisecond overflow. Save can replace faster or slower
  times; Clear deletes the phase entry and removes an empty floor bucket.
  Each action writes immediately through the existing config, even with the overlay
  disabled. Esc simply closes; there is no final save step. Unsaved field text is
  discarded on floor change/close. Tab switches fields and Enter saves the focused row.
  Editing the current run's floor refreshes both prediction caches and discards an
  already buffered sample for that phase, including a pending final-phase PB awaiting
  victory. Frozen run clocks/results stay unchanged; newly completed phases and future
  runs can still learn PBs normally.
- Each enabled, accurately completed phase also posts a local Kung system message,
  for example `[Kung Splits] F6 Blood Open: 31.00s (PB: 30.00s)`. A first PB or strictly faster
  phase adds a separate message with bold pink `PERSONAL BEST!` and green result text, for example
  `[Kung Splits] PERSONAL BEST! F6 Blood Open: 29.00s (Previous PB: 30.00s)`.
  The ordinary result shows the best including the just-completed phase; the
  celebration preserves the previous best from before the update. A first record
  uses `Previous PB: --`, and an unknown floor uses `PB: --`.
  Equal/slower times only post the ordinary result. Comparison uses that phase's
  PB for the floor/mode known at the boundary, before merging the new candidate.
  An unknown floor may report measured time but cannot claim a floor-specific PB.
  Minutes/Seconds formatting applies, and Time Prediction off does not mute the
  messages. The Splits master off does; manual, interrupted, unknown-duration and
  zero-duration phases stay silent. Messages never go to party/server chat.
  Final boss dialogue reports its completed phase once. If Team Score arrives
  first without a confirmed boss death, both messages wait for the matching victory
  banner and use the frozen phase duration. Repeated score/victory/boss messages,
  stop and reset cannot repeat notifications. PB storage keeps its batched run-end
  behavior and its final-floor correction; notifications do not add disk writes.
- PB candidates are collected at phase boundaries, then committed in a batched config
  save at finish, transfer/abort or reset, and only for improvements. Waiting until
  then allows late M7 metadata to classify the early phases correctly. Completed
  phases from an aborted run still count; interrupted, skipped/unknown and zero-time
  phases do not. Unknown floors and runs changed with manual debug splits cannot
  write records. A score-only banner cannot prove the final boss phase completed;
  that PB requires a boss-death boundary or the `Defeated ... in ...` banner.
  The server may send Team Score before its victory text. The score freezes both
  clocks immediately and retains a pending final-phase sample for up to five seconds.
  A matching floor/boss victory banner in that window confirms and saves that sample
  once, even though the timer has stopped. It does not append a phase or include
  the inter-message delay. Reset/new countdown discards the pending sample; the shared
  instance router rejects messages outside the dungeon. Repeats, unrelated bosses,
  player quotes, expired confirmations, manual runs and interrupted phases cannot
  create that final PB. Tracking eligibility is captured at the score boundary.
- Splits Overlay > **Time Prediction** is a toggle with expandable children, using
  the same control as Dungeon Chat Filter > Boss Messages. It defaults on to retain
  the existing prediction row; the Splits master toggle stays off by default.
  Turning prediction off removes the `Predicted` row below Total (before Time Lost),
  including its preview/editor height, while PB collection continues. The child
  **Update** switches between **Phase End** (default) and **Live**. Both settings
  persist in the existing Splits config; missing/unknown modes fall back to Phase End.
- Phase End starts with the sum of that floor's PBs, then uses **real elapsed time
  at the last boundary + PBs of the active and later phases**. It stays fixed within
  a phase, even if that phase exceeds its PB. Live uses **current real Run time +
  PBs of only the later phases**, excluding the active phase. For example, during
  Storm in F7: current Total + Terminals PB + Goldor PB + Necron PB; M7 also adds
  Relics, Wither King and Dragons. Live advances with Total, including banner wait,
  using the same clock sample as the displayed Total. Future PB sums are cached at
  boundaries/floor changes/manual PB edits; switching modes takes effect immediately mid-phase.
- Late floor metadata refreshes both forecasts. Skipped spans are already included
  in elapsed time and never counted twice. Missing required PBs or unknown floors
  show `--`; passed phases do not require PBs, and Live does not require the active
  phase's PB. No tick-time or TPS adjustment is applied. Phase End freezes during
  the boss/banner wait; confirmed completion freezes both modes to actual Total.
  An unfinished stop shows `--`. Minutes/Seconds formatting and `/kung hud`
  position/scale apply to the row, including its preview and editor bounds.
- The Hypixel tick source is **standalone non-zero ping packets**. Additional
  pings inside `ClientboundBundlePacket` are synchronization traffic, not more
  game ticks. Ignore their IDs before deduplication, just like zero; they cannot
  reset the standalone baseline or consume a later standalone ID.
- `ServerTpsTracker` classifies the original network envelopes. Dungeon tick
  events retain `handlePing` TAIL ordering alongside chat boundaries; a scoped
  `ClientPacketListenerMixin.handleBundlePacket` wrapper preserves child-packet
  provenance. `ServerTickPacketContext` restores that scope on return, nested
  calls and exceptions, including vanilla's thread-enqueue exception.
- Several standalone packets delivered at the same instant remain several ticks.
  A delayed network batch is different from a Minecraft protocol bundle. Both
  receipt-time TPS and applied split ticks use the same `ServerTickSequence`
  filter; ignore zero/extra bundle pings without inferring ticks from ID gaps.
- Ideal duration conserves **50 ms per accepted standalone tick**. Phase durations
  subtract cumulative tick snapshots in applied packet/chat order. Do not clip
  ticks to real time: receipt jitter and bursts otherwise discard valid progress.
  A short phase can receive more nominal tick time than its real duration; retain
  that measurement without moving ticks across boundaries or inventing a time offset.
  Total loss is `max(0, total real time - total accepted ticks * 50 ms)`, not the
  sum of positive phase losses. Later delayed ticks may reduce the loss at the
  next settlement, but completed phase snapshots never change.
  The `Time Lost` label uses the same red as its measured value.
  `currentTimings()` samples both clocks together without changing either clock.
- Start-room tick evidence survives countdown within the prepared instance, so
  an immediate full stall yields zero ideal progress. With no observed stream,
  ideal time remains unknown; instance reset clears that evidence.
- Current TPS uses a two-second arrival window ending **now**, including silence,
  so a complete stall reaches 0 TPS without a new packet. Average and historical
  samples still describe the retained packet intervals. Split ideal time counts
  accepted ticks directly; it does not integrate this smoothed TPS value.
- Phase/end traces identify `tickSource=standalone-ping`, accepted `receivedTicks`,
  `ignoredBundledPings` (including zero bundle pings), duplicate/zero standalone
  counts and last IDs from each source. `phaseTicks`, `phaseStartTicks`, `totalTicks`
  and the exact `boundary` message identify applied accepted progress; network
  totals can be slightly ahead of the client at a boundary. Countdown and Mort's
  map message get separate `start-candidate` records. Kung starts at the countdown;
  logging Mort's message does not restart or shift either clock.
  Full saved traces retain the last 128 split records separately from the
  general 2,500-entry ring, merged in original order without duplicates.
- Red loss values update at settled phase boundaries, then freeze at run end.
  Never recompute the displayed loss from the active phase each frame. Omit unknown
  or rounding-zero suffixes. Format selects Minutes/Seconds; Time Lost controls
  both suffixes and the extra row, including reserved editor bounds.
- Show the live overlay only after the timer starts; retain frozen results until
  instance exit. Share the map's HUD layer so inventory backgrounds dim it.
  `hasCurrentSplit()` selects the active highlight; `running()` includes banner wait.
  Chat filtering must forward boss messages to the tracker before suppressing them.

The ideal clock remains a packet-based estimate; network/client delay at a boundary
cannot be separated perfectly from server TPS loss. See
[SPLITS_TIMING_FIXES.md](SPLITS_TIMING_FIXES.md) for evidence and clock regressions.

PB/prediction validation (2026-09-14): regression coverage includes actual config
save/reload, legacy/null values, floor/mode isolation, strict improvements,
phase-time/PERSONAL BEST messages with current/previous PB snapshots, duplicate notification suppression,
both prediction modes and mid-phase switching, the F7/M7 Storm example, hidden-row
editor bounds, learning with prediction off, missing PBs, skipped boundaries,
completed versus aborted phases, manual/disabled runs, late M7 metadata,
boss/banner timing and overflow. The full active-module Java 25 build passes
all 537 tests with no failures/errors/skips. Live F6/M7 runs, restart restoration, menu
toggle/expansion/mode clicks, local phase/PB chat and HUD readability remain open.

### Score-before-victory PB correction — 2026-09-15

The supplied friend's `message.txt` retains a completed F1 run starting at
15:01:42.905. At 15:04:03.604, `Team Score: 177 (B)` freezes Total at 140,699 ms
and records Bonzo Phase 2 at 13,736 ms. At 15:04:03.607, the server sends
`☠ Defeated Bonzo in 02m 19s`. The earlier implementation removed the last PB
candidate on the score and ignored all later messages because `running` was false.
This could leave the final PB missing after every successful run, blocking both
forecasts in earlier phases. Live starts showing Total in the last phase because
there are no later PBs left to require.

The unchanged messages and countdown-relative timings are retained in
[`splits-score-before-victory-2026-09-15.tsv`](../versions/mc26_1_2/src/test/resources/dungeon/splits-score-before-victory-2026-09-15.tsv).
`DungeonSplitCompletionOrderTest` reproduces the failure before the fix
(`expected 13736, got -1`) and now confirms the PB and both prediction modes on
the next run after config serialization/reload. It also checks frozen clocks,
duplicate saves, reward metadata, wipes, reset, tracking toggles and matching bosses.
All 103 focused split/lifecycle/config/trace tests and the full 530-test Java 25 build pass.

Run-start/end diagnostics now include `pbFloor`, `pbTracking`, `predictionMode`,
`pbKnown` and `pbMissing`; `run-victory-confirmed` records the delayed confirmation.
Existing phase/tick trace fields are unchanged. The supplied trace has no saved
config/PB inventory, so it cannot establish which other records the friend already
had. Existing missing PBs need a newly completed matching-floor run with the fixed
build; no old timings or other players' records are imported. First-ever runs still
need measurements before a forecast can exist. The next live successful F1 run,
its following run's early prediction and restart restoration remain unverified.
