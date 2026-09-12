# Dungeon split timing: September 12, 2026

## 0.2.19 live check: 18:16:58 trace

The follow-up `kung-trace-20260912-181658.log` and combined screenshot confirm
clock conservation in live play. The trace retains both completed runs, including
their countdown/Mort markers and all eleven phase boundaries, despite the normal
ring starting after the latest Blood Open boundary. Its 2,521 merged entries
include 21 older retained split records.

| Completion | Real ms | Accepted ticks | Ideal ms | Loss ms |
| --- | ---: | ---: | ---: | ---: |
| 18:09:26.798 | 356,953 | 6,852 | 342,600 | 14,353 |
| 18:16:55.550 (screenshot) | 357,491 | 6,864 | 343,200 | 14,291 |

For every phase in both runs, `serverMs = phaseTicks * 50` and
`totalServerMs = totalTicks * 50`. Each starting tick snapshot equals the preceding
endpoint; phase durations partition real time without gaps. No clipped tick time
or broken phase partition was found. The screenshot run's 381 ms completion wait
contains another six ticks (300 ms), contributing 81 ms to final loss. Its final
14,291 ms loss rounds to Kung's displayed 14.3 s; the comparison shows whole seconds
for total loss, so it does not establish equality at millisecond precision.

The latest countdown occurs at 18:10:58.059; Mort's map message follows with
`wallMs=1148 totalTicks=20`. The distinct start signals therefore differ by
1,148 ms real and 1,000 ms ideal in this run. This supports the earlier start-origin
explanation for the roughly one-second total/Blood Open discrepancy without
applying an offset to Kung. The screenshot still contains individual one-tick
differences (e.g. Terminals and Goldor) between displays; the aggregates do not
establish a Kung packet-order defect or justify moving those ticks.

This check validates the recorded clocks and retention in these runs, not every
possible server/network failure. No code or version change was needed. Validation
for this documentation update: trace arithmetic/phase partition checks and
`git diff --check`; no new build was run.

## 0.2.19: valid ticks discarded by arrival-time caps

Source: `kung-trace-20260912-171838.log` from Kung 0.2.18 and its two comparison
screenshots. The full general ring contains 2,500 entries starting around 17:11:09;
run start and Blood Open's boundary have already been evicted. No separate
boss-entry trace was supplied/found for this run. The comparison is supporting
evidence, not the definition of correct clocks.

The trace independently establishes the remaining clock error:

- Real run time: **473,901 ms**, unchanged by this fix.
- Applied standalone ticks: **9,200**, hence **460,000 ms** of ideal 20-TPS time.
- 0.2.18 credited only **458,341 ms**, losing **1,659 ms** of accepted progress.
- Corrected loss on Kung's existing boundaries: **13,901 ms**, previously 15,560 ms.
  Noamm displays 13,880 ms; that remaining 21 ms alone is not evidence of a defect.

The 0.2.17 per-tick cap survived the 0.2.18 packet-source correction. With normal
arrival jitter it truncated fractions of legitimate ticks whenever nominal tick
time briefly led real time. It also threw away delayed ticks delivered around
short dialogue phases. Once discarded, those fractions never returned. Removing
invalid packet sources and conserving valid ticks is sufficient; no constant
offset, dialogue subtraction or inferred redistribution across phases is used.
This supersedes the wall-time caps described in the historical sections below.

### Phase comparison

Seconds below use Kung's full trace precision and Noamm's displayed precision.
The accepted-tick column is computed solely from Kung's counters. Blood Open is
derived by subtracting Blood Clear from the first retained cumulative boundary.

