# Stella boss maps — 2026-09-12

Active module: Minecraft 26.1.2 (initial boss-map integration: 0.2.11,
Necron calibration: 0.2.13, boss visibility toggle: 0.3.3).

The user requested the exact boss maps used by Stella. Kung now bundles the
original twelve PNGs and `imagedata.json` from Stella's asset repository, with the
Necron calibration correction described below.
The **Boss Map** setting controls the entire map HUD during the boss phase.
With it disabled, both arena and ordinary clear-map rendering are hidden, including
footer and legend. Clear-phase rendering remains available. With it enabled, the
map area uses the matching arena. HUD positioning, scale, text scale, inventory
dimming and dungeon-instance ownership are preserved.

## Source and attribution

- [Stella assets](https://github.com/Eclipse-5214/ether/tree/30a1b4bcdd82ef4b2e1ac45bf6b981ff564b900c/stellanav),
  pinned commit `30a1b4bcdd82ef4b2e1ac45bf6b981ff564b900c`.
- [Stella code](https://github.com/Eclipse-5214/stella/tree/6ddec7c7a957781749b020f006d6f38809eb44ff),
  pinned commit `6ddec7c7a957781749b020f006d6f38809eb44ff`.
- Ether's README credits **BetterMap** for the artwork. The PNGs also match
  [BetterMap's originals](https://github.com/BetterMap/BetterMap/tree/b4b24512f99cbe3e8febfc2e30e6d7f793b4663f/assets).
- Every PNG and the JSON matched the pinned Ether Git blob at import. The record is in
  [reference/stella-boss-map-blobs.json](reference/stella-boss-map-blobs.json).
  Original metadata SHA256: `743BC494EF4CABFD6CAAAC767F0113ACC75C54AC60EDD26E6B90B5E141D73922`.
  The reference now records the one local JSON correction; all PNGs remain original.
- Attribution is also packaged beside the assets in `CREDITS.txt`. Ether and
  BetterMap's inspected trees supply no separate artwork license. Stella's
  code/project license labels are not assigned to the images.

## Selection and projection

`DungeonBossMapCatalog` loads the metadata once from the JAR. There are no runtime
downloads, per-frame disk checks, or extra world scans. The first inclusive XYZ
match for the known floor wins. Normal and Master floors share the same table.
Entrance and unknown floors have no boss map.

| Floor | Images |
| --- | --- |
| F1/M1–F6/M6 | One arena image per floor |
| F7/M7 | Maxor, Storm, Terminals/Goldor, Necron, Dragons, reward room |

The original 128×128 viewport follows the player and clamps at image edges.
`renderSize=64` controls the terminal-area zoom. Texture proportions, world sizes
and origins follow the upstream formulas, including unusual original metadata:
F4/F5 origins differ from their bounds and some images' world aspect ratios
differ from their pixel dimensions. Do not silently recalibrate those values.

### Necron calibration correction (0.2.13)

The user's screenshot shows the marker southeast of the island while standing
at its center. `f7_boss_s4` has square bounds X `[-3,111]`, Z `[19,133]` and a
square image centered on the island, but upstream `heightInWorld` was `94`.
Because the projection uses the smaller world dimension for both axes, the
center `(54,76)` mapped to `(77.62,77.62)` rather than `(64,64)` in the 128-pixel
viewport. This reproduces the screenshot's diagonal error.

Only that image's `heightInWorld` is corrected to `114`. Its center and both
arena edges now align with the original image. This is a scale calibration,
not a constant marker offset; it applies to self and teammates across F7/M7.
Other floors/phases, arena selection, images and rendering formulas are unchanged.

`DungeonBossMapSelection` is presentation state, separate from instance detection.
It requires packet-confirmed Catacombs context and an applied position. Some
arena bounds overlap the coarse clear-grid rectangle, so initial selection there
requires existing boss-phase evidence. A matching arena outside that rectangle
can establish the view immediately on teleport, even before its dialogue.
The selected arena survives fractional gaps between F7's integer Y bands. A
return to the clear grid outside every arena restores the clear map; instance
epoch/floor changes discard selection. With Boss Map enabled, run completion does
not hide the map. With it disabled, boss-phase evidence keeps the HUD hidden after
completion until instance reset. Visibility uses either the retained boss-phase
signal or the selected arena, so early teleports and fractional layer gaps cannot
fall back to the clear map. Phase evidence still works when arena assets are missing.
Changing the toggle during the boss phase takes effect on the next rendered frame.
The `dungeon-boss-map` trace records image/floor/epoch/position only when the view
or instance changes.

Boss markers retain dungeon player-slot identity and use interpolated, loaded
world entities. Other players must lie within the displayed arena's XYZ bounds.
Clear-map decorations and old clear-map marker positions are not reused here.
An unloaded teammate has no current boss position available and is omitted,
matching Stella's entity-based boss rendering.

## Validation

The 0.3.3 regressions cover the full HUD visibility decision during clear, boss
entry, completion, missing arena assets, early teleports, layer gaps, live toggles
and the next instance. The visibility check runs before drawing the grid, footer
or legend. The requested live check is Boss Map off/on during an actual boss run.

The 0.2.13 full build passed with **244 tests, zero failures/errors/skips**.
The added Necron regression checks the center and all four arena edges against
the image coordinates. The packaged JAR was compared with 0.2.12: all 12 PNGs
are identical and the only calibration difference is Necron's height `94 -> 114`.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.13.jar`. The corrected
position has not yet been tested in a live run.

The initial 0.2.11 active-module build passed with **235 tests, zero failures/errors/skips**,
including 15 focused boss-map tests. All 13 imported asset files were rechecked
against their upstream Git blobs; the final JAR contains all 12 PNGs, metadata and
credits. F1 and terminal-map images were visually inspected.

Focused coverage checks original PNG dimensions, floor and layer selection,
inclusive bounds, reward-room priority, viewport clamping, marker coordinates,
false early selection in overlapping F4 bounds, fractional-height transitions,
instance reset and post-completion retention. Live Minecraft rendering remains
to be checked in an actual boss arena.
