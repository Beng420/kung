# Calculator fixes — 2026-09-12

Active module: `versions/mc26_1_2`.

## Wrong profile and obsolete GUI API

The screenshot's HTTP 403 came from the calculator screen's old PixelStats
endpoint. `/kung calculate cata`, `!c50`, and `!ca50` now use the same
`HypixelSkyBlockProfileClient.loadCalculatorPlayer` route: configured direct
Hypixel API, falling back to the current Adjectils profiles endpoint. Calculator
loading needs only profiles; an unrelated player-achievements request cannot
delay or block it. Dungeon run secret enrichment retains its separate API path.

The profile parser previously allowed an unselected profile later in the response
to overwrite `selected=true`. Modern responses do not include the old `last_save`
field used by that fallback. Selection now keeps the explicitly selected profile;
without a selection flag it uses the profile with the most dungeon XP. Missing
members, empty profile lists and unsuccessful responses are errors. A selected
profile without dungeon data is not treated as a fresh character with zero XP.

The public Salty_Whale response has selected **Watermelon**, followed by empty
**Raspberry**. The old choice reproduces the reported **2436 M7 runs** exactly.
The dungeon-only fixture is saved at
`versions/mc26_1_2/src/test/resources/catacombs/salty-whale-profiles.json`.

With Watermelon's actual XP and Adjectils' maximum accessory/shard presets:

| Mayor | Total M7 runs | Required classes |
| --- | ---: | --- |
| None | 1538 | 484 Archer, 514 Berserk, 540 Mage |
| Derpy | 1026 | Archer, Berserk, Mage |

The first row exactly reproduces the user's Adjectils output. With Derpy, equally
valid minimal allocations can differ by one run between classes because of
tie-breaking. Non-selected classes receive one quarter of their selected-class
XP, both in the current Adjectils source and the supplied game trace. This factor
was already correct. Class bonuses cap at 50% for Derpy/Aura as in Adjectils.

The GUI and chat now share the profile models, class XP formula and CA50 solver.
An older asynchronous GUI response cannot replace a newer request. Existing
local observed XP corrections for the local player remain in chat calculations.

## Catacombs Explorer

Adjectils adds **Catacombs Explorer** separately from the **Catacombs Expert Ring**:
each Explorer level adds 0.01 to the Catacombs XP multiplier, up to 0.10 at level
10. It is added once, before the global multiplier. It does not affect class XP.
The cata formula is now shared by the GUI and `!c50`.

The new GUI control uses the existing numeric-control style and accepts levels
0–10. When loading a profile, its level comes from
`member.attributes.stacks.catacombs_explorer`. These are cumulative syphoned
Bonzo shards, not levels. Bonzo is Epic, with cumulative thresholds
`1, 2, 4, 6, 9, 12, 16, 20, 25, 32`. Thus the fixture's 32 shards mean level 10.
An absent attribute block is unknown, while an existing empty stacks map means
level 0. Unknown data uses a visible `(preset)` label and level 10. The user can
adjust the control manually. `!c50` keeps the established maximum-bonus preset,
including Explorer 10, matching Adjectils' default settings.

At M7, Expert Ring, Hecatomb X and Explorer X, the projected Catacombs XP is
534,000 without a mayor bonus, 759,000 with Derpy, and 786,000 with Aura. This
follows the website's 300-score/max-completion projection, not every possible
run's actual reward. Tiny binary floating-point residues are removed before
rounding up, avoiding an accidental extra 1 XP on mathematically integral results.

The API now exposes shard counts even though Adjectils' current source still
hardcodes Explorer/Graduate to their maximum values. Do not infer a level by
clamping the shard count to 10. Do not copy SkyCrypt's current level helper
without checking whether its table is incremental or cumulative.

## Sources and validation

Sources read on 2026-09-12:

- [Adjectils dungeon calculator and its inline formulas](https://adjectils.com/dungeon.html).
- [Adjectils API client and profile selection](https://adjectils.com/apireader.js).
- [NEU attribute constants](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/master/constants/attribute_shards.json): Bonzo identity, Epic rarity and incremental costs.
- [SkyCrypt attribute processing](https://github.com/SkyCryptWebsite/SkyCrypt-Backend/blob/dev/src/stats/attribute_shards.go) and [attribute ID conversion](https://github.com/SkyCryptWebsite/SkyCrypt-Backend/blob/dev/src/stats/neu/attribute_shards.go): API stacks are syphoned counts.
- [Wiki attribute leveling table](https://hypixelskyblock.minecraft.wiki/w/Attributes#Leveling): independent cumulative cost comparison.

All **13 `CatacombsCalculatorTest` tests passed**. They cover profile selection,
failed/missing data, the exact 2436/1538 reproductions, Derpy and passive XP,
completed/impossible targets, Aura's class bonus cap, independent Explorer/Ring
bonuses, global/lower-floor cata XP and cumulative shard conversion including
unknown versus known-zero data. Live GUI interaction remains to be checked in-game.
