# Run statistics and splits

Current contracts as of 2026-09-18. Source and regression entry points:
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

### Death/revive evidence — 2026-09-18

The supplied trace created at 13:39:58 UTC (15:39:58 local) ends at 17 counted
deaths. Its 2,500-entry ring and message rate limit omit some input lines. The
matching `Dungeons 26.1.2/logs/latest.log` supplies a separate anchored ghost
message for every increment: 15 death messages and two disconnect-to-ghost
messages. Another death at 15:39:58, after the clipboard capture, brings the run
to 18 (Beng114 3, _FabledHyperion_ 5, FLC_x1 4, Frostelle 2, Loveisla 4).
Revive starts, player/fairy revives, Revive Stone notices and reconnections do
not increment the counter; a preceding death still does. The real message replay
in `DungeonStatisticsSourcesTest` checks every transition and all five totals.
The production counter is unchanged. Neither log provides an authoritative
`Team Deaths: 12` field, so the reported discrepancy with the server's death total
remains unconfirmed; it needs the exact server counter at the same moment.

### Score authority

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

When Skyblocker is installed, an optional event hook also accepts its confirmed
Mimic/Prince/Bat kill callbacks, including validated Secret Sync reports that never
pass through chat. The bridge requires Kung's active, started dungeon and statistics
workload. It sets the existing per-run flags without guessing a contributor or
echoing party announcements. It does not poll another mod's retained state or open
a new network connection; Skyblocker remains optional.

### Four-point bonus gap — 2026-09-19, 12:33 trace

`kung-trace-20260919-123326.log` and the supplied result-map screenshot isolate
the discrepancy to Bonus. Kung has 46/50 secrets (92%), full room/puzzle credit,
one death and eight crypts: Skill 99 + Explorer 96 + Speed 100 + Bonus 6 = 301.
The server reports 305 with the same first three components and Bonus 10; final
Team Score correctly overrides the estimate. Kung has Bat, but no Mimic or Prince.
Even enabling both missing flags would yield Bonus 9 and total 304, so those
flags alone cannot explain all four points. Secret rounding and death penalties
agree with the server breakdown and remain unchanged.

The matching Minecraft log records an accepted Skyblocker Bat sync and party
report at 12:27:34, followed by the server Bat bonus line at 12:27:44. These
overlapping reports do not establish separate bonus awards. No accepted Mimic
or Prince report is present. Rejected sync messages from foreign UUIDs do not
prove that a teammate's valid report was lost. The installed Kung 0.4.2 JAR
already contains the optional Skyblocker bridge; a missing installation of that
bridge is not the explanation for this run.

To preserve the next run's inputs, traces retain a separate bounded history of
256 `score-calc`, `score-bonus` and `mimic-kill` records alongside the existing
128 split records. Recognized bonus chat inputs are logged even when a previous
sync/chat report already set the flag. This changes diagnostic retention only;
it adds no score points, scans or outgoing messages. Regressions replay
the captured 301/final-305 transition without guessed bonuses and check overlap
logging and retention under trace-ring churn. Both new diagnostic regressions
fail before the change and pass afterward. Focused tests and the full Java 25
build pass: 736 cases, 735 passed, one Windows symlink skip, no failures/errors.
`git diff --check` passes. The existing 0.4.2 version is rebuilt, not installed.

The cause of the four missing bonus points remains unresolved. A further live
run with retained bonus inputs and its server component breakdown is required;
this investigation does not claim a score-formula fix.

### Missing synchronized bonuses — 2026-09-19, 00:02 result

The user's `kung-trace-20260918-235726.log` and
`kung-trace-20260919-000225.log` cover one M7 run. At 23:57:06 Kung has
43/52 secrets, full projected room credit, three crypts, Bat, no Mimic and no
Prince: 100 Skill + 93 Explorer + 100 Speed + 4 Bonus = 297. Noamm announces
300 at the same second. The first actual death at 23:57:28 lowers Kung to 296;
five total deaths eventually yield 91 + 93 + 100 + 4 = 288. The server result
and supplied score-map screenshot agree on 91 + 93 + 100 + 7 = 291.

Archived Minecraft logs `2026-09-18-6.log.gz` and `2026-09-19-1.log.gz`
provide the missing input: Skyblocker Secret Sync logs Mimic at 23:55:48 and
Prince at 23:55:58, with no corresponding chat reports. Those are exactly the
missing three bonus points. The installed Skyblocker 6.10.4+26.1.2 bytecode
checks the sync sender before calling `DungeonScore.onMimicKill()` /
`onPrinceKill(false)` on the render thread. The optional mixin observes these
accepted callbacks and the equivalent Bat callback; changed/missing optional
methods do not prevent Kung from loading.

