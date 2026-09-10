# Kung 26.1.2 Foundation Todo

This is the implementation queue for future chats, AIs, and maintainers. Work only in
`versions/mc26_1_2` unless the user explicitly requests another version.

## Goal

Make new features small, modular, testable, and consistent without rewriting the working
dungeon room database. Preserve current player-visible behavior unless a task explicitly
calls for a behavior change.

## Working Rules

- Read `docs/AI_HANDOFF.md` before changing dungeon behavior.
- Keep feature master toggles disabled by default.
- Keep all player-visible UI, chat, command, status, and error text in English.
- Prefer compatibility facades and incremental migrations over project-wide rewrites.
- Add characterization tests before splitting large behavior-heavy classes.
- Do not mix database content changes with architecture changes.
- Keep the full `:versions:mc26_1_2:build` green after every completed phase.

## P0 - Mimic ESP

- [ ] Add Mimic ESP as a dungeon feature for 26.1.2.
- [ ] Detect Mimic state from reliable dungeon signals without requiring the Dungeon Map overlay to be enabled.
- [ ] Keep rendering isolated behind a feature toggle and reuse shared HUD/message/rendering utilities.
- [ ] Add tests or a small harness for detection state transitions before polishing visuals.

## P0 - Wish Reminder

- [ ] Add Wish Reminder as a dungeon feature for 26.1.2.
- [ ] Detect low-health party members from reliable dungeon player/team health signals without requiring the Dungeon
  Map overlay to be enabled.
- [ ] Show a clear on-screen alert when a player is low and Wish should be used.
- [ ] Play a configurable warning sound for the alert, with cooldown/debounce so it does not spam.
- [ ] Recreate SkyHanni-style behavior and UX, but implement it in Kung's own feature/config/HUD/message structure
  instead of copying external code directly.
- [ ] Add focused tests or a small harness for low-health threshold, cooldown, self/teammate filtering, and reset on
  dungeon end.

## P0 - Shared Presentation

- [x] Add a central message builder with `INFO`, `SUCCESS`, `WARNING`, `ERROR`, and `DEBUG` styles.
- [x] Keep `KungChat` as a compatibility facade during migration, then remove it after the last caller.
- [x] Migrate Updater and Room Sync player messages to the central message API.
- [x] Migrate `/kung` command feedback to the central message API.
- [x] Migrate remaining feature chat output and remove direct Kung-owned `Component.literal(...)` messages.
- [x] Separate local mod messages from outgoing Hypixel party/guild/public chat messages.
- [x] Add shared semantic `UiTheme` palettes and use them in both large screens.
- [x] Add shared spacing and text-style primitives to the UI theme layer.
- [x] Extract shared `UiBounds` geometry and use it for click regions in both large screens.
- [x] Extract a reusable `UiTextField` and use it in both large screens.
- [x] Extract reusable `Toggle`, `NumberField`, `ScrollList`, and tooltip controls.
- [x] Extract a reusable HUD text-block renderer with alignment, scale, background, and line spacing.
- [x] Move the private settings entry models out of `KungConfigScreen` once both settings screens can consume them.

## P1 - Runtime And Features

- [x] Introduce a small `AppServices` runtime object containing config, messages, and shared trackers.
- [x] Pass required services explicitly to new features instead of adding more global lookups.
- [x] Register long-lived trackers/services and user-facing features through clearly separate registries.
- [x] Define lifecycle semantics: initialize once, enabled state, reset, and shutdown hooks.
- [x] Reclassify Blood Rush as a client service because it is consumed by dungeon tracking.
- [x] Make config category state private after all direct field writes have been migrated to setters.
- [x] Remove unused compatibility methods after all callers and saved-config migrations are covered.

## P1 - Dungeon Boundaries

- [x] Add characterization tests for dungeon start/end/context-loss transitions.
- [x] Add characterization tests for chat parsing, score calculation, and room matching.
- [x] Put the finished room catalog behind a narrow `DungeonRoomRepository` facade.
- [x] Split event collection and lifecycle transitions out of `DungeonStateTracker`.
- [x] Split score calculation, player statistics, API enrichment, and announcements out of `DungeonRunStats`.
- [x] Separate live-map data collection from diagnostic file rendering in `DungeonLiveMapWriter`.
- [x] Keep room data formats and matching thresholds unchanged during these extractions.

