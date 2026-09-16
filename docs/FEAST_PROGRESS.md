# Feast Progress

Active module: `versions/mc26_1_2` (Minecraft 26.1.2, Java 25).

## Player behavior

Enable **Garden > Feast Progress** in `/kung`. The master defaults off, including
for old configurations. `/kung hud` moves/resizes it and shows an illustrative
`234 / 750` preview with the milestone bar, `16 to next - 1,234 Kernels` and
`Avg Kernels/h: 120`, even while disabled.

Live visibility requires Hypixel's Garden or the Hub farm area and an active Feast:

- Harvest Feast: **Early Autumn, Autumn and Late Autumn**, using fresh sidebar dates.
- Grand Feast: the elected **Grand Feast** perk, including the selected minister
  perk when present. Candidate perks and the Finnegan name alone do not enable it.
- **Show in Hub Farm** defaults on. It includes Farm/Wheat Farm, Farmhouse and
  Communal Stew only when shared location evidence also identifies the Hub.
  Turning it off keeps the Garden display. Other regions/islands and inactive
  seasons hide the overlay. The normal HUD layer respects F1.

Open the **Harvest Feast** or **Grand Feast** menu once to establish a baseline
for each new Feast. Opening it at Ted in the Hub works. The baseline and Kernel
balance are saved per Minecraft account and SkyBlock profile and restored after
restarts, reconnects, ordinary warps and disabling/re-enabling the feature.
Upgrading from the earlier memory-only implementation needs one initial progress
sync. Kung reads Kernels directly from the server scoreboard and counts Ted's
confirmed gains and claimed Grand Feast milestone rewards; another mod's cached
balance is not used as a baseline.
Before synchronization the HUD
shows `-- / 250` or `-- / 750` and `Open Feast menu to sync`, never a made-up zero.
Reopening explicitly resynchronizes, including corrections to a lower server total.

The top line is the cumulative total / final goal. Each equal-width bar segment
represents a milestone: completed tiers are green, the entire active tier is yellow,
and future tiers are gray. A light vertical marker with a dark outline moves across
the active tier according to donations since the previous goal. It extends slightly
beyond the bar to distinguish it from the dividers. Reaching a goal turns that tier
green and moves the marker to the start of the next yellow tier. Unsynced progress
stays gray; at full completion every tier is green and no marker is drawn.
The line below the bar shows donations needed for the **next** tier, or `All milestones
reached` at the cap. Seasoning drops advance it; rewards/Kernels do not count again.
During **Grand Feast** the same line appends the player's Kernel balance, e.g.
`16 to next - 1,234 Kernels`. Harvest never shows Kernels. A long footer scales
down to the bar width, preserving the HUD bounds and the player's position/scale.
Unknown currency displays `-- Kernels`; zero is displayed only after a real balance.

Grand Feast adds one more centered line: **Avg Kernels/h**. It uses an exponentially
weighted average of confirmed Seasoning-related Kernel gains, with a **five-minute
half-life in farming time**. A gain and the farming time around it retain half
their weight after five farming minutes, a quarter after ten, and 6.25% after
twenty. Recent performance therefore matters more; older observations fade
continuously without a hard cutoff. Milestone claims, sidebar/menu balance
changes and spending do not affect this rate. The denominator is weighted time
actually observed, so startup does not assume a full history of zero gains. The
first 60 farming seconds show `-- (warming up)`; afterward, no gains show zero.
The rate is still an estimate and can fluctuate with sparse/random drops.

Mature crop clicks start/resume measurement. **Kernel Timeout (s)** is a seconds
text field under Garden > Feast Progress, defaulting to **60** (also for existing
configurations) and accepting **10–300 seconds**. A 20-second pest hunt therefore
keeps the clock running by default. Once the time since the last crop reaches
the selected timeout, the rate shows `(paused)`. The timeout grace is included
in farming time; later idle time is excluded. Long gaps cannot slip into the denominator if a tick is
delayed. Outside the Garden, during transfers or without Grand Feast evidence,
the clock pauses immediately. Returning requires another harvest. The Hub farm
overlay displays the paused value. Both weights freeze during pauses. The weighted history survives ordinary warps
within the same connection but starts fresh after disconnect/restart, disable,
an account/profile change or a different Feast. Currency and milestone progress
retain their existing persistence. HUD width, top position and scale stay the
same; editor bounds grow from 40 to 52 units to include the new line. Harvest
keeps its previous text and bar.

