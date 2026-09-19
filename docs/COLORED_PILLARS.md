# Colored F7/M7 Pillars

Active module: Minecraft 26.1.2.

**Dungeon > Colored F7/M7 Pillars** defaults off. **Material** offers Wool
(default), Glass and Terracotta in both the Kung menu and native OneConfig.
The toggle and material persist in the existing `dungeonMap` config section;
missing, null or unknown material values fall back to Wool.

Rendering requires packet-confirmed Catacombs context and floor 7, covering
both F7 and M7. When structured floor metadata is unknown, original server
dialogue from Maxor, Storm, Goldor or Necron can confirm the arena for this
instance. Explicit other-floor metadata still wins. The fallback does not set
shared floor/master-mode metadata and is independent of Dungeon Map, Boss Map
and Splits settings. Only matching blocks inside Storm's four columns can
change appearance; no phase timer controls the replacement.

## Bounds and colors

All coordinates are inclusive. Each column is 7 x 38 x 7 blocks, from Y=169
through Y=206. The arena floor at Y=168 is excluded.

| Pillar | Center X/Z | X range | Z range | Minecraft color |
| --- | --- | --- | --- | --- |
| Green | 46 / 41 | 43–49 | 38–44 | Lime |
| Yellow | 46 / 65 | 43–49 | 62–68 | Yellow |
| Purple | 100 / 65 | 97–103 | 62–68 | Purple |
| Red | 100 / 41 | 97–103 | 38–44 | Red |

Only `DIORITE` and `POLISHED_DIORITE` render as the selected colored wool,
stained glass or terracotta. Air, slabs and other blocks retain their original
state, including air received during a crush.

Geometry follows the pinned
[OdinLegacy pillar implementation](https://github.com/odtheking/OdinLegacy/blob/bfe1b391f7acb11e51241287b7086527f6bf00a2/odinclient/src/main/kotlin/me/odinclient/features/impl/floor7/FuckDiorite.kt).
The color arrangement also agrees with the bundled
[Storm boss-map image](../versions/mc26_1_2/src/main/resources/assets/kung/textures/dungeon/boss/f7_boss_s2.png)
and its [map metadata](../versions/mc26_1_2/src/main/resources/assets/kung/textures/dungeon/boss/imagedata.json).
The legacy source checked `Blocks.stone`, which included all stone variants.
Kung's modern diorite-only predicate is inferred from that feature's intent;
complete live pillar coverage still needs confirmation.

## Rendering contract

`ColoredPillarsFeature` publishes the current level, instance epoch and selected
material once per client tick. Each vanilla `RenderSectionRegion` captures that
material when created; the optional Sodium `LevelSlice` captures it when its
render data is copied. Mesh workers use that snapshot instead of mutable config.
Sodium 0.9.2 is the compatibility target; Sodium is not required or bundled.

Both mixins substitute block states only when rendering snapshots are queried.
They do not write world blocks, change collision or feed replacements into room
scans. Server block updates remain authoritative. Toggle, material, level or
instance changes trigger one native terrain-renderer reload; unchanged context
does not rebuild terrain. This also discards in-flight initial Sodium meshes:
Sodium's regional dirty requests skip sections whose first mesh has not uploaded.
No world scans are added. Glass uses actual stained glass states for its transparent
rendering; the real world's light propagation remains unchanged.

## Validation and live checks

`ColoredPillarsFeatureTest` covers all colors/materials, inclusive bounds,
excluded floor/neighbor blocks, crush air and the F7/M7 context gate. It also
covers missing floor metadata, boss dialogue while the setting is disabled,
mid-boss material changes, explicit other floors, rejected actionbar/quoted
messages and invalidation on instance change/exit.
`KungSettingsTest` covers defaults, menu changes, persistence and fallback;
`KungOneConfigTreeTest` checks native menu reachability and choices.

Still verify in actual F7 and M7 runs: all three materials and all four pillars,
complete diorite coverage, crush and recovery animation, live toggle/material
changes, disconnect/instance transitions, and rendering with vanilla and
Sodium 0.9.2. Unit tests do not establish live rendering correctness.

## Missing floor metadata — 2026-09-19, 16:47:40

The supplied trace reports run-statistics floor `0` throughout clear, then `7`
immediately after Maxor's original message at 16:46:40.231. Storm's map appears
at 16:47:02.438. The profile enables pillars with `TERRACOTTA`, and its loaded
Kung 0.4.3 JAR contains the replacement hooks. Run statistics already had a
boss-dialogue fallback; the pillar gate required shared structured floor `7`
and had no fallback. The retained trace lacks `context-state` records, so it
does not directly establish the missing shared value or why entry metadata
was lost. The entry banner in `latest.log` is at 16:42:29; the run's dungeon
start is at 16:44:10. Timeout or display filtering remains unproven.

The pillar feature now observes original non-overlay `ALLOW_GAME` messages,
before display filtering, and retains boss evidence only for its instance
epoch. Leaving Catacombs or resetting discards it. Observing while disabled
allows later enablement without depending on another feature's workload.
`colored-pillars` trace events record boss confirmation and render-context
changes; no per-block logging or extra world scans are added.

Installed Sodium 0.9.2 bytecode confirms its meshing task calls the hooked
`LevelSlice.getBlockState(III)` with world coordinates and uses that result for
the model. Installed Firmament only wraps subsequent model lookup. There is
no evidence of a bypassed render hook, so both renderer mixins stay unchanged.
The missing-metadata regression and full build pass; live appearance still
requires a new run with the rebuilt JAR.
