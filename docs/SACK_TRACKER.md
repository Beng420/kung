# Sack Tracker

Util > Sack Tracker is a default-off HUD inspired by SkyHanni's shard tracker.
Set **Track Key** in its settings, open any sack (a menu whose title ends in
` Sack`) and press the key over an item: it is tracked with the count from its
`Stored:` lore line. The same key on a tracked item stops tracking it.

Each tracked item has a collapsible settings row with a **Goal** (`10k`, `1.5m`
work; empty means no goal) and **Remove**. The HUD shows `Name: count` in the
item's rarity color, or `count/goal` in red until the goal and green from it.
Move and resize it in `/kung hud`. **Show In Menus** (default on) draws it again
on top of inventories, chat and other menus, but only in a world; Kung's own
screens and the title screen are left out. Over a menu the tracker is interactive:
hovering an item shows "Click to remove from tracker" and clicking it stops
tracking, and the red `[Reset Display]` line below the items clears them all.

Counts follow SkyBlock's own `[Sacks] +X items, -Y items. (Last Ns.)` messages:
the hover text of each part lists `+1,280 Wheat (Agronomy Sack)` lines, netted
per item. This covers items taken out of sacks too, by `/gfs`, the Forge or a
recipe. These messages must stay enabled in SkyBlock's settings. An open sack
sets tracked counts to what it shows every 10 ticks. A message can overlap what
the open sack already showed, which counts that part twice until the sack is
opened again. Both are traced under `sack-tracker` in `/kung trace`.

Gemstones list qualities instead of `Stored:` and cannot be tracked. Items and
goals persist in `misc.sackTrackerItems` of `config/kung/kung.json`.
`SackTrackerFeatureTest` covers the title, lore, amount and hover parsing.