The captured-count replay now gives 300 before deaths and 291 afterward, without
waiting for final Team Score. Regressions cover duplicate/chat overlap, silent
receipt with announcements enabled, absent contributor attribution and countdown
reset. No score formula, crypt count or death penalty changes. Live callback
delivery with Skyblocker remains to verify after restarting with the rebuilt JAR.

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

### Two-point score gap — 2026-09-18, 16:40 run

`kung-trace-20260918-164256.log` records 45/57 secrets, 78.9%, no deaths,
full projected room/puzzle credit, nine crypts, Prince and Bat at 16:42:19.
Kung calculates 298: skill 100 + explore 91 + speed 100 + bonus 7. Noamm's
300 announcement appears at 16:42:22 in the matching Minecraft log.
At 16:42:23 Kung has 46/57 secrets, 80.7% and score 299; a later death lowers
that estimate to 298. The server reports Team Score 300 at 16:47:18, and Kung's
summary correctly adopts it.

The missing Mimic flag would account for exactly two points, but neither a
Mimic death observation nor a kill/charm chat report appears in the available
run evidence, and the user cannot confirm the kill. This is a missing-input
hypothesis, not a proved detection bug. A different mod's total does not establish
a Mimic kill. The formula and evidence requirements remain unchanged.
`DungeonExtraScoreMessagesTest` replays the observed 298/299/298 sequence and
the final server override without inventing Mimic evidence.

### One-point score gap — 2026-09-18, 22:12 result

`kung-trace-20260918-221219.log` records 47/59 secrets (79.7%), full projected
room/puzzle credit, five crypts, Mimic, Prince and Bat. Kung estimates 300 before
the actual `RedTurtle4000 died and became a ghost` message at 22:09:30.957.
After that death, the estimate is 299: skill 99 (80 room credit, one death point,
no puzzle penalty) + explore 91 (60 rooms, 31 secrets) + speed 100 + bonus 9.
The server reports Team Score 300 at 22:12:15.638; Kung correctly adopts it,
while the unchanged estimated components still total 299. This run does not
have the missing Mimic evidence seen in the earlier investigation.

The matching Minecraft log and saved Team Score chat component contain only
the total. The EXTRA STATS component is a `/showextrastats` link with a generic
hover, not the score breakdown. Skill, Explorer, Speed and Bonus were requested
from the user and remain unavailable for this particular run. The subsequent
22:18 screenshots concern another run, with no deaths and a missing Bonus point.
Rounding the secret score up would add
one point here, but that alone does not establish the cause; the inspected
Noamm 26.1.2, Skyblocker and Skytils implementations also truncate secret score.
No score formula, death rule or bonus flag was changed based on this mismatch.

### Missing bonus point — 2026-09-18, 22:18 result

`kung-trace-20260918-221900.log` and the supplied score-board screenshot isolate
this run's mismatch: server Skill 100 + Explorer 91 + Speed 100 + Bonus 9 = 300;
Kung's identical first three components plus Bonus 8 = 299. It has 41/52 secrets,
78.8%, no deaths, full room/puzzle credit, five crypts, Mimic and Bat, but
`prince=false`. The secret target consequently stays at 42 instead of 41.
Neither the trace nor the matching Minecraft log contains a Prince bonus/party
report for this run. This establishes the missing bonus input, not why it was
unavailable; it does not resolve the preceding run's different Skill discrepancy.

The message router previously used Fabric's post-filter `GAME` / `CHAT` events;
only Kung's own filter forwarded hidden messages. It now observes the original
message once via `ALLOW_GAME` / `ALLOW_CHAT`, including messages another listener
cancels or rewrites. The old forwarding path is removed to prevent double delivery.
New `score-bonus` trace events retain the actual input when Prince/Bat first gain
credit, including hidden inputs. A routing regression replays the captured 299
inputs, then a **synthetic** canceled Prince message: score becomes 300 and the
secret target becomes 41; actionbar text cannot award it and repeated reports do
not add another point. The same test checks a hidden Mort start message.

No score formula or assumed Prince credit was added. Display filtering is a
confirmed routing gap, but the old trace cannot prove it caused this particular
missing message. A bonus only received by a teammate still needs their report;
the client cannot infer it from a Prince room or an ordinary entity death.

## Splits

