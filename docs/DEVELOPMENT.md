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

### VS Code dependency import errors

If Gradle compiles the tests but VS Code cannot resolve `org.polyfrost` or `kotlin`,
check the Java language server's imported classpath. The Gradle Build Server can
retain an older dependency list after an import failure. This workspace uses these
local, gitignored `.vscode/settings.json` overrides:

```json
"java.gradle.buildServer.enabled": "off",
"java.import.gradle.annotationProcessing.enabled": false
```

The first selects the standard Java Gradle importer. The second avoids its
[Gradle 9 parallel-import bug](https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3807);
the active module has no annotation processors. These settings do not disable Java
diagnostics or change Gradle builds. Run **Java: Clean Java Language Server Workspace**
and choose **Reload and delete** to rebuild an existing IDE cache. Keep test
dependencies in the active module's Gradle file.

## Diagnostics

`KungDebugRecorder` keeps an in-memory trace ring buffer. After reproducing an issue:

- `/kung log save`: saves a trace and prints its exact path.
- `/kung log tail <lines>`, `/kung log copy`, `/kung log clear`: inspect, copy or reset.
- `/kung instance`: current shared location/server/floor context.
- `/kung room debug` or Dungeon Map > Room Debug: display recognition evidence.
- `/kung roomdata`: copy cell coordinates, core/stable/first hash, hints and match.
- `/kung mimic`: current static-chest status; capture/undo details in
  [MIMIC_STATIC_CHESTS.md](MIMIC_STATIC_CHESTS.md).
