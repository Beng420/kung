# Bow Draw Indicator

Util > **Bow Draw Indicator** is default off. While drawing a normal bow on
Hypixel it shows estimated launch power as a percentage and a colored bar.
There is no trajectory, distance estimate, automatic release or input change.
`/kung hud` and optional OneConfig share placement, scale (25–300%) and previews.

## Charge and markers

The bar and percentage use Minecraft's `BowItem.getPowerForTime`: with
`t = drawTicks / 20`, power is `min(1, (t*t + 2*t) / 3)`. This is launch strength,
not damage percentage. It increases on every server tick rather than jumping
between a few fixed strengths. Markers use the same power curve as the fill.
The default list contains the two gameplay boundaries:

| Marker | Meaning |
| --- | --- |
| 3t | First tick that meets the minimum 0.1 power needed to fire |
| 20t | Full power; further holding does not increase it |

The former 13t/18t defaults were only visual model stages, not ballistic
breakpoints, and are no longer added automatically. The mechanics were checked
against the local Minecraft 26.1.2 `BowItem` bytecode
and `assets/minecraft/items/bow.json` from the merged game JAR.

Right-click the feature in `/kung` to expand its threshold list. **Add Threshold**
adds an unused value (10t when available). Each row has -/+ controls for 1–20
server ticks and a separate red removal button. Up to 20 entries are supported;
all may be removed. Entries persist in `misc.bowDrawThresholds` in Kung's existing
config. Missing/null lists get 3t/20t; explicitly empty lists stay empty. Null
entries are removed, loaded values are clamped, and lists are capped at 20.
Duplicate values share one HUD line. Every distinct marker is drawn, with only
overlapping labels omitted. Previews use the same current list.

OneConfig uses native tick-number and removal controls. Add/remove opens the
updated Kung list, returning to the native screen on close, as with Hitboxes.
Rows retain object identity during tick edits; removed native/HUD controls cannot
accidentally modify a different row. Neither markers nor their removal affect
charge, the power formula or the red/yellow/green minimum/full-power colors.

## Timing and lifetime

`LocalPlayerMixin` observes the actual local `startUsingItem`/`stopUsingItem`
transitions, including starts outside the game-mode interaction return path.
`BowDrawIndicatorFeature` listens to the existing `DungeonServerTickEvents`
stream, which is published for standalone nonzero Hypixel ping ticks even
outside dungeons. The shared `ServerTickSequence` rejects duplicates, zero IDs
and bundle pings before dispatch. Client frames, client ticks and elapsed wall
time never add charge: at 10 TPS a full draw needs about two seconds, and a
server stall stops progress. Received batches advance by their accepted ticks.

The `~` marks an estimate: the server does not acknowledge the exact draw-start
tick, and network delay/jitter can shift observed charge relative to release.
No smoothed TPS extrapolation runs ahead through a server stall.

Release/canceled use stops tick accumulation immediately and holds the final
value as **Last Draw** for 200 ms. This keeps a draw shorter than one rendered
frame visible and avoids flashing off between rapid shots. Repeated stops do
not extend that interval. A new actual start immediately replaces it with zero,
including release/redraw within one client tick. Only display persistence uses
wall time; charge remains server-tick-only.

Held-item/hand/hotbar-slot/player/world changes, death, disconnect and disabling
clear active and held displays. Inventory packet replacements with another stack
of the same bow item retain charge, matching vanilla's `updatingUsingItem`
behavior; object identity alone is not a cancellation signal. Switching to a
shortbow still invalidates it. Enabling midway through a
draw waits for the next use instead of importing a client-tick charge count.
Shortbows identified by their Hypixel lore and crossbows are excluded.
Ordinary rendering is hidden in screens, with F1 and during external HUD editing.
Editor previews use illustrative 13t charge without enabling the feature.

## Responsiveness report — 2026-09-17

The user confirms the original HUD works but reports missing draws when spamming
the bow. The supplied 21:25:45 UTC trace contains 83 entries and no bow events,
so it does not identify which particular draw was missed. Code inspection found
the old reference-equality cancellation and immediate release hiding described
above; both are now regression-covered. The local player start/stop bytecode was
checked before moving the observer to that lifecycle.

`bow-draw` now records starts with hand/slot, stops with server ticks and held
milliseconds, and invalidations, capped at 80 records per minute. Save
`/kung log save` after a reproduction to inspect these events. No chat debug
switch or extra output in the HUD is needed.

## Validation and remaining live checks

`BowDrawProgressTest` covers filtered tick advancement, saturation, reset/redraw,
the charge curve/minimum firing threshold, and normal bow versus shortbow/item
selection, copied/damage-updated stacks, sub-frame draws, bounded release holds
and redraws without inherited charge. `BowDrawThresholdSettingsTest` covers
defaults, malformed/empty lists, bounds, live add/edit/remove, persistence and
stable row ownership. `KungSettingsTest` covers default-off behavior and shared
HUD placement. Native editable/removal controls and trace bounds have focused
regressions; existing tick/editor tests cover the reused routing and adapters.

Validation on 2026-09-17: 38 focused cases pass. The full Java 25
`:versions:mc26_1_2:build --console=plain` passes 661 cases (660 passed, one
Windows symlink skip, zero failures/errors), and `git diff --check` passes.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.3.5.jar`; no profile installed.

Live checks remain: repeated normal bow/Last Breath draws, reduced TPS/stalls,
release/cancel/rapid redraw, main/offhand and slot switches, lore exclusion of
shortbows, world transfers, custom marker readability, list add/edit/remove in
both menus and both HUD editors' drag/resize.
Tests do not establish live Hypixel release timing or Mixin application.