- `DungeonSplitTracker` transitions are ordered and idempotent. Late boss messages
  cannot rewind progress. Missing boundaries leave segment times unknown while
  preserving totals. Missing floor metadata cannot downgrade an established M7 run.
- Countdown prepares the live timer. Mort's exact map greeting rebases real and
  accepted-tick time once before Blood Open completes, excluding the pre-start
  countdown. Missing greetings retain the countdown fallback; late/duplicate
  greetings and manual runs cannot reset timings. Existing PB/AVG history stays
  stored; newly recorded Blood Open samples use the corrected origin.
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
  The header also shows **AVG: N/20 runs** for the selected floor and a **Reset AVG**
  button. Reset immediately deletes that floor/mode's saved run history, preserves
  PBs and other floors, and invalidates a running forecast. New completed runs can
  collect samples again. This action works with the overlay disabled.
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
  This also applies when the boss identifies the floor number but normal/master
  mode is still unknown. Common boss dialogue selects phases only; it cannot
  assume F7 from Maxor/Storm/Goldor/Necron or read F7 prediction baselines. Exact
  shared floor metadata, structured SYSTEM floor fields, or M7-specific Wither
  King dialogue confirm the record bucket. Earlier measured candidates remain
  available for that later confirmation.
  Minutes/Seconds formatting applies, and Time Prediction off does not mute the
  messages. The Splits master off does; manual, interrupted, unknown-duration and
  zero-duration phases stay silent. Messages never go to party/server chat.
  Final boss dialogue reports its completed phase once. If Team Score arrives
  first without a confirmed boss death, both messages wait for the matching victory
  banner and use the frozen phase duration. Repeated score/victory/boss messages,
  stop and reset cannot repeat notifications. PB storage keeps its batched run-end
  behavior and its final-floor correction; notifications do not add disk writes.
- Splits Overlay > **Run End Chat** defaults off and requires the Splits master
  toggle. At the first completion/Team Score message it posts one local summary:
  floor/mode, every canonical phase in order, Boss Entry (cumulative Portal Entry),
  Total, and Time Lost when enabled. Rows use the selected Minutes/Seconds format
  and show server time in parentheses. Phase labels share the overlay palette,
  measured real times are light, server times are muted, and losses are red.
  With Time Lost enabled, each phase, Boss Entry and Total appends its own
  `max(0, real - server)` loss using the overlay's tenth-second rounding; unknown
  and rounding-zero suffixes stay hidden. Explicit row styles bypass generic chat
  number highlighting while retaining the shared Kung prefix.
  Missing measurements remain `--`; a phase
  interrupted by a wipe is marked `unfinished`. A missing boss-entry boundary
  stays unknown. The summary snapshots the frozen times and does not announce PBs.
  Repeated completion messages and score-before-victory confirmation cannot print
  it again. Transfers/abort without an end signal, reset and manual debug runs
  stay silent. Time Prediction and player-summary settings do not gate this output;
  nothing is sent to party/server chat.
- PB candidates are collected at phase boundaries, then committed in a batched config
  save at finish, transfer/abort or reset, and only for improvements. Waiting until
  then allows late M7 metadata to classify the early phases correctly. Completed
  phases from an aborted run still count; interrupted, skipped/unknown and zero-time
  phases do not. Unknown floors/modes and runs changed with manual debug splits cannot
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
  including its preview/editor height, while PB and AVG collection continues.
  **Update** switches between **Phase End** (default) and **Live**; **Source** selects
  **PB** (default) or **AVG** independently. All settings persist in the existing
  Splits config; missing/unknown values fall back to Phase End and PB.
- AVG stores up to the **last 20 confirmed finished runs per floor/mode** under
  `splitsOverlay.recentRuns`, oldest first. Each run contains that run's accurately
  measured, positive real phase milliseconds collected with Splits enabled.
  Each phase's baseline is its arithmetic mean across those runs, rounded to the
  nearest millisecond. Missing/skipped/zero phases are omitted from that phase's
  denominator, never treated as zero. Eviction removes the oldest whole run.
  There is no PB fallback or cross-floor sharing when an average is missing.
  Collection works in PB mode and with Time Prediction hidden. PB edits do not
  alter measured AVG samples. Aborts, score-only wipes, unknown floors and manual
  debug runs cannot enter history. A score-before-victory run is added once on the
  matching confirmation, using the frozen score-boundary sample. Boss-death runs
  wait for the completion banner. Late M7 detection uses the final floor bucket.
  PB improvements and run samples share one save at a confirmed finish; a slower
  completed run still updates AVG even when no PB improves.