The timeout persists as `feast.kernelTimeoutSeconds` in the existing settings.
Integer input is clamped to 10–300; blank, non-integer and overflowing text retains
the previous value. Loading also normalizes the range. Edits apply immediately to
the deadline measured from the last crop without clearing the weighted rate.
Already measured time stays intact and past pauses are not backfilled. Editing
after a world/location pause still requires a new crop to resume measurement.

The settings card contains only Show in Hub Farm and Kernel Timeout (s).
The former static timeout/progress/currency info rows are now delayed tooltips:
hover over Feast Progress for two seconds for sync/rate help, or over a setting
for its own explanation. The timeout tooltip includes the 10–300-second range
and 60-second default. Tooltips also show full names when the narrow menu clips
the label. They wrap within the menu viewport and reset on leaving the row,
changing targets, clicking, scrolling or opening an editor/modal. Timing and
target resets have model-test coverage; live hover/rendering remains to check.

## Sources and ownership

`feature/garden/FeastOverlayFeature` owns Fabric callbacks and HUD drawing.
`FeastKernelRate` owns the independent in-memory rate and monotonic farming clock.
`GardenCropTracker`, shared with the visitor alarm through separate position
guards, recognizes the actual Garden crops including Wild Rose and
Sunflower/Moonflower. The existing mining hook observes initial/continued input
without altering gameplay. Only the anchored Ted Kernel confirmation counts for
the rate, and only during a currently eligible farming interval. It is counted
once through the existing pre-filter message observer, without also counting the
Seasoning message. This works before the balance/menu baseline is known and after
the final milestone cap. Two weighted totals use constant memory. For each active
interval `dt` in milliseconds, let `lambda = ln(2) / 300000` and
`decay = exp(-lambda * dt)`: multiply weighted gains by `decay`, and update weighted
time to `oldTime * decay + (1 - decay) / lambda`. Each confirmed gain adds one to
the weighted gains. Their ratio times 3,600,000 gives Kernels/h. The integral uses
`expm1` for short-interval precision and is independent of tick/render cadence.
Raw gain/time totals only serve diagnostics and the 60-second warmup; they are
not the currency balance or the rate numerator/denominator. Missing event evidence
pauses without discarding the known history; a changed event key resets it.
Account/profile names and a subsequently changed Profile ID also isolate rates.

`FeastContext` consumes the existing packet-confirmed instance/sidebar/tab cache.
Garden recognition uses the location or `Area: Garden` field, never chat mentions;
matching is cached until shared evidence changes. New instances drop the current
date, preserving event history. No dungeon lifecycle/scanning behavior is changed.

`FeastProgress` reads only the **top inventory**, at most 54 slots and 40 lore lines
per item, every five client ticks while a matching menu is open. Five Harvest or
nine Grand entries must contain consistent cumulative progress and increasing
goals. Milestone names accept the server's Roman numerals (`I`–`IX`) and Arabic
digits; both forms resolve to the same tier for duplicate detection.
Completed tiers can cap their numerator (`5/5`) while later tiers show
`5/25` and `5/750`. Partial/mixed menu updates are ignored. All actual goals come
from the menu; the editor's example uses the observed Grand goals.
Claimable tiers' enchantment glint and the current tier's yellow pane do not
participate in donation-progress parsing. The user confirmed that claimed/claimable tier text keeps
the capped donation fraction. Menu parsing normalizes Unicode spaces and accepts
a progress-bar glyph immediately touching the numerator after formatting is removed.

`FeastKernels` owns a separate currency balance. It automatically
reads exact `Kernels: 1,234`, `Kernels: 123 (+3)`, `1,234 Kernels` or `123 (+3) Kernels` rows from the
shared server sidebar cache (123 is the total in the last example), so that
source requires no menu opening. It also reads `Your Kernels: 1,234` lore when the
user opens Grand Feast or Grand Bakery. The user's screenshot confirms that the
Grand Bakery item inside **Grand Bakery**, opened at Feast Baker Scott, contains
the owned total. Opening the Feast milestone menu alone need not supply it.
No menus are opened or clicked by the mod. Rounded sidebar amounts such as `1.2k`
are not treated as exact totals. There is no currency HTTP request or required dependency on another mod.

