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
over later ordinary Score fields. Only puzzle states `x`, `X`, `✖` indicate failure,
not `✦`. Footer secrets mean **found - remaining for 300 - max**, not target total.
Use `score-calc` traces for source selection, target, puzzle failures and bonuses.

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

## Splits

- `DungeonSplitTracker` transitions are ordered and idempotent. Late boss messages
  cannot rewind progress. Missing boundaries leave segment times unknown while
  preserving totals. Missing floor metadata cannot downgrade an established M7 run.
- M7 continues through Relics, Wither King dialogue and Dragons. Final boss dialogue
  ends combat; Total runs until the completion banner. There is no Victory phase.
  Wipe/abort freezes the unfinished measurement without pretending it completed.
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