- Phase End starts with the sum of that floor's selected baselines, then uses
  **real elapsed time at the last boundary + baselines of the active and later
  phases**. It stays fixed within a phase, even if that phase exceeds its baseline.
  Live uses **current real Run time + baselines of only the later phases**,
  excluding the active phase. For example, during Storm in F7: current Total +
  Terminals baseline + Goldor baseline + Necron baseline; M7 also adds
  Relics, Wither King and Dragons. Live advances with Total, including banner wait,
  using the same clock sample as the displayed Total. Future sums are cached at
  boundaries/floor changes and invalidated on source/data changes; AVG does not scan
  history every frame. Switching Update or Source takes effect immediately mid-phase.
- Late floor metadata refreshes both forecasts. Skipped spans are already included
  in elapsed time and never counted twice. Missing required baselines or unknown floors
  show `--`; passed phases do not require baselines, and Live does not require the active
  phase's baseline. No tick-time or TPS adjustment is applied. Phase End freezes during
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
  map message get separate `start-candidate` records. The accepted Mort greeting
  adds `timer-start` and rebases both clocks before the first completed phase.
  Full saved traces retain the last 128 split records separately from the
  general 2,500-entry ring, merged in original order without duplicates.
- Red loss values update at settled phase boundaries, then freeze at run end.
  Never recompute the displayed loss from the active phase each frame. Omit unknown
  or rounding-zero suffixes. Format selects Minutes/Seconds; Time Lost controls
  both suffixes and the extra row, including reserved editor bounds.
- Show the live overlay only after the timer starts; retain frozen results until
  instance exit. Share the map's HUD layer so inventory backgrounds dim it.
  `hasCurrentSplit()` selects the active highlight; `running()` includes banner wait.
  The shared pre-filter message observer receives boss dialogue even when hidden.

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

PB/AVG validation (2026-09-18): seven new regressions cover persisted whole-run
eviction at 20, per-phase means with missing samples, floor/mode isolation, legacy
config defaults, immediate reset/source switching in both update modes, disabled
prediction learning, excluded runs and late M7 completion. Extended completion-order
tests cover single insertion after score-before-victory, duplicate/expired banners
and tracking eligibility. The focused suite and full Java 25 build pass: 708 cases,
707 passed, one Windows symlink skip, no failures/errors. The JAR is built, not
installed; live Source clicks, run count/reset and next-run HUD remain unchecked.

### Missing mode after a failed M7 warp — 2026-09-19, 16:47:40 trace

The supplied trace and matching profile `latest.log` show an M7 entry at
16:42:29, a failed transfer/read timeout, return to Prototype Lobby, and a summon
into the dungeon at 16:44:10 without a new entry banner. The old entry hint had
expired; retaining it across those unrelated transfers would be unsafe. Clear
split messages had no floor. At Maxor's dialogue, both run statistics and the
split tracker inferred floor 7 but incorrectly treated the default
`masterMode=false` as evidence for F7. Incorrect F7 PB announcements continue
from Portal Entry at 16:46:40 through Necron at 16:49:58.

Wither King dialogue corrects the mode, with an M7 Relics notice at 16:50:08 and
the correct M7 summary at 16:51:08. The inspected profile contains this run's
exact measurements under M7, only F1/M7 PB buckets, and no F7 PB or AVG bucket.
Every phase is slower than its stored M7 PB. No profile repair was needed or
performed; deferred final-mode persistence already prevented contamination here.

Splits now distinguish the inferred phase layout from a confirmed floor/mode.
Generic run-statistics floor inference no longer becomes PB metadata at ticks,
messages or countdown. Exact shared metadata and SYSTEM floor headers remain
accepted. Without mode confirmation, timing continues with `PB: --`, no F7/M7
label, no borrowed predictions and no PB/AVG save on abort. Later M7 confirmation
keeps all accurate preceding measurements and saves only to M7. Two regressions
failed before the correction and pass afterward, covering the supplied timing
prefix, unknown-mode aborts and late Wither King confirmation through a complete
run. The countdown lifecycle regression also rejects inferred mode.

Separately, entry metadata now observes original non-overlay `ALLOW_GAME` text
before display formatting/filtering. Its regression fails on the old post-filter
`GAME` route. This closes another reproducible entry-loss path; it does not
explain or bypass the stale failed-warp hint in this particular run. The 30-second
limit and packet-confirmed instance gate are unchanged. Live rejoin classification
and phase messages remain to verify with the rebuilt JAR.

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