| Phase | Kung real | Noamm real | Kung 0.2.18 ideal | Accepted ticks × 0.05 s | Noamm ideal |
| --- | ---: | ---: | ---: | ---: | ---: |
| Blood Open | 37.846 | 36.85 | 37.385 | 37.450 | 36.60 |
| Blood Clear | 77.312 | 77.25 | 74.510 | 74.600 | 74.45 |
| Portal Entry | 98.543 | 98.51 | 97.514 | 97.550 | 97.55 |
| Boss Entry (cumulative) | 213.701 | 212.61 | 209.409 | 209.600 | 208.60 |
| Maxor | 25.966 | 25.96 | 25.718 | 25.750 | 25.75 |
| Storm | 50.868 | 50.87 | 49.732 | 49.750 | 49.75 |
| Terminals | 57.732 | 57.73 | 57.208 | 57.500 | 57.50 |
| Goldor | 8.763 | 8.75 | 8.691 | 8.750 | 8.75 |
| Necron | 35.810 | 35.81 | 33.333 | 33.350 | 33.35 |
| Relics | 13.962 | — | 11.564 | 11.600 | — |
| Wither King | 5.827 | — | 5.504 | 6.200 | — |
| Dragons (Kung segment) | 60.764 | — | 56.978 | 57.250 | — |
| Completion wait | 0.508 | — | 0.204 | 0.250 | — |
| Necron end → run end (Noamm's Dragons span) | 81.061 | 81.14 | 74.250 | 75.300 | 75.30 |
| Total | 473.901 | 472.98 | 458.341 | 460.000 | 459.10 |

Portal Entry through Necron now match the comparison's ideal values without
changing a boundary. Terminals lost 292 ms solely to clipping. The Wither King
span received 124 ticks in 5.827 s; 696 ms were clipped, including within-phase
arrival effects. Preserve the measured 6.200 s nominal tick span instead of
silently moving or deleting ticks. Its exact generation times cannot be recovered
from aggregate delivery diagnostics.

Kung's standalone Dragons row is not the same span as Noamm's. Relics, Wither
King, Dragons and the 508 ms completion wait together contain 1,506 ticks (75.300 s).
The old sum had lost 1.050 s; the real-span difference is only 79 ms. Combat end
is logged at 17:17:08.606, and Kung's completion signal at 17:17:09.115. These
are two separate boundaries and must not be conflated.

The installed `NoammAddons-1.2.6-26.1.2-legit.jar` was inspected read-only:
`DungeonListener` starts from Mort's message, `Here, I found this map when I first
entered the dungeon.` Kung's lifecycle starts at `Starting in 1 second.` Noamm
also consumes a Catacombs completion header. This establishes different signal
definitions, not their exact separation in this run. Kung keeps its existing
countdown origin; a blanket one-second subtraction would hide this distinction.
The missing early entries prevent explaining Blood Clear's remaining three-tick
ideal difference or attributing Blood Open's entire 996 ms real difference to a
specific pair of received messages. Their screenshots alone cannot do that.

### Clock, diagnostics and regression coverage

Each accepted standalone tick adds exactly 50 ms to the run clock. Phase snapshots
partition that counter in applied chat/packet order, even if ticks on both sides
of a boundary arrive at one instant. The source filter still rejects bundle/zero
pings before deduplication. No ticks are extrapolated during silence. Completed
phases stay frozen, and the loss display still updates only at phase/run end.
Total loss uses cumulative real minus ideal time, not the sum of clipped positive
phase losses; actual delayed progress may lower that total at a later settlement.

[The aggregate fixture](../versions/mc26_1_2/src/test/resources/dungeon/splits-171838.csv)
preserves all phase durations/counts and the completion wait. The regression uses
the retained dialogue boundaries with explicitly synthetic tick spacing, not a
claim to have recovered packet timestamps. Both the measured-run conservation
test and the healthy-jitter test failed against 0.2.18 before correction. A separate
packet test verifies ticks delivered before/after chat at the same timestamp stay
on their respective sides without changing a completed snapshot. Existing tests
retain complete stalls, 60 s at 10/20 TPS, source filtering and delayed batches.

New diagnostics include the exact `boundary` message and cumulative applied
`phaseStartTicks`/`totalTicks`, plus countdown/Mort `start-candidate` records with
real time and ticks since Kung's origin. Full saves merge a separate,
bounded history of 128 split events with the normal ring in sequence order.
Retention, deduplication, clearing and the history limit have regression coverage;
the existing noise/rate-limit tests remain. No per-tick logging or write path is added.

Validation: full Java 25 active-module build, **291 tests**, zero failures/errors
or skipped tests; `git diff --check` passes. Version metadata and changed classes were verified in
`versions/mc26_1_2/build/libs/kung-26.1.2-0.2.19.jar`. Live play remains unverified.

Next measurement: use 0.2.19, capture both overlays from the same completed run,
and immediately run `/kung log save`; supply that trace and both screenshots.
Do not clear the trace during the run. The reserved start/boundary records are
automatic, so extra chat debug and a separate boss-entry save are unnecessary
for this split investigation. The newly retained countdown/Mort timing and
Blood Open boundary are the specific evidence missing from the supplied trace.

## 0.2.18: protocol-bundle pings are additional traffic, not game ticks

Source: the supplied `kung-trace-20260912-164109.log` from Kung 0.2.17 and the
two matching screenshots. The trace establishes systematic overcounting, with
`duplicatePings=0`. The raw clock credited 506.350 s during 448.263 s of real run
time. For phases with both boundary counters retained, removing the additional
bundle pings reproduces the Noamm ideal times exactly in these three cases:

| Phase | Real ms | Old phase pings | Bundle counter delta | Standalone ideal ms | Loss ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Storm | 54,923 | 1,239 | 188 (835 -> 1,023) | 52,550 | 2,373 |
| Terminals | 64,569 | 1,327 | 122 (1,023 -> 1,145) | 60,250 | 4,319 |
| Goldor | 9,702 | 179 | 12 (1,145 -> 1,157) | 8,350 | 1,352 |

Ideal ms = `(old phase pings - bundle delta) * 50`. Other retained phases show
the same relationship, with occasional one-tick boundary differences between
received counters and applied chat. The first run/start boundary entries were
already evicted from the 2,500-entry trace ring. This is not a complete raw-packet
replay, and it cannot reconstruct the entire run's exact corrected result.

The earlier bundle expansion incorrectly treated every non-zero child ping as
a game tick. Consecutive-ID deduplication did not help because these were distinct
IDs. The 0.2.17 clock cap removed credit for *future* silence, but additional pings
arriving *after* slowdown still filled elapsed time and erased loss. Its tests
also incorrectly represented delayed standalone tick delivery as protocol bundles.
This source-classification finding supersedes the earlier bundle assumption below.

Both tick consumers now reject protocol-bundle pings before deduplication.
`ServerTpsTracker` sees the original network envelope. Applied dungeon ticks stay
at the common listener's `handlePing` TAIL; a `handleBundlePacket` wrapper carries
the envelope context through vanilla child dispatch and restores it in `finally`.
It neither cancels child packets nor queues replacement callbacks. Chat boundaries
therefore keep their original applied order. The context is local to each thread
and is restored after nested dispatch or vanilla's off-thread enqueue exception.
The shared Blood Rush tick consumer receives the same corrected tick stream.

Delayed delivery of multiple **standalone** pings still counts every distinct
tick, even at one timestamp. Clock caps, 50 ms ideal tick duration, unknown-stream
handling and loss settlement only at phase/run end remain unchanged.

The [small trace fixture](../versions/mc26_1_2/src/test/resources/dungeon/splits-164109.csv)
preserves the three measured phase aggregates. Tests use explicitly synthetic IDs
and spacing, replaying their standalone/additional-bundle composition through the
shared applied filter and real packet classes. The Storm receipt-time regression
fails on 0.2.17 before the fix. Coverage also includes 3 s without real ticks while
bundle traffic continues, 60 s at 10/20 TPS with extra pings and delayed deliveries,
chat inside a bundle, deduplication isolation, unknown bundle-only streams and
scope cleanup after nesting, exceptions and separate-thread processing.

Validation: **285 tests** pass in the full active-module build with Java 25,
including seven added tests. `git diff --check` passes. Version metadata and the
corrected filter/context/mixins were verified inside
`versions/mc26_1_2/build/libs/kung-26.1.2-0.2.18.jar`. The wrapper signature also
matches the bundled MixinExtras 0.5.4 API and Minecraft 26.1.2's bundle handler.
This is automated validation; loading the new mixin in live Hypixel play and the
next screenshot comparison remain open.

At phase/run boundaries, diagnostics now print `tickSource=standalone-ping`,
`receivedTicks`, `ignoredBundledPings`, standalone duplicate/zero counts and the last
IDs of each source. No per-packet logging or new runtime file paths are added.
For a live check, capture both overlays after the run and immediately use
`/kung log save`. If full clear-phase evidence is needed, also save once at boss
entry: the general trace ring can evict the start during a long run. Share both
saved paths and the screenshots from that same run; extra chat debug is unnecessary.

## 0.2.17: excess tick credit masking later stalls

The supplied Kung 0.2.16 screenshot shows about 0.9 s total loss, while the
Noamm screenshot shows about 11.3 s. No packet log was supplied. These images
establish the discrepancy, but cannot establish the exact packet sequence or
separate server slowdown from network/client delay in that run.

The demonstrated clock defect was the **read-time-only bound**:
`min(phase wall time, raw phase ticks * 50 ms)`. For example, 120 accepted pings
during three real seconds left six seconds of raw tick credit. After another
three seconds with no ticks, the displayed ideal clock grew to six seconds,
erasing the entire stall despite no additional server progress. Existing tests
covered excess across phase boundaries, but missed this case within one phase.

The tracker now grants at most 50 ms on each accepted tick and immediately caps
the phase's accumulated credit at its elapsed real time. Discarded excess remains
discarded. A three-second stall therefore adds three seconds of loss even after
an earlier excess burst. Genuine delayed ticks can still fill already elapsed
time in the active phase, without affecting settled boundaries. Reads never
advance ideal time. Raw ticks remain available for diagnostics; `boundedPhaseMs`
now reports all discarded phase credit, including excess discarded before silence.
Applied/network gap diagnostics include the open gap at the time of observation.

Tick-stream availability now preserves start-room evidence through countdown,
allowing a full stall at run start to measure zero ideal progress. A stream never
observed in the instance still means unknown, not proof of zero TPS. Reset clears
that evidence. The separate TPS sampler now includes the silent tail in a rolling
two-second current window; previously its last healthy sample persisted forever
without a new packet. Historical interval samples remain historical, and the
split clock does not integrate or extrapolate the smoothed TPS value.

Packet dispatch was checked against Minecraft 26.1.2 bytecode: `handlePing`
enforces the packet-processing thread and throws after enqueueing on other
threads. The existing TAIL hook therefore counts applied packets once, in the
same order as chat boundaries, including vanilla-unpacked bundles. The shared
zero filter, consecutive-ID deduplication and no-ID-gap-inference rules remain.
No change to packet dispatch, HUD layout or loss settlement was needed.

Regression coverage includes three-second complete stalls (also immediately
after countdown), excess before silence, 60 s at 10 TPS with delivery batches
of 1/10/100/600 ticks, 60 s at 20 TPS with a three-second delayed delivery,
resumption without fabricated ticks, settled-boundary protection, read-frequency
independence, stale TPS aging and real bundle traversal with repeated/zero pings.
Old wipe/overlay fixtures now timestamp simulated ticks as elapsed progress;
they no longer preload future tick credit at time zero.

Validation: nine added tests; all **278 tests** pass in the full Minecraft 26.1.2
build with Java 25. Three new split regressions failed against the previous
clock, and the new stale-TPS regression failed against the previous sampler
before correction. `git diff --check` is clean.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.17.jar`.

Live Hypixel validation remains open. Timing is quantized to 50 ms ticks, and a
client cannot perfectly distinguish delivery delay at a boundary from actual
server slowdown. The screenshot's particular 11.3 s cannot be reconstructed.

## 0.2.12: ideal time exceeding real time

The later screenshot shows Blood Open as `36.15s (42.00s)`. The 0.2.10/0.2.11
clock accepted ping ID `0` as a tick and as its deduplication baseline. Thus
`-10, 0, -10` counted three ticks instead of one. It also exposed raw tick count
times 50 ms directly, without bounding the ideal duration by the real duration.
The screenshot establishes the inflated duration; without a new packet trace it
does not establish the exact ID sequence in that run.

The corrected shared `ServerTickSequence` ignores zero before updating its last
real ID. This follows the non-zero filter in
[Odin's packet hook](https://github.com/odtheking/Odin/blob/331628623c59f7a7274e3985ae16f5411ae21123/src/main/java/com/odtheking/mixin/mixins/ConnectionMixin.java).
Both applied dungeon ticks (including Blood Rush) and network TPS sampling use
the corrected filter. `nonTickPings` is recorded separately from duplicate pings.

The split tracker now bounds each segment's ideal duration by its real duration.
Run totals are the sum of those bounded segments plus the current segment, rather
than one globally capped raw counter. Excess tick time cannot become credit for
the next phase, and a fast phase cannot cancel a previous phase's settled loss.
Ticks batched after a delay can still catch up within their current phase. Raw
tick totals and `boundedPhaseMs` remain in diagnostics. The HUD samples real and
ideal times together, avoiding mismatched instants at a rendering boundary.

Phase names, finish/wipe retention, settings and loss updates at phase boundaries
remain unchanged. The result remains a packet-based estimate: network/client
delays at a boundary cannot be identified as pure server TPS loss.

Validation: full Minecraft 26.1.2 build, 243 tests passing. Eight added regressions
cover zero-only packets, zero-separated duplicate ticks, healthy/slow applied
ticks, the reported 36.15/42.00 pair, excess before/after a slow phase, delayed
batches, and bounded wipe/restart clocks. Live Hypixel playback is not verified.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.12.jar`.

## Earlier dialogue report

The reported `-5.0s` around Maxor/Storm cannot be reconstructed from
`kung-trace-20260912-025947.log`: that trace contains boss messages but no split
clock totals or ping counts. It preserves Maxor's last dialogue at
`02:53:21.129`, Storm's phase-opening line at `02:53:23.090`, and Storm's next
dialogue at `02:53:28.059`. Those chat timestamps alone do not establish lost TPS.

Dialogue never pauses either split clock and there is no fixed dialogue duration
subtracted from server time. At 20 observed ticks per second, five seconds of
dialogue add five seconds to both clocks and produce no loss.

## Packet handling correction

The old `ConnectionMixin` saw only top-level `ClientboundPingPacket` objects.
Pings inside `ClientboundBundlePacket` were omitted, so their elapsed time could
incorrectly appear as lost time. Repeated ping IDs were also counted more than
once. This is a demonstrated coverage defect in the old hook, not proof that it
caused the reported five seconds in this particular run.

- Dungeon ticks now come from `ClientCommonPacketListenerImpl.handlePing` at
  `TAIL`. Minecraft 26.1.2 enforces its client thread before handling the ping,
  and vanilla dispatches bundled pings through this handler too. Tick delivery
  therefore follows the same applied packet order as split-ending messages.
- `ServerTickSequence` deduplicates consecutive real tick IDs, ignoring zero
  pings even when they separate duplicates. It does not infer extra ticks from
  numeric gaps between IDs.
- Network TPS sampling traverses bundles separately at receipt time. It counts
  distinct pings even if several arrive in one batch at the same timestamp.
  Client frame stalls therefore do not directly lower that network TPS sample.
- Elapsed split time uses a monotonic clock, so computer clock corrections cannot
  create lost time. Before any tick has been observed, server duration and loss
  remain unavailable instead of reporting the entire elapsed duration as lag.
- The `dungeon-splits` trace category records only run starts, phase boundaries
  and run stops: wall/server totals, phase tick count, longest applied tick gap,
  received/bundled/duplicate counts, longest network arrival gap and network TPS.
  There is no per-frame or per-tick logging.

Time Lost remains a packet-based estimate of real duration minus the bounded
observed server duration. It cannot by itself distinguish a server slowdown from a
network delivery delay or delayed local processing at a phase boundary. Do not
claim a specific TPS drop without corresponding clock/packet evidence.

The frozen phase-loss display, Minutes/Seconds setting, Time Lost toggle and
retention until leaving the dungeon instance are unchanged.

## Regression coverage

Six focused cases cover bundled pings with repeated boundaries, several ticks
in one arrival batch, sequence reset/ID reuse, healthy Maxor dialogue, genuinely
slow observed ticks during dialogue, and an absent tick stream. Existing split
ordering, completion, frozen-loss and overlay visibility tests remain applicable.