When Ted confirms a Kernel, Kung first reads the already-applied Minecraft
scoreboard through `SkyBlockSidebar` (at most 15 rows). This sees a pre-donation
balance delivered in the same packet batch before the shared end-of-tick cache
publishes it, then adds exactly the confirmed Kernel. It can establish a missing
baseline without opening a menu when the server supplies the row. No world scan,
network request, shared tracker mutation or third-party data access is involved.
Tab-only changes do not replay the older shared sidebar after this immediate read.

After a known balance, Ted's server message `Thanks for the donation! I've
added a Kernel to your purse.` adds one Kernel. Seasoning messages and merely
viewing tier-reward descriptions do not add currency. Chat observation uses the
same pre-filter hook as donations. Confirmed gains not yet reflected in the sidebar are tracked
separately. If a changed sidebar trails the current balance by no more than those
gains, the higher confirmed total stays and the remaining gap is remembered.
A matching/higher sidebar clears that gap; larger decreases correct spending.
A fresh menu always supplies the total, even a decrease of one or zero. There is
no unconditional `+1` correction or inference from another mod's discrepancy.
Repeated unchanged observations cannot undo a newer chat gain. The balance survives
ordinary warps and Feast changes. Profile changes select that profile's own saved
balance. A missing baseline stays unknown until an exact server balance is observed.
Once learned, returning to Scott is not required after each restart: the saved
total plus subsequent Ted confirmations and milestone claims is used. Activity while Kung is disabled,
on another installation or offline cannot be inferred; a fresh server total corrects
such changes. Without an exact server/menu observation, a missing balance stays
unknown; past activity is not reconstructed from another mod's possibly stale cache.

`FeastMilestoneClaims` tracks manual claim clicks in the top inventory of an exact
**Grand Feast** menu on Hypixel. It requires a completed tier, the `Rewards:` /
`- Kernels xN` lore, and either enchantment glint or `Click to claim!`. Reward
amounts come from the item rather than a hardcoded tier table. Roman and Arabic
tier names use the same parser as donation progress. Left/right and shift-clicks
are observed at the game-mode hook before local inventory prediction; no clicks
are generated, changed or cancelled.

A click alone does not add currency. Within 15 seconds a server slot/content
packet must show the same completed tier and reward without glint or the claim
prompt. Confirmation can arrive in a newly opened Grand Feast container. An empty
predicted slot, a partial menu or an unchanged claimable item is not confirmation.
Each confirmed tier credits once per Feast; duplicate clicks/packets and menu
reopens cannot add it again. A failed/expired attempt can be retried. Outstanding
clicks clear on a world/profile change, disable/disconnect, a different Feast or
an authoritative owned menu balance. No unconfirmed click is persisted.

Overlapping claims share the balance from the first click and include subsequent
Ted confirmations. The resulting balance floor prevents a sidebar that already
includes the rewards from adding them twice. Late sidebar rows use the existing
pending-gain reconciliation; a fresh Bakery total still corrects spending. Claimed
Kernels and their pending gains are saved through the existing cache, while
donations and milestone-bar progress are unchanged. An unknown balance remains
unknown until an exact server/menu total is available.

`FeastSession` owns the baseline and menu observation identity. Unchanged or
delayed lore cannot undo chat donations. Only anchored server messages like
`RARE CROP! Seasoning (+148%) (automatically donated!)` count. The current format
uses the U+E02B Overbloom icon in place of `%` and omits the final exclamation mark;
both variants work, with an optional `[HH:mm:ss]` chat timestamp. The boost value
is Overbloom, not a quantity. Explicit `2x Seasoning` / `Seasoning x2` quantities
are supported. Observation uses Fabric's `ALLOW_GAME`, always returning true:
all listeners run even if another filter rejects the message, and observation
precedes `MODIFY_GAME`. There is no second listener on `GAME`/`GAME_CANCELED`, so
one server event is counted once. Identical messages in separate events count as
separate drops. Action-bar/player chat, other crops, Ted's tier messages and Kernel
thanks do not advance progress. The counter caps at the menu's final milestone.

`FeastPersistence` selects the cache using the player account UUID and normalized
SkyBlock profile name from anchored profile rows/server announcements. Repeated
join notices for the same profile do not reset anything. A server `Profile ID:`
UUID, when available, also invalidates a deleted/recreated profile with the same
fruit name. Social messages cannot select profiles; fresh profile announcements
take precedence over stale tab rows within the current instance.

`FeastStateStore` saves donations, goals, event identity and Kernels under
`config/kung/feast-progress.json`, resolved through `KungPaths`. The bounded cache
holds at most 32 account/profile entries and reads at most 128 KiB. Invalid data
does not become a baseline. One background writer coalesces updates and replaces
the file through a temporary sibling; shutdown waits up to three seconds for it.
Every accepted menu/sidebar/chat change updates the cache. Disconnect/disable
clear only live state. Settings remain in the existing `kung.json` `feast` section.
The same version-1 file now includes `pendingKernelGains`; missing fields in older
files mean zero, and values outside zero through the known balance are rejected.
This retains confirmed donation and milestone-gain evidence across restart/profile selection. Sidebar/menu
acknowledgements also save when they clear pending gains without changing the total.

Restoring donations requires matching Feast kind and event identity. Grand uses
the elected mayor's election year, so consecutive Finnegan terms cannot reuse
totals. Harvest uses the SkyBlock year: explicit sidebar years take precedence,
otherwise the standard year-zero timestamp and 372 twenty-minute days give a
restart-stable identity, with observed year wraps retained. A fresh Autumn sidebar
date is still required for visibility. Unknown dates/API outages do not erase the
saved baseline. A different Feast requires a new menu sync; Kernels survive it.

`SkyBlockMayorTracker` reuses the existing public election request. While this
feature is enabled, it attempts refreshes at most once per minute; Grand evidence
expires after two minutes without a successful refresh. Only `mayor.perks` and
`mayor.minister.perk` participate, excluding candidate history. Expired evidence
cannot keep Grand visible outside Autumn. Once the same elected term is confirmed
again, its saved baseline can be restored without reopening the menu.

Evidence checked on 2026-09-14/15:

- The supplied 2026-09-15 01:15:07 UTC trace and user report confirm a successful
  live milestone claim. At 03:15:03.116 local, tier V is clicked with reward 125
  and balance 454; at 03:15:03.308 the server item update confirms it and
  `feast-kernels source=milestone` records balance 579. The transient incomplete
  menu is ignored, donations stay at 250, and the older sidebar total 453 does
  not erase the gain. This validates this claim path; overlapping claims and
  subsequent spending still need live comparison with the server balance.
- The supplied 20:57:12 UTC trace shows Grand Feast at 22:56:56 with 150/750
  donations, a transient missing third tier at 22:56:57, then replacement
  containers 10 and 11. The fourth tier explicitly offers `Kernels x100` and
  `Click to claim!`; the first two retain capped progress without that prompt.
  At 22:57:05 a menu supplies the owned total 354. The eight raw items are kept
  in `src/test/resources/feast/grand-feast-225657.json`. This establishes the lore
  and menu-replacement behavior, but does not capture glint transitions or the
  individual server confirmation packets. The later trace above confirms a
  completed live claim.
- User screenshots show Grand tiers 1/2/9 with `5/5`, `5/25`, `5/750` and the
  automatic Seasoning message above.
- The 17:24:32 trace captures all nine raw names as `Feast Milestone I` through
  `IX`, with `incomplete-tiers:0/9`. The original Arabic-only name matcher skipped
  every tier. The unchanged names/lore are retained in the test fixture
  `src/test/resources/feast/grand-feast-172432.json` under the active module.
  It reproduces the failed sync before the Roman-tier fix, then yields `25 / 750`
  and `50 to next`. Observed goals are 5, 25, 75, 150, 250, 350, 450, 550 and 750.
  Completed tiers show `5/5` and `25/25`; the latter also has `Click to claim!`.
  Formatting-space variants remain covered, but were not the cause in this trace.
- The user's follow-up screenshot and confirmation establish successful live menu
  synchronization: the HUD shows `27 / 750` and `48 to next`.
- The 17:37:21 trace records menu totals 27 at 17:34:47 and 28 at 17:37:11, with
  no donation increment between them. The adjacent Minecraft `latest.log` contains
  `[17:36:58] RARE CROP! Seasoning (+80\uE02B) (automatically donated)` (the escape
  denotes the actual private-use glyph). The old parser rejects that format;
  regressions reproduce the failure and now increment 27 to 28 before any resync.
  Chat filtering is a separate compatibility improvement: the trace's general
  message logging was rate-limited and does not establish whether this drop was
  canceled. The locally cached Fabric message API source confirms ALLOW_GAME's all-listener,
  pre-modification delivery; tests exercise modified and canceled message paths.
- Kernel source inspection: the installed SkyHanni 7.56.0 `CurrencyApi` reads
  `Your Kernels` from Grand Bakery item lore, caches it per profile and adds Ted's
  confirmation messages. The installed Custom Scoreboard 1.12.14-2 delegates to
  its bundled SkyBlock API 4.2.19, whose `CurrencyAPI` reads `Kernels:` scoreboard
  rows and `Your Kernels:` inventory lore. These establish the protocol fields;
  Kung implements its own readers and does not import either mod's balance.
  The later user screenshot confirms `Your Kernels: 123` in Grand Bakery and the
  user reports that Kung displays the correct balance after opening it. The Hub
  scoreboard shows `123 (+3) Kernels`, with location `Communal Stew`.
- The follow-up inspection of Custom Scoreboard 1.12.14-2 and bundled SkyBlock API
  4.2.19 confirms that `KernelsElement` calls `CurrencyAPI.getKernels()`. The API
  learns Kernels from added Garden scoreboard rows and Grand Bakery lore, storing
  them under account UUID/profile name in its currency cache. It has no separate
  Kernel request or Kernel tab-widget reader, and does not count Ted's confirmation
  in that currency reader. The user reports a persistent one-Kernel deficit versus
  Scott. A later read of the same account/profile's caches found Kung 136 and
  SkyBlock API 135; latest.log includes the preceding Ted confirmation at 19:06:12.
  The short-lived import bridge was removed because it would inherit a stale
  baseline. The caches alone do not prove the exact server-packet ordering.
- The adjacent live log repeats `You are playing on profile: Coconut` followed
  by the same Profile ID at 18:08:15, 18:09:02 and 18:13:11. The prior unconditional
  profile-message invalidation caused ordinary joins to discard the baseline.
  Tests now preserve live progress through repeated timestamped announcements.
- Harvest calendar constants were checked against the installed SkyHanni 7.56.0
  `SkyBlockTime`: year zero is 1559829300000 ms and each year is 446400000 ms.
- [Hypixel's release announcement](https://hypixel.net/threads/hypixel-skyblock-0-24-4-harvest-feast-event-fossil-essence-shop-and-more.6089392/)
  confirms the three Autumn months, cumulative Harvest tiers and year-long Grand perk.
- The [public election endpoint](https://api.hypixel.net/v2/resources/skyblock/election)
  returned active Finnegan with `Grand Feast`, `mayor.election.year = 513` and Cole
  with selected minister perk `Mining Fiesta`. No API key is needed.

## Validation and live checks

The 2026-09-16 Java 25 full build completes **578 test cases: 577 passed, zero
failures/errors, one skipped** because Windows cannot create the updater test's
symlink fixture. The built 0.3.4 JAR contains the exponentially weighted rate,
configurable idle timeout and shared crop tracker;
`git diff --check` passes. No JAR was installed into a game profile.
The focused UI/config selection passes 12 tests, including the two-second hover
boundary and resetting the delay on target changes, leaving and interactions.
The focused rate/message/config/control selection passes **28 tests**, with no failures,
errors or skips. The 16 rate tests cover warmup, exact half-life decay without a
hard cutoff, response to improving/slowing farming with equal total gains,
tick/render cadence independence, zero gains, a six-hour steady stream, actual
farming time, idle grace and delayed ticks, pause/resume across worlds and missing
evidence, independent sources, clock regression and profile/event
resets. The pre-filter message test also covers rate gains under chat cancellation
and rejects action bars. Timeout tests additionally cover the default 20-second
pest gap, exact 10/60/300-second boundaries, custom values, live edits without
history loss, invalid text, range normalization and saved settings.
Thirteen existing regressions cover milestone claims and
their persisted gains. See the current handoff for the final Java 25 full build.

Focused tests: `FeastProgressTest`, `FeastContextTest`, `FeastSessionTest`, `FeastKernelsTest`, `FeastKernelRateTest`, `FeastMilestoneClaimsTest`, `FeastPersistenceTest`, `FeastMessageRoutingTest`, `VisitorCropTrackerTest`,
`SkyBlockFeastMayorTest` and Feast cases in `ConfigRegressionTest`. They cover
parsing, partial/delayed menus, event/profile resets, warps, duplicate message
sources, all seasonal gates, candidate/minister perks, expiry, completion and
configuration persistence. The raw-menu fixture also feeds `FeastSessionTest`:
after synchronization, one Seasoning changes 25/750 and 50 to next into 26/750
and 49 to next. See [current handoff](AI_HANDOFF.md) for the full build.

Kernel tests cover menu-free sidebar synchronization, balance/price separation,
zero/unknown values, malformed and inconsistent observations, gains, resync after
spending/rewards, profile invalidation, warps, filtered Ted messages and Grand-only
footer text. The user confirms the milestone reward appeared correctly in the HUD.
Claim regressions cover the supplied partial-menu lore, glint/prompt detection,
server confirmation, failed/duplicate clicks, overlapping claims with early/late
sidebar totals, simultaneous donations, fresh menu corrections, expiry,
event/profile/world resets, unknown/overflow balances and persistence without
changing donation progress.

Persistence tests exercise file round-trips, continued donations after restart,
repeated lobby announcements, account/profile isolation, recreated profiles,
changed Feasts, API recovery, late identity/event evidence, downward corrections,
coalesced replacement and malformed/oversized caches. Context/config tests cover
the Hub regions/toggle and restart-stable Harvest years.
Kernel regressions cover changed delayed sidebar rows, a directly applied sidebar
ahead of the shared tick cache, menu-free baseline discovery at a confirmed donation,
world changes, no guessed offsets, spending/menu corrections, pending-gain round-trips,
legacy caches and invalid pending counts. The event-order cases are controlled
regressions, not a replay of a newly captured live packet trace.

Live checks remain: compare the weighted rate through slower/faster farming phases;
no new live trace establishes whether the reported 14 Kernels/h was incorrect.
Verify the updated hover help, seconds field, a 20-second pest hunt, a pause past
the configured timeout and return from the Hub, including Wild Rose/flower harvests, chat filters
and HUD editor scaling. These rate paths have unit coverage, not a live play result.
Also verify Hub farm visibility/toggle and saved values after a
restart/lobby switch; confirm automatic baseline reading at a donation without
talking to Scott; compare Ted gains/reward claims/Bakery purchases with the
server balance; compare a new Seasoning with the server total; inspect the
yellow active tier and moving marker, including milestone transitions; test seasonal
and profile transitions, F1 and HUD positioning/scaling. Unit tests do not establish
compatibility with other chat filters or future server-menu changes. For a failed
sync, reproduce with the menu open and use `/kung log save` for Feast/context evidence.
The `feast-menu` trace now records the parse reason and actual milestone lore on
failure (also when event context is absent), bounded to three changed observations
per open menu and nine milestone entries per observation. Items with missing lore
remain visible to diagnostics. `feast-donation` records Seasoning candidate text,
parsed amount, active event, baseline availability and whether it counted, separately
from high-volume action-bar/message logs. Shared trace limits still apply. No
repeated chat messages are emitted.
`feast-kernels` records balance, last sidebar value, pending confirmed gains,
raw measured Kernels/farming milliseconds since the current measurement began,
weighted Kernels/farming milliseconds, calculated rate and pause state
for changed source observations, including ignored late rows, plus bounded Grand-menu
observations of the balance and Grand Bakery item lore for diagnosing missing totals.
`feast-claim` records accepted click intentions with container/slot, tier, reward
and baseline. Confirmed claims emit `feast-kernels source=milestone` with the tier
and reward amount, even when an earlier sidebar update already included them.
