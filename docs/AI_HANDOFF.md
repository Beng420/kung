# Current handoff

Entry point for Kung maintenance. Project rules: [AGENTS.md](../AGENTS.md).
Read the [code map](CODE_MAP.md), then only the topic needed for the task.

## Current state — 2026-09-12

- Active module: Minecraft **26.1.2**, version **0.3.0**, Java **25**.
  The version source is [gradle.properties](../gradle.properties).
- Last code validation: full active-module build, **293 tests**, zero failures/errors.
  Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.3.0.jar`.
  Split clock conservation and trace retention also passed the supplied live
  trace check at 18:16:58; details are in the split timing report.
- Latest change: `Dungeon > Extra Score Messages` adds a default-off master and
  independent Mimic/Prince/Bat announcement switches. Bonus tracking stays active;
  enabling only these messages needs no room scans. All 16 switch combinations,
  persistence and workload activation pass automated checks; live settings/party
  delivery remains to be checked. The existing project version 0.3.0 was retained.
  See [statistics and announcements](RUN_STATISTICS.md#room-credit-and-summary).
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
