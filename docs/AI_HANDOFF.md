# Current handoff

Entry point for Kung maintenance. Project rules: [AGENTS.md](../AGENTS.md).
Read the [code map](CODE_MAP.md), then only the topic needed for the task.

## Current state — 2026-09-15

- Active module: Minecraft **26.1.2**, version **0.3.2**, Java **25**.
  The version source is [gradle.properties](../gradle.properties).
  The user's existing version change to 0.3.2 is preserved.
- Last code validation: full shared-root active-module build with Java 25,
  **519 tests**, zero failures/errors/skips, including the Kernel rate over active farming time,
  Wild Rose/flower/pumpkin visitor-timer reductions,
  Kernel milestone claims, per-floor split PB persistence, prediction toggle and both update modes,
  Visitor Alarm reminder replacement/chat mute and immediate offer-click acknowledgement,
  update polling/cooldown and independent Kernel donation/sidebar reconciliation.
  Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.3.2.jar`
  (rolling Kernel farming rate with idle pause, per-floor split PBs and finish prediction, 6th Visitor Alarm with Wild Rose/flower/pumpkin timing, five-minute update polling and lobby reminders, Kernel donation/sidebar ordering and persisted pending gains,
  Safari Uniques, Feast Hub farm option and profile persistence, Grand Feast Kernel balance, donation format/filter fix, yellow active tier and progress marker, Roman-tier parsing/diagnostics, menu-font resource fix, Feast Progress, menu redesign, dismissal fix, changelog history/command, chat-emote alias,
  realistic update preview, patch notes, update popup, Mimic discovery/diagnostics
  and unfinished-room score correction).
  Split clock conservation and trace retention also passed the supplied live
  trace check at 18:16:58; details are in the split timing report.
- Garden > 6th Visitor Alarm (default off, volume 70%) repeats alternating pling
  tones until an Accept Offer / Refuse Offer click, chat mute, a visitor departure,
  or disabling. A clickable local chat reminder appears immediately and every
  10 seconds, replacing the prior reminder in chat history and all wrapped lines.
  Other messages are preserved, and the next cycle replaces any surviving old
  reminder too. Clicking it runs client-only `/kung visitors mute`, stopping sound
  and reminders for the current cycle while leaving the feature enabled. The
  command also works outside the Garden; stalled ticks do not replay reminders.
  Offer clicks now stop the sound immediately, including unsuccessful attempts;
  the acknowledged cycle stays silent through stale tab updates until a visitor
  leaves/replaces another. The click is observed before local inventory prediction
  and guarded by container, top slot, visitor title, info lore and button name.
  Five visitors alone do not trigger: the hidden sixth timer includes crop/pest
  reductions. Immediate replacement at five visitors stops the previous alarm;
  menus and lobby travel do not silence a latched alarm. Guest Gardens cannot
  trigger; disconnect/profile change clears it. Optional read-only SkyHanni data
  can seed an already-known timer. Without a known initial timer, a first full
  queue uses a conservative interval and can alarm late. The supplied 20:39:12
  trace confirms roster parsing and a learned 360-second interval, but caught an
  early alarm: seven Field Mouse rewards were each treated as a kill. Only its
  Dung reward now counts; Lunar Moth and Overclocker extras are also excluded.
  A replay of the 14-message/eight-kill burst fails before the fix and passes
  afterward. The 2026-09-15 01:15:07 UTC trace catches a late alarm instead:
  eight pests are counted correctly, but Wild Rose harvests were not recognized,
  leaving 44,375 ms at the last kill. The shared `GardenCropTracker` includes Wild Roses,
  Sunflower/Moonflower and carved pumpkins, preserving maturity checks and the
  shared initial/continued-mining position guard. Five new regressions cover crop
  classification, deduplication and combined harvest/pest timing; the focused
  crop/alarm/message/config run passes 41 tests. Live corrected Wild Rose/pest
  timing, immediate accept/refuse silence, 10-second reminder replacement/chat mute,
  rearming, off-toggle and startup seed remain open.
  `visitor-pest` records reward decisions, remaining time and crop reduction count;
  bounded `visitor-crop` records expose recognized blocks and timer reductions.
  See [Visitor Alarm](VISITOR_ALARM.md).
- Hunting > Safari Uniques (default off) displays four fixed columns with all 37
  species: Forest 9, Cavern 9, Icy 9, Haunted 10. Headers show caught/total; missing
  names are red, caught names gray and struck through, completed headers green.
  Own captures and Loot Share count once per species, including Hideyho and
  Sparkling variants. Floor drops/attempts/player quotes do not count. The shared
  instance epoch clears captures on world/server changes; biome movement and
  other players joining preserve them. `/kung hud` moves/scales the full preview.
  Sixteen new tests and the full build pass; no Feast implementation files were
  changed by the Safari work. Live location gating, catches, next-instance reset
  and HUD readability/scaling remain open. See [Critter Safari](CRITTER_SAFARI.md).
- Garden > Feast Progress (default off) shows cumulative donations, a segmented
  milestone bar and donations to the next tier during a Feast in the Garden and
  Hub farm area. Show in Hub Farm defaults on; turning it off retains Garden visibility.
  Hub recognition requires Hub evidence plus Farm/Wheat Farm, Farmhouse or Communal Stew.
  Completed tiers are green, the active tier yellow with a vertical progress marker,
  and future tiers gray. The marker advances within the current tier's interval;
  full completion is entirely green with no marker. The HUD editor uses the same drawing.
  Grand appends the Kernel currency balance, e.g. `16 to next - 1,234 Kernels`.
  A new line below it shows `Avg Kernels/h` over 20 minutes of farming time,
  using actual elapsed farming time until the window fills and a 60-second warmup.
  Mature crop input starts/resumes; five seconds without another harvest pauses
  the clock. Hub/world travel pauses immediately, retaining the rolling history
  within the connection. Only Ted-confirmed Seasoning gains count, even without
  a balance baseline or beyond the milestone cap; claims, balance sync and spending
  do not affect it. Rate history is session-only and resets on disconnect/disable,
  profile/account change or a different Feast. HUD bounds extend downward by 12
  units while position, width and scale stay unchanged. Eleven new rate tests and
  the existing chat-routing test cover these paths; the focused Feast/crop/config
  run passes 101 tests. Live rate, idle/resume and new-line rendering remain open.
  Exact `Kernels:` or `123 (+3) Kernels` server sidebar rows sync automatically;
  `Your Kernels:` lore in Grand Bakery is a confirmed second source. The user's
  Scott screenshot shows 123 and they report correct live sync after opening it.
  Opening the milestone menu alone need not supply the currency balance.
  Confirmed Ted messages then add one. Grand milestone clicks now also credit
  the tooltip's Kernel reward once a server item update removes glint/claim text.
  Empty predicted slots do not count; new Grand Feast containers can confirm the
  click within 15 seconds. Overlapping claims and early sidebar totals do not
  double count. Confirmed rewards persist through the existing pending-gain cache
  without advancing donations. The supplied 20:57:12 UTC trace establishes the
  fourth tier's 100-Kernel claim prompt and container replacements. The later
  2026-09-15 01:15:07 UTC trace and user report confirm a live tier-V claim:
  454 -> 579 Kernels (+125), server-confirmed 192 ms after the click, donations
  unchanged at 250. Overlapping claims and spending comparison with Scott remain open.
  Currency survives Feast changes and stays
  `--` until a total is known. No other mod or menu automation is required.
  The brief Custom Scoreboard cache bridge has been removed: the user reports
  its total stays one below Scott. A local cache comparison found Kung 136 versus
  SkyBlock API 135 for Coconut; this alone does not prove packet ordering.
  Kung reads the already-applied server sidebar immediately before counting Ted,
  so a pre-donation row in the same packet batch need not wait for shared tick
  publication. Tab-only changes cannot replay that older shared sidebar.
  Late sidebar rows cannot erase a gap supported by actual Ted confirmations.
  Pending gains persist across restarts/profile selection; matching totals clear
  them, larger decreases and fresh menu totals correct spending. Older cache files
  default the added pending field to zero. No unconditional +1 or third-party
  balance import occurs. Live donation/source ordering still needs confirmation.
  Server `Kernels: 123 (+3)` rows and Unicode spaces now also parse directly.
  Harvest follows all three Autumn months; Grand follows the active elected perk.
  Open the Feast menu once to sync; automatic Seasoning donations then advance it.
  Both values now persist per account/profile under `config/kung/feast-progress.json`:
  at most 32 entries, bounded reads, coalesced background writes, temporary replacement
  and shutdown flush. Lobby/restart/disable restore the matching profile's values.
  Repeated timestamped Coconut join notices in the 18:08–18:13 live log exposed
  the prior unconditional profile-message reset. These now retain the live counter.
  Profile IDs, when available, distinguish deleted/recreated fruit names. Donation
  restoration requires the same election year for Grand or SkyBlock year for Harvest;
  a new Feast needs a fresh baseline. The memory-only prior build needs one initial
  progress sync after upgrade; the server sidebar can supply the initial currency
  baseline at a donation without Grand Bakery. `/kung hud` moves/scales it and displays the sketch's sample values.
  The 17:24:32 trace identifies the failed sync: raw milestone names use I–IX,
  while the parser accepted only Arabic digits and found zero of nine tiers.
  Both forms now work. An unchanged menu fixture reproduces the failure before
  the fix and now yields 25/750, 50 to next; a subsequent Seasoning yields 26/750,
  49 to next. Completed tiers retain capped fractions. Eighty-nine focused Feast/config/
  mayor tests pass, including restart/lobby/profile isolation, recreated profiles,
  new Feast/API recovery, malformed files, write coalescing, Hub toggles and the
  existing Kernel, Roman-tier, real-message and chat-filter regressions. Kernel
  cases cover direct versus shared sidebar ordering, delayed rows, donation-backed
  gaps, no guessed offsets, menu corrections, pending-gain persistence and old files.
  Thirteen new regressions cover claim detection/confirmation, duplicate and
  overlapping claims, source ordering, invalidation and saved milestone gains.
  Bounded `feast-menu` diagnostics remain available. The user confirms live menu
  sync with a screenshot showing 27/750 and 48 to next. The 17:37:21 trace and adjacent
  Minecraft log confirm a missed drop at 17:36:58: `(+80\uE02B)` and `(automatically
  donated)` did not match the percentage/required-exclamation parser. Both old/new
  formats and optional timestamps now work. Observation uses non-canceling ALLOW_GAME
  before chat modification/filtering; `feast-donation` logs parse/context outcomes.
  The real message now advances 27 to 28 in tests, without doubling on menu resync.
  Live Hub visibility/toggle, restart/lobby persistence, Kernel gains and spending
  still need confirmation; `feast-kernels` records the source and balance.
  Live donation counting with the prior fix, marker appearance/milestone transitions,
  season/profile changes and HUD checks also remain open.
  See [Feast Progress](FEAST_PROGRESS.md).
- Kung settings now render at 80% size, with feature columns another 10% narrower
  (171 logical units; 28% narrower overall). Search, title, tooltips and menu modals
  use the same transform; input and scrolling use the matching menu coordinates.
  The Odin-inspired design adds a menu-local Inter font, rounded charcoal panels,
  soft shadows, wider gaps and a narrower, raised search field. Updates stays open
  with Installed/Latest versions and Changelogs → Open. HUD/toast scale remains
  independent. The 17:07:37 user trace/screenshot and adjacent Minecraft log expose
  missing glyphs: uppercase `kung:Inter.ttf` invalidated the whole menu font.
  Font/license filenames and the JSON reference now use lowercase. A new regression
  using Minecraft's provider codec failed with that exact error before the fix and
  now passes; the rebuilt JAR contains the corrected paths. Live readability with
  this corrected JAR, clicks, dragging, scroll and modals remain open.
  See [layout conventions](DEVELOPMENT.md#changing-code-and-comments).
- Kung checks for updates at startup and every five minutes, independently of the
  menu. A newly found compatible release shows a popup when ready on Hypixel.
  Polls continue with an available update, preserve it during refresh/failure and
  cannot overwrite a concurrent download or pending restart.
  The bottom-right card slides in/out, stays eight visible seconds and pauses on
  hover. Open Updates opens the existing Kung update control; GitHub opens the
  latest release page. Open chat to click, or use `/kung preview updates` to test
  with fresh GitHub data and the ordinary card text, even without a newer release.
  It shows installed/latest versions and accurately labels an up-to-date build.
  Once an automatic card finishes/closes, a five-minute cooldown starts. After it
  expires, the next lobby/world change can repeat the same release. Early transfers
  are discarded; idle expiry/repeated polling alone stays quiet, and reconnects
  cannot bypass the cooldown. Shared instance epochs coalesce packet resets.
  Passive `update-check`/`update-notice` traces expose checks, display and completion.
  The settings header also shows the loaded version, e.g. `Kung - v0.3.1`.
  Thirty-three update-package tests pass, including exact cooldown boundaries and
  periodic checks without menu input. Live polling, timed lobby reminders, joins,
  layering, GUI scales and clicks remain open.
  See [updates](UPDATES.md).
- The first Kung settings opening for an unseen installed version shows that
  exact GitHub release's patch notes above the menu. Text scrolls; Got it/Escape
  dismisses, GitHub confirms the source link. Successful display is remembered
  under `config/kung/updates/release-notes.properties`, including a shutdown flush.
  Explicit Close/Got it/Escape/Enter persists `lastDismissedVersion`, including
  loading/error states, so the offer stops repeating; successful body display still
  stores `lastSeenVersion` separately. `/kung changelog` and the permanent menu
  button reopen it after display/dismissal. A permanent narrow history sidebar loads
  its first page when the popup opens, then selected bodies and further pages on
  demand. The installed version stays pinned above the list; compact < / > arrows
  page the history. List and notes scroll independently, preserving the list position
  across selections and the notes position across page changes. Footer actions are
  narrower, and the popup widens to retain space for the notes. Historical
  reads do not consume the installed version; caches are bounded and memory-only.
  Sixteen tests cover persistence, dismissal, retries, selection races, lazy caches, actual
  paginated HTTP/tag requests and layout bounds. Live UI/command checks
  remain open. Both `v0.3.2` and `0.3.2` GitHub release endpoints returned 404 on
  2026-09-13; notes require the matching release/body to be published.
  See [patch notes](UPDATES.md#patch-notes-after-installation).
- Latest Mimic trace (14:44:17): Catwalk is first detected 21.370 s after the
  countdown and marked immediately. Its chunk was visible earlier; the trace
  cannot establish when the chest itself arrived. The fast scanner now includes
  pending block-entity positions; discovery-source and prior-scan diagnostics
  distinguish this path from the full-block fallback. Live timing remains open.
  The earlier 01:56:28 trace has competing Redstone_Key and Mines
  trapped-chest candidates, so the map marker is intentionally withheld. Mines'
  2D waypoint is active. The kill is accepted from starziiiii's party report;
  the subsequently read profile has Extra Score Messages off. Added candidate
  positions/rooms and kill packet/source/switch diagnostics; no detection or
  announcement policy was changed. A precise capture of the fixed Redstone Key
  chest (not the disappearing Mimic chest) is still needed to exclude it safely.
  Local kill failure before the party report is not proven by the old trace.
  See [Mimic evidence and next live check](MIMIC_STATIC_CHESTS.md#prüfung).
- Score no longer fills unfinished clear rooms with Blood/boss forecast credit.
  Pending as well as failed puzzles subtract ten skill points plus their missing
  room contribution; tab/map evidence is reconciled and cached. The server's
  parenthesized Cleared format now supplies the denominator when available.
  Twelve new regressions cover early-300 scenarios; live boss-entry/final-score
  comparison remains open. See [score readiness](RUN_STATISTICS.md#score-and-unfinished-rooms).
- Player Stats off suppresses the complete end-of-run summary,
  including shared totals and pending exit flushes. Boss Map off hides the entire
  map HUD throughout the boss phase; early arena teleports and layer gaps are
  covered. Mimic's red 3D chest box/world-render callback is removed; the 2D
  waypoint and map marker remain. Three regressions pass; live checks with the
  reporting friends' profiles remain open. See [statistics](RUN_STATISTICS.md),
  [boss maps](BOSS_MAPS.md), and [Mimic lifetime](DUNGEON_ARCHITECTURE.md#mimic-lifetime).
- Util > Chat Emotes (default off) replaces `:iman:` and `:ironman:` with `♲` in
  outgoing chat and commands. Settings list both aliases without the long history
  hint. Fabric's modification callbacks preserve Minecraft's
  original input history for Up-arrow recall. Event/config tests pass; live recall
  and compatibility with the user's other chat mods remain open.
  See [chat emotes](CHAT_EMOTES.md).
- Run statistics use the five-player dungeon roster, excluding stale
  global party members. Shared `Secrets Found: n` no longer credits all party secrets
  to self. Missing personal sources are explained; API preparation and typed sync
  reports preserve valid data without trusting older erroneous reports. Seven added
  tests and a local sync HTTP round trip pass. The live profile has API disabled/no
  key, so full personal counts still require an actual data source. Sync users also
  need the updated companion server. Live 0.3.1 validation remains open.
  See [run-statistics evidence](RUN_STATISTICS_FIXES.md).
- Splits now keep real-time personal bests for each phase separately per Entrance,
  F1–F7 and M1–M7 in `splitsOverlay.personalBests` in the existing Kung config.
  Enabled, accurately completed phases contribute; one save at finish/exit uses
  the final floor metadata so late M7 detection cannot pollute F7 records.
  Unknown/interrupted phases and manual debug runs are excluded. Splits Overlay >
  Time Prediction is now a toggle with expandable Update setting, like Boss Messages.
  Phase End (default) uses the last boundary plus active/later phase PBs. Live uses
  current Total plus only later phase PBs: during Storm, Terminals/Goldor/Necron
  and the additional M7 phases, with no Storm PB. Changes apply mid-phase; both
  settings persist. Turning prediction off removes its row/editor height while
  PB collection continues. Missing required PBs show `--`; confirmed completion
  freezes both modes to Total. The full 486-test build passes, including the
  explicit F7/M7 Storm example, mode switching, persistence and hidden-row bounds.
  Live F6/M7 learning, restart restoration, menu toggle/expansion/mode clicks and
  HUD appearance remain open.
  See [splits and personal bests](RUN_STATISTICS.md#splits).
  Split clocks conserve every accepted tick. Full traces preserve 128 split
  records, including countdown/Mort start markers. The 18:16:58 live
  trace retains two complete runs with consistent phase partitions and no clipped
  tick time; the screenshot run measures 14,291 ms loss. No further timing change
  is indicated by this check. See [split timing](SPLITS_TIMING_FIXES.md).
- Runtime files are grouped under `config/kung/` and `logs/kung/`.
  Legacy profile-root folders migrate on next startup with conflict preservation.
  See [file storage](FILE_STORAGE.md); the live startup migration remains unverified.
- **Buttons and Dueces static-chest templates are bundled
  in the JAR**, with no runtime profile JSON. New explicit captures remain in memory
  until Minecraft restarts; `/kung mimic copy` exports them to the clipboard.
  The redundant profile file was removed after verifying the packaged original data.
  Repeated recognition in differently rotated live rooms still needs verification:
  [Mimic static chests](MIMIC_STATIC_CHESTS.md).
- Known protected fixes: conserved filtered tick clocks, settled loss display, retained
  post-run results; Necron map calibration; packet-based instance lifecycle.
  Their contracts and regressions are linked from the code map.

## Where to continue

- Build, focused tests, diagnostics: [DEVELOPMENT.md](DEVELOPMENT.md).
- Feature ownership, tests and topic links: [CODE_MAP.md](CODE_MAP.md).
- Starting or resuming a Codex task: [WORKING_WITH_CODEX.md](WORKING_WITH_CODEX.md).
- Older architecture proposals: [FOUNDATION_TODO.md](FOUNDATION_TODO.md).
  Verify against current code before treating an unchecked item as missing.
- Historical detail only when needed: [archived handoff](AI_HANDOFF_ARCHIVE_2026_09_12.md).

Keep this page short. Replace status rather than appending another release recap;
record detailed evidence in the relevant topic or a dated investigation.
