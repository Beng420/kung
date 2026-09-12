# Development

Start with [AGENTS.md](../AGENTS.md), [current status](AI_HANDOFF.md) and the relevant
row of [CODE_MAP.md](CODE_MAP.md). The active module is `versions/mc26_1_2`;
Java packages start at `com.github.beng420.kung`. Other Minecraft modules are references.

## Build and tests

Use Java 25. The local JDK path is configured by `org.gradle.java.home` in
[gradle.properties](../gradle.properties); adjust that machine-specific path if needed.
Run from the repository root:

```powershell
./gradlew.bat :versions:mc26_1_2:build --console=plain
```

For focused iteration, select the relevant test class from the code map. Example:

```powershell
./gradlew.bat :versions:mc26_1_2:test --tests "com.github.beng420.kung.feature.dungeon.DungeonStaticChestTest" --console=plain
```

JUnit XML results are under `versions/mc26_1_2/build/test-results/test`; the HTML
report is `versions/mc26_1_2/build/reports/tests/test/index.html`. The build JAR is
under `versions/mc26_1_2/build/libs`, with the version from `gradle.properties`.
Finish code changes with the full active-module build. Documentation-only changes
need path/command/content checks, not a build or version bump.

Gradle may need downloads and access to its user cache. In a restricted execution
environment, request the required tool permissions rather than repeatedly running
a build already known to be blocked. A build does not install its JAR. When installation
is requested, ensure the target game is stopped before replacing the mod.

## Diagnostics

`KungDebugRecorder` keeps an in-memory trace ring buffer. After reproducing an issue:

- `/kung log save`: saves a trace and prints its exact path.
- `/kung log tail <lines>`, `/kung log copy`, `/kung log clear`: inspect, copy or reset.
- `/kung instance`: current shared location/server/floor context.
- `/kung room debug` or Dungeon Map > Room Debug: display recognition evidence.
- `/kung roomdata`: copy cell coordinates, core/stable/first hash, hints and match.
- `/kung mimic`: current static-chest status; capture/undo details in
  [MIMIC_STATIC_CHESTS.md](MIMIC_STATIC_CHESTS.md).

Current play profile:
`%APPDATA%/ModrinthApp/profiles/Dungeons 26.1.2/logs/kung`.
Before the first 0.2.16 startup, saved traces remain in that profile's `kung-debug`.
Older supplied traces also exist under `Here We Go Again (2)/kung-debug`.
Prefer the exact user-supplied path and time over scanning every saved log.

| Symptom | Trace to inspect first |
| --- | --- |
| Door late, room/checkmark changes | `map-change`, `map-check` |
| Wrong teammate, white class border or skin | `player-slots`, `player-markers` |
| Secrets, score, target mismatch | `score-calc` |
| Extra summary players / missing personal secrets | `run-statistics` summary roster/count/source/API state, `player-slots`, `player-stats` API outcomes; include the five actual names |
| Superpairs reveals or counts | `superpairs` |
| Missing overlays or resets | Instance/lifecycle events around the supplied time |
| Split loss | `dungeon-splits`: real/ideal clocks, `phaseStartTicks`, `phaseTicks`, `totalTicks`, exact `boundary`, `start-candidate`, `receivedTicks` and `ignoredBundledPings` |

For split comparisons, save both overlays and `/kung log save` immediately after
the run. Since 0.2.19, full saves retain the last 128 split records in
addition to the general ring, including countdown/Mort markers and phase boundaries.
A separate boss-entry save is no longer necessary to preserve one run's split
evidence. Do not clear the trace during the run. The diagnostics are automatic
and need no extra chat debug. See [split timing](SPLITS_TIMING_FIXES.md).

Chat diagnostics belong behind Debug > Debug Messages. The passive ring buffer is
separate. Existing throttled dungeon runtime errors should remain visible without
flooding chat. Optional title debugging: `/kung test title <doors>` and
`/kung test dungeonstart <doors>`.

## Changing code and comments

Use typed config categories, feature/service registries, command groups and shared
message/UI controls. Keep player-visible text English. Preserve existing config
migrations, master toggles and HUD layout unless the task changes their behavior.

Comment the reason for a non-obvious rule next to the code that owns it. Good
examples already exist in `ServerTickSequence.accept` (why zero cannot reset
deduplication), `HypixelInstanceTracker.tick` (why sidebar packets are coalesced),
and `DungeonRoomTransform` (coordinate origin and absolute Y). For caches, document
what invalidates them; for clocks and geometry, state the units. Avoid repeating
method names in prose or adding comments to every obvious statement.

For a behavior-heavy refactor, preserve behavior with focused regression coverage.
Do not add tests that merely mirror a trivial implementation or build just to
validate Markdown. Keep room-content changes and architecture refactors distinct.

## Keep documentation useful

- `AGENTS.md`: short, durable project rules and navigation.
- `AI_HANDOFF.md`: only current version, last validation and immediate open checks.
- `CODE_MAP.md`: feature ownership, test names and links; update when classes move.
- Topic documents: current contracts, pitfalls, evidence and live limitations.
- Dated reports / archived handoffs: history, read only for a related investigation.
- `FOUNDATION_TODO.md`: older proposals to reassess, not an automatic work order.

Update an existing topic instead of duplicating the same explanation across the
handoff, README and source comments. Keep historical validation counts scoped to
their reports. Distinguish a proposed fix, implemented behavior, passing tests and
a live result; none of those automatically proves the next.

Room formats, Wiki authority, local overrides and audit commands live in
[ROOM_DATA.md](ROOM_DATA.md). Normal builds never run `syncLocalDungeonRoomData`.
Confirmed static-chest templates are shipped inside the JAR. New explicit captures
stay in memory and can be copied with `/kung mimic copy` for review and bundling;
do not import arbitrary profile data during cleanup.

All runtime paths and legacy-folder migration are documented in
[FILE_STORAGE.md](FILE_STORAGE.md). New write paths must remain below the Kung
config or log directory, apart from installing the mod JAR.
