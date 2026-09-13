# Current handoff

Entry point for Kung maintenance. Project rules: [AGENTS.md](../AGENTS.md).
Read the [code map](CODE_MAP.md), then only the topic needed for the task.

## Current state — 2026-09-13

- Active module: Minecraft **26.1.2**, version **0.3.1**, Java **25**.
  The version source is [gradle.properties](../gradle.properties).
  The existing `Version fix` commit reset this value; this diagnostic change preserves it.
- Last code validation: full active-module build, **310 tests**, zero failures/errors.
  Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.3.1.jar` (update notices and Mimic diagnostics).
  Split clock conservation and trace retention also passed the supplied live
  trace check at 18:16:58; details are in the split timing report.
- Hypixel joins now show one local notice per connection when the updater finds
  a newer compatible release, including checks that finish after joining.
  Clickable Open Updates opens the existing Kung update control; GitHub opens
  the latest release page. Reconnects can notify again; world changes do not.
  The settings header also shows the loaded version, e.g. `Kung - v0.3.1`.
  Five focused tests and the full build pass; live joins and clicks remain open.
  See [updates](UPDATES.md).
- Latest investigation: the 01:56:28 trace has competing Redstone_Key and Mines
  trapped-chest candidates, so the map marker is intentionally withheld. Mines'
  2D waypoint is active. The kill is accepted from starziiiii's party report;
  the subsequently read profile has Extra Score Messages off. Added candidate
  positions/rooms and kill packet/source/switch diagnostics; no detection or
  announcement policy was changed. A precise capture of the fixed Redstone Key
  chest (not the disappearing Mimic chest) is still needed to exclude it safely.
  Local kill failure before the party report is not proven by the old trace.
  See [Mimic evidence and next live check](MIMIC_STATIC_CHESTS.md#prüfung).
- Player Stats off suppresses the complete end-of-run summary,
  including shared totals and pending exit flushes. Boss Map off hides the entire
  map HUD throughout the boss phase; early arena teleports and layer gaps are
  covered. Mimic's red 3D chest box/world-render callback is removed; the 2D
  waypoint and map marker remain. Three regressions pass; live checks with the
  reporting friends' profiles remain open. See [statistics](RUN_STATISTICS.md),
  [boss maps](BOSS_MAPS.md), and [Mimic lifetime](DUNGEON_ARCHITECTURE.md#mimic-lifetime).
- Util > Chat Emotes (default off) replaces `:iman:` with `♲` in
  outgoing chat and commands. Fabric's modification callbacks preserve Minecraft's
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
- Split clocks conserve every accepted tick. Full traces preserve 128 split
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