## P0 - Demand-Driven Runtime

- [ ] Separate lightweight context tracking from feature-specific dungeon processing.
- [ ] Keep instance, party, and dungeon lifecycle detection available while individual features are disabled.
- [ ] Define explicit runtime capabilities such as `DUNGEON_CONTEXT`, `PARTY_CONTEXT`, `ROOM_SCAN`,
  `DOOR_SCAN`, `MAP_DATA`, `RUN_STATS`, and `ROOM_SYNC`.
- [ ] Let enabled features declare their required capabilities and compute the active workload from their union.
- [ ] Gate world scans, map reads, room matching, player/stat processing, and room sync independently.
- [ ] Stop all world scanning and dungeon calculations when no enabled feature requires them; registered callbacks
  should return immediately without world iteration, network requests, or file work.
- [ ] Support hot activation during an existing run: enabling the Dungeon Map after joining must immediately
  rebuild its current state from the world, dungeon map item, lifecycle context, and already tracked party members.
- [ ] Do not require historical map scans for hot activation; the first scan must produce a usable current snapshot.
- [ ] Release feature-specific transient state when its last consumer is disabled without discarding shared instance,
  party, or dungeon lifecycle context.
- [ ] Add characterization tests for all-disabled idle behavior, Map-only, Blood-Rush-only, Splits-only, enabling
  the Map mid-run after 20 seconds, and disabling the final scan consumer mid-run.

## P0 - Blood Rush Helper Reliability

- [x] Treat scanned Wither/Blood door block transitions as the authoritative trigger for progress titles.
- [x] Count every newly observed non-start Wither/Blood door as part of the conservative lower bound.
- [x] Show a progress title every time a tracked Wither/Blood door opens, even when the rendered path estimate has
  not changed yet.
- [x] Keep the `+` suffix while the helper only knows a lower bound, for example `2+ doors`.
- [x] Drop the `+` only when all map-visible rooms are recognized or when an uninterrupted Start-to-Blood path is
  visible from the current map data.
- [x] Add focused tests for locked door transitions, lower-bound counting, and exact-count readiness.
- [ ] Add a small title-output harness so repeated door opening title decisions are tested without a live Minecraft GUI.

## P1 - Menu And Utility Cleanup

- [x] Keep HUD position and scale editing out of Mod Menu; use `/kung hud` for those settings.
- [x] Add `Close Only On Change` for Loadouts Auto Close.
- [x] Split `!c50` and `!ca50` into separate command toggles.
- [x] Add `!tps` with separate party, guild, all-chat, and DM toggles.

## P2 - Commands And Screens

- [x] Split `KungCommands` into command groups with one root registration class.
- [x] Keep room-learning commands isolated from ordinary user commands.
- [x] Move screen state and validation out of rendering methods.
- [x] Convert `KungConfigScreen` to shared controls without changing its current layout.
- [x] Convert `CatacombsCalculatorScreen` to the same controls.
- [x] Add focused tests for control validation and settings model generation.

## Foundation Definition Of Done

- [x] A normal feature needs one feature class, one typed config section, and one registry entry.
- [x] Features can be unit-tested without initializing the full Minecraft client.
- [x] Kung-owned local chat output uses the central message API.
- [x] Screens and overlays share text, field, spacing, and color primitives.
- [x] Dungeon database consumers depend on a narrow facade rather than storage details.
- [x] Large dungeon classes have named responsibilities protected by focused tests.
- [x] Config migration tests and the full 26.1.2 build pass.
- [ ] Disabled features cause no feature-specific background processing, while shared context remains ready for
  correct mid-session activation.

## Current Status

The September 10 dungeon hardening pass added bounded room/Mimic scans, consumer
gates, cached room matching and Blood Rush paths, preload transition tests, and
pure door-estimate/wording tests. See `docs/DUNGEON_FIXES.md`. The capability queue
above still needs broader end-to-end activation and lifecycle tests before every
item can be marked complete.

The original foundation queue is complete for `mc26_1_2`, but the demand-driven runtime work above
is still required before the foundation should be considered finished. Continue adding features through
the typed config categories, lifecycle registries, shared message/UI primitives, command groups, and
dungeon repository/service boundaries established here. Treat new database content and player-visible
behavior changes as separate follow-up work.
