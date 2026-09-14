# Feast Progress

Active module: `versions/mc26_1_2` (Minecraft 26.1.2, Java 25).

## Player behavior

Enable **Garden > Feast Progress** in `/kung`. The master defaults off, including
for old configurations. `/kung hud` moves/resizes it and shows an illustrative
`234 / 750` preview with the milestone bar and `16 to next - 1,234 Kernels`, even while disabled.

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
Upgrading from the earlier memory-only implementation needs one initial sync.
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
The bottom line shows donations needed for the **next** tier, or `All milestones
reached` at the cap. Seasoning drops advance it; rewards/Kernels do not count again.
During **Grand Feast** the same line appends the player's Kernel balance, e.g.
`16 to next - 1,234 Kernels`. Harvest never shows Kernels. A long footer scales
down to the bar width, preserving the HUD bounds and the player's position/scale.
Unknown currency displays `-- Kernels`; zero is displayed only after a real balance.

## Sources and ownership

`feature/garden/FeastOverlayFeature` owns Fabric callbacks and HUD drawing.
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
participate in parsing. The user confirmed that claimed/claimable tier text keeps
the capped donation fraction. Menu parsing normalizes Unicode spaces and accepts
a progress-bar glyph immediately touching the numerator after formatting is removed.

`FeastKernels` owns a separate currency balance. It automatically
reads exact `Kernels: 1,234`, `1,234 Kernels` or `123 (+3) Kernels` rows from the
shared server sidebar cache (123 is the total in the last example), so that
source requires no menu opening. It also reads `Your Kernels: 1,234` lore when the
user opens Grand Feast or Grand Bakery. The user's screenshot confirms that the
Grand Bakery item inside **Grand Bakery**, opened at Feast Baker Scott, contains
the owned total. Opening the Feast milestone menu alone need not supply it.
No menus are opened or clicked by the mod. Rounded sidebar amounts such as `1.2k`
are not treated as exact totals. There is no HTTP request or dependency on another mod.

After a known balance, only Ted's server message `Thanks for the donation! I've
added a Kernel to your purse.` adds one Kernel. Seasoning messages and tier-reward
descriptions do not add currency again. Chat observation uses the same pre-filter
hook as donations. Fresh absolute sidebar/menu balances correct rewards or spending;
repeated unchanged observations cannot undo a newer chat gain. The balance survives
ordinary warps and Feast changes. Profile changes select that profile's own saved
balance. A missing baseline stays unknown until an exact server balance is observed.
Once learned, returning to Scott is not required after each restart: the saved
total plus subsequent Ted confirmations is used. Activity while Kung is disabled,
on another installation or offline cannot be inferred; a fresh server total corrects
such changes. Currency is not imported from other mods.

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

Evidence checked on 2026-09-14:

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
  Kung implements its own reader and does not ship or call either mod's code.
  The later user screenshot confirms `Your Kernels: 123` in Grand Bakery and the
  user reports that Kung displays the correct balance after opening it. The Hub
  scoreboard shows `123 (+3) Kernels`, with location `Communal Stew`.
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

Focused tests: `FeastProgressTest`, `FeastContextTest`, `FeastSessionTest`, `FeastKernelsTest`, `FeastPersistenceTest`, `FeastMessageRoutingTest`,
`SkyBlockFeastMayorTest` and Feast cases in `ConfigRegressionTest`. They cover
parsing, partial/delayed menus, event/profile resets, warps, duplicate message
sources, all seasonal gates, candidate/minister perks, expiry, completion and
configuration persistence. The raw-menu fixture also feeds `FeastSessionTest`:
after synchronization, one Seasoning changes 25/750 and 50 to next into 26/750
and 49 to next. See [current handoff](AI_HANDOFF.md) for the full build.

Kernel tests cover menu-free sidebar synchronization, balance/price separation,
zero/unknown values, malformed and inconsistent observations, gains, resync after
spending/rewards, profile invalidation, warps, filtered Ted messages and Grand-only
footer text. The actual HUD drawing still needs a live check.

Persistence tests exercise file round-trips, continued donations after restart,
repeated lobby announcements, account/profile isolation, recreated profiles,
changed Feasts, API recovery, late identity/event evidence, downward corrections,
coalesced replacement and malformed/oversized caches. Context/config tests cover
the Hub regions/toggle and restart-stable Harvest years.

Live checks remain: verify Hub farm visibility/toggle and saved values after a
restart/lobby switch; compare Ted gains/reward claims/Bakery purchases with the
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
`feast-kernels` records changed balances with their source, plus bounded Grand-menu
observations of the balance and Grand Bakery item lore for diagnosing missing totals.
