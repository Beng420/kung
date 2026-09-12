# Kung contributor guide

## Start small

- Work in `versions/mc26_1_2` (Minecraft 26.1.2, Java 25). `mc26_2` is parked;
  `mc1_21_11` is historical. Change another version only when requested.
- Inspect `git status --short`; preserve existing edits, including untracked files.
- Read [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md) for current status. Use the
  [code map](docs/CODE_MAP.md) to select the relevant topic, source and tests.
  Read those selectively; do not load all docs, archived handoffs or logs by default.
- Source and tests establish implemented behavior. Dated fix reports explain
  decisions; unchecked backlog items are proposals, not authorization to implement them.

## Keep these invariants

- Features are passive client QoL. Keep player-visible UI, commands and messages
  in English. Feature master toggles default off; useful subsetting defaults are fine.
- Preserve HUD/settings layouts during cleanup. HUD position and scale belong in
  `/kung hud`; shared message/UI/config helpers are the normal extension points.
- Dungeon work must preserve bounded scans, cached matching and packet-confirmed
  instance state. Read [dungeon architecture](docs/DUNGEON_ARCHITECTURE.md) before
  changing lifecycle, scanning, room ownership or player markers.
- Before editing room metadata, read [room data](docs/ROOM_DATA.md). The selected
  Wiki reference supplies metadata, never hash identity. Admin=34 crypts,
  Buttons=21; bundled Lava Pit is NORMAL. Core `-1005518830` is a type-only RARE hint.
- Keep profile data separate from bundled data. Normal builds must not sync learned
  rooms or install a JAR into a game profile as a side effect.
- Runtime files belong under `config/kung/` or `logs/kung/` (apart from the mod JAR).
  Use `KungPaths`; see [file storage](docs/FILE_STORAGE.md) before adding write paths.

## Validate and hand off

- For code changes, run the relevant tests while iterating, then
  `./gradlew.bat :versions:mc26_1_2:build --console=plain` with Java 25.
  Setup, focused tests and log commands: [development](docs/DEVELOPMENT.md).
- For documentation-only changes, verify paths, commands and consistency;
  no release bump or game build is needed. Report what was actually checked.
- Keep comments focused on reasons, units, ownership, invalidation and edge cases.
  Do not narrate obvious code or paste fix histories into classes.
- Update the affected topic when its contract changes. Update current handoff
  status in place; keep old investigations outside the default reading path.
- Finish with changed behavior, validation and remaining live checks. Separate
  confirmed results from assumptions; passing unit tests does not prove live play.