- Beng114 only: `/kung dev dragons on|off|sample|copy` controls the bounded,
  in-memory M7 measurement capture and clipboard export. Enable before spawning;
  see [M7 Dragon Helper](M7_DRAGONS.md#developer-measurements). Existing public
  diagnostic commands remain available.

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
| Mimic late / marker / missing kill announcement | `mimic-discovery` index/fallback source and prior scan ticks, `mimic-candidates` positions/rooms, `mimic-kill` death packet/evidence/source/switches, `mimic-esp`, `mimic-static`; save immediately after the late marker or kill |
| Missing overlays or resets | Instance/lifecycle events around the supplied time |
| Split loss | `dungeon-splits`: real/ideal clocks, `phaseStartTicks`, `phaseTicks`, `totalTicks`, exact `boundary`, `start-candidate`, `receivedTicks` and `ignoredBundledPings` |
| Missing split prediction / final-phase PB | `dungeon-splits` run-start/end: `pbFloor`, `pbTracking`, `predictionMode`, `pbKnown`, `pbMissing`; `run-victory-confirmed` after score-before-victory completion |

For split comparisons, save both overlays and `/kung log save` immediately after
the run. Since 0.2.19, full saves retain the last 128 split records in
addition to the general ring, including countdown/Mort markers and phase boundaries.
A separate boss-entry save is no longer necessary to preserve one run's split
evidence. Do not clear the trace during the run. The diagnostics are automatic
and need no extra chat debug. See [split timing](SPLITS_TIMING_FIXES.md).

Full saves also retain the last 256 `score-calc`, `score-bonus` and `mimic-kill`
records separately, so map traffic cannot discard early bonus inputs. Recognized
bonus chat reports are recorded even when another source already set the flag;
overlapping reports do not add score. A separate 128-record reserve retains the
first nonempty hash per room cell (`map-change initial-room-point`); those at-most-36
events per instance bypass noisy map rate limits. Save and copy merge all reserves
chronologically without duplicates. Explicit line limits, including `/kung log tail`,
still return at most that many event lines. See [score evidence](RUN_STATISTICS.md#four-point-bonus-gap--2026-09-19-1233-trace).

Chat diagnostics belong behind Debug > Debug Messages. The passive ring buffer is
separate. Existing throttled dungeon runtime errors should remain visible without
flooding chat. Optional title debugging: `/kung test title <doors>` and
`/kung test dungeonstart <doors>`.

## Changing code and comments

Use typed config categories, feature/service registries, command groups and shared
message/UI controls. Keep player-visible text English. Preserve existing config
migrations, master toggles and HUD layout unless the task changes their behavior.

Local informational text uses `KungMessages.highlight` through `info`/`detail`:
gray prose, gold numbers, green/red states, colored dragon names and light-blue
Ice Spray/Sprayed labels. Text, commands and paths are preserved. Warning/error/
success messages retain their severity color. Components that already have rich
styles should remain components; do not flatten them through the string helper.
Kung's menu uses accent category headings, muted setting labels and gold numeric
values. Feature names remain uniformly white, including Ice Spray Highlight;
do not apply chat/HUD semantic highlighting to menu feature rows.
Native OneConfig owns its own text rendering and colors.

### Optional OneConfig / Mod Menu integration

`config/KungSettings` is the shared catalog for the existing `KungConfigScreen`
and native OneConfig controls. It exposes six categories and 28 feature entries,
including enum choices, nested settings, actions, help and raw loadout bindings.
Hitboxes uses dynamic entity rows and shared color/removal
controls. Its native Add/Edit buttons open the complete Kung search/color editor;
see [Hitboxes](HITBOXES.md).
Bow Draw Indicator uses the same dynamic list pattern with tick steppers and
removal controls. OneConfig supplies native numbers; membership edits open the
updated Kung list. See [Bow Draw Indicator](BOW_DRAW_INDICATOR.md).
Keep new settings in this catalog so both menus reach the same validated setters.

`compat/KungModMenu` retains the standard `modmenu` screen factory. When OneConfig
is present, that factory lazily registers `KungOneConfigTree` under `kung` before
returning. OneConfig **1.2.0** recognizes the native tree and opens its own settings
page instead of the Kung screen; its published standalone Mod Menu API shim does
this too, without a separate Mod Menu install. The native tree has no `on_click`
screen redirect or `ui_only` flag. Flat property IDs contain no path separators.
Category/subcategory metadata groups the native page, and ancestor labels retain
nested settings (OneConfig only renders one tree nesting level). Each property also
has `searchTags` for Kung, its category and feature: OneConfig 1.2.0's global search
does not match category/subcategory headings, so those labels alone cannot make
generic controls such as `Enabled` or `Volume` discoverable by feature name.

Both APIs are compile-only and are not bundled or required by Kung. All OneConfig
class references stay behind optional-mod checks. `KungOneConfigBridge` also registers
the native settings and HUD wrappers at `CLIENT_STARTED`, after OneConfig initializes;
without OneConfig, neither native integration class is loaded. Incompatible optional API versions
log a warning and retain the existing screen factory. `/kung`, standalone Mod Menu
and `/kung hud` continue to open Kung's existing screens. Escape retains the caller;
the explicit changelog action also returns to OneConfig when its Kung dialog closes.

OneConfig properties delegate to Kung's existing getters and setters. `custom_save`
bypasses OneConfig loading/writing a second Kung config. Setters ignore OneConfig's
profile-rebinding defaults pass, so its profiles cannot reset shared Kung values.
Explicit resets use a fresh Kung defaults catalog. Per-file audio controls absent
from that defaults catalog reset to 1x. Display units are seconds, percent or audio
multipliers rather than their stored tenths/hundredths. Child-toggle dependencies
refresh after edits. Loadout keybinds use native single-input capture, but opt out
of OneConfig global binding/Minecraft-control registration; Kung retains execution
ownership in the loadout menu. Existing scan codes survive unchanged-key edits.

Custom audio file-list, hitbox and bow-threshold membership changes rebuild a fresh native tree after leaving OneConfig,
at most once per second, refreshing search and file-specific controls without
interrupting text entry. HUD panels retain and refresh their own linked controls
across that rebuild. Dynamic status text may need reopening the page because
OneConfig caches its UI values.

`KungHudLayout` supplies the same seven HUD bounds and config setters to `/kung hud`
and the optional OneConfig HUD editor. Native wrappers use GUI coordinates, subtract
the map/Superpairs border offset when moving, and convert scale multipliers to
Kung's 25–300% range. Map text scale remains independent (50–200%) in its native HUD
settings. Each HUD links its native feature settings; visibility controls the original
feature toggle. Registration and previews never enable a feature. Disabled HUDs
appear on the canvas with OneConfig's disabled styling; these single-instance wrappers
are existing canvas entries, not additional copies in the Add library.

Kung retains placement ownership (`ownsPlacement=true`) and profile-rebinding guards;
position, scale and visibility setters save through Kung even for spinner/resize edits
that do not trigger OneConfig's drag-end callback. The editor redraws the existing
Splits, Feast and Safari previews above its background. Map and Superpairs use the same
safe labeled bounds as `/kung hud`, without creating dungeon/container observations.
Normal HUD drawing pauses while those external previews render. Gameplay appearance
and the existing standalone editor layout remain unchanged. OneConfig 1.2.0 can only
edit external HUD placement in a loaded world, not from the title screen.

Live validation still needs native opening/search, all control kinds, persistence,
profile switches, audio-list refresh, title-screen settings access, optional-mod absence,
and all seven HUDs' drag/resize/visibility, map text scale and switching between editors.

`KungSettingsTest` checks catalog coverage and persistence shared with the original
menu. `KungOneConfigTreeTest` exercises the released OneConfig API for native
control coverage, guarded writes, units/defaults, dependencies, keys, actions and
replacement of the old launcher card without additional config-file I/O.
`KungHudLayoutTest` and `KungOneConfigHudTest` cover offset-aware placement, live
bounds, scale limits, shared values, default-off visibility and guarded native writes.
An isolated class-loader test also loads the client entrypoints while both optional
APIs are unavailable; this does not replace a full standalone game-start check.

API references: [Mod Menu](https://github.com/TerraformersMC/ModMenu/tree/26.1#java-api),
[OneConfig with Mod Menu](https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/main/kotlin/org/polyfrost/oneconfig/internal/compat/ModMenuCompat.kt),
[OneConfig's standalone API bridge](https://github.com/Polyfrost/OneConfig/blob/v1/minecraft/src/modMenuShim/java/org/polyfrost/oneconfig/internal/compat/ModMenuApiCompat.java).

### Menu layout and shared controls

`KungConfigScreen` renders its menu at 80% of Minecraft's selected GUI scale.
Feature columns are 171 logical units wide (10% narrower than the former 190);
their controls are proportionally narrower. The title, search, tooltips, text
editor and patch-notes modal share this menu scale. Layout/scroll limits use the
inverse-scaled viewport, keeping the title and search centered. Minecraft already
delivers mouse events in GUI units: convert positions and text-selection drag
distances once into menu coordinates. Keep `Screen.width`/`height` and the global
GUI scale intact, and restore the drawing transform before external screen hooks.
HUDs and the standalone update toast retain their own scale. Live checks should
cover clicks, slider dragging, category/horizontal scrolling, text selection and
modal buttons at multiple GUI scales and window sizes.

The current menu follows the supplied Odin reference with opaque charcoal panels,
rounded corners, soft shadows, 12-unit column gaps and a 220-unit rounded search
field raised 80 units from the bottom. Column scroll limits reserve space for it.
`UiShapes` draws small curves with bounded strips and edge coverage. `UiTheme.menu()`
is local to settings and the changelog modal; the shared HUD theme is unchanged.
`UiMenuFont` uses the normal font provider through `FontAccessor`, replacing only
the default font with `kung:menu` for this menu. Rendering, measurements, wrapping
and text input use that same font; resource reloads still resolve through Minecraft.
The menu font disables text shadows and retains the vanilla fallback for symbols.
`UiMenuFontTest` checks provider isolation, Minecraft's provider codec, referenced
TTF loading, glyph coverage, the fallback and the bundled license. Resource IDs
and filenames must use lowercase: a single invalid provider ID rejects the whole
menu font, including its fallback. Live rendering/resource reload remains open.

Inter is included unmodified from the [Google Fonts distribution](https://github.com/google/fonts/tree/main/ofl/inter)
(downloaded 2026-09-13) under the SIL Open Font License. Its license is packaged as
`assets/kung/font/inter-ofl.txt`; `inter.ttf` has SHA-256
`29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`.
The TTF provider uses size 12 and 3× oversampling; the overall menu scale stays 80%.
The 2026-09-14 17:07:37 user trace identifies the affected settings openings;
the same profile's `logs/latest.log` reports `kung:Inter.ttf` as an invalid resource
ID and rejects `kung:menu` on every reload. The supplied screenshot shows missing
glyph boxes throughout Kung settings. Both font/license filenames and the JSON
reference now use lowercase. The new Minecraft-codec regression reproduced the
exact parsing failure before the fix; the earlier AWT-only check could not detect
Minecraft resource-name restrictions. A live check with the corrected JAR remains open.
Feature and setting help belongs in optional `withTooltip(...)` metadata rather
than permanent info rows when the information is only needed on demand.
`UiHoverDelay` waits two seconds on the same entry; clicks, scrolling, leaving
or modal/editor interaction reset the timer. Full clipped names also use this
delay. `UiTooltip` wraps text and positions the panel within menu coordinates,
including the menu's 80% scale. `UiControlModelTest` covers timing and target resets;
live wrapping, edge placement and GUI-scale behavior remain UI checks.

The updater feature is pinned open through `FeatureEntry.alwaysExpanded`, shared
by drawing and content/scroll sizing, with an explicit right-click collapse guard.

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
