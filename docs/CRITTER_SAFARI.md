# Critter Safari uniques

`Hunting > Safari Uniques` is a passive HUD feature in Minecraft 26.1.2.
Its master switch defaults off. Enable it in `/kung`; move and scale the complete
four-column display in `/kung hud`. The editor renders a sample with the same
layout and text styles as the live HUD, even outside a Safari.

## Display and counting

Columns remain in this order: Forest, Cavern, Icy, Haunted. Each header shows
**caught unique species / all species**, for example `Forest (3/9)`.
Missing names are red. Caught names remain in their original row, gray and
struck through. A complete region's header turns green.

| Region | Unique species |
| --- | --- |
| Forest (9) | Foxtrot, Bluebird, Honeybug, Treefrog, Woodchucker, Fluffling, Hideonfloor, Parakeet, Macaw |
| Cavern (9) | Cavernfish, Flitter, Shyworm, Driftling, Chuckwalla, Rockmite, Scrappy, Snoozle, Gemzie |
| Icy (9) | Strongarm, Tepid, Polaris, Shuddersquid, Billygoat, Mantis Shrimp, Nozzlenose, Troodon, Wumpa |
| Haunted (10) | Areita, Bloodbat, Duplico, Gazer, Litterbug, Solsnatcher, Gimmiegold, Hideonwall, Hideyho, Doomspiral |

All 37 species are included, including Macaw even though it is not guaranteed
to appear. Sparkling variants count as their base species. Shard quantities,
repeated captures and chat-compaction multipliers do not create extra uniques.
This is an observed-capture checklist, not a prediction of which critters spawned.

The observer accepts whole server `CAPTURE! You caught ... and gained ... Shard!`
and `LOOT SHARE! You received ... Shard from ... catching/finding ...!` messages.
Hideyho's own `You found [the] Hideyho, and as a reward he/it gave you ...`
capture is also supported.
The user's screenshots supply ordinary catches, shared catches, `finding the
Hideyho`, timestamps and repeat suffixes. Additional Hideyho/Sparkling forms were
cross-checked against the public message grammar linked below.

Capsule throws, escapes, feeding, cornering, floor drops and player chat quoting
capture text never count. Fabric `ALLOW_GAME` observes non-action-bar messages
before cancellation or modification by chat filters, returns true and sends no
chat. New unique captures add a `safari-uniques` diagnostic to Kung's shared ring.

## Session ownership

`SafariSession` belongs to the shared `HypixelInstanceTracker.instanceEpoch()`.
A world/confirmed-server change clears all captures before the next chat event
or draw. Disconnect and disabling the feature clear it too. Walking between
biomes, temporarily missing location rows, and another player entering do not
reset the list. No dungeon lifecycle code is changed.

The HUD is gated to Hypixel and exact `Safari`, `Critter Safari` or the four
`... Biome` location names. `Area: Safari` in the tab list supplies the shared
instance name `Safari`. The Torrhus entrance does not qualify. During a new epoch with
no location yet, the local player's Safari entry, `HEAD START!` or the supplied
Safari Manager welcome can activate tracking. These start messages never clear
an existing run. `SAFARI REWARD SUMMARY` hides the HUD for the rest of that epoch.

Config uses the regular `safari` section in `config/kung/kung.json`; position and
scale follow the existing HUD editor convention. Capture state is memory-only.
Enabling the feature mid-run cannot recover catches received while it was off.

## Sources and validation

The species/region facts were compared on 2026-09-14 against
[critterMod's catalog](https://github.com/MrCloudy2/critterMod/blob/26.1.2/src/main/java/dev/rok/crittermod/data/Critters.java)
and [SafariGroupHelper's biome catalog](https://github.com/soldey/SafariGroupHelper/blob/main/src/main/kotlin/me/kmsold/safarigrouphelper/data/CritterBiome.kt).
Supplemental server message forms are documented in
[critterMod's chat grammar](https://github.com/MrCloudy2/critterMod/blob/26.1.2/src/main/java/dev/rok/crittermod/parse/ChatParser.java).
These sources supply game facts; Kung owns the implementation and its lifecycle.

The supplied trace created at **2026-09-16 23:48:39 UTC** confirms the missing-HUD
cause: at 01:44:07 local time, the shared context correctly published
`instance=Safari server=m33cq` from `Area: Safari`, but `SafariSession` rejected
that exact name. Its inactive gate blocked both rendering and capture counting;
`HEAD START!` could not override a known, rejected location. Accepting the exact
`Safari` alias fixes both paths without changing the shared instance tracker.
The same trace's Hideyho reward uses `the Hideyho` and `it gave you`, which the
old capture expression also rejected. Both forms are now accepted alongside the
previous `Hideyho` / `he gave you` spelling.

The context/message regression and Hideyho assertion failed before the fix;
all 17 focused Safari tests pass afterward. The retained capture messages yield
16 uniques: Forest 0/9, Cavern 0/9, Icy 9/9 and Haunted 7/10. This is a replay of
the supplied evidence, not a claim that the old runtime counted them; the trace
contains no `safari-uniques` records and does not record the feature toggle.

Validation on 2026-09-17: the full Java 25 active-module build passed with
613 tests (612 passed, zero failures/errors, one Windows symlink-permission skip).
`git diff --check` passed. Artifact:
`versions/mc26_1_2/build/libs/kung-26.1.2-0.3.5.jar`.
No game-profile installation was performed.

Focused tests: `SafariSessionTest`, `SafariOverlayTest`, `SafariConfigTest`.
They exercise screenshot-derived messages (player names simplified), false
positives, hidden chat, action bars, deduplication, Sparkling/Hideyho, world/server
changes, early start messages, biome transitions, summaries, styles, HUD bounds,
Hypixel host checks and persisted settings with Feast positions preserved.

Live checks remain: enable before entering with the corrected build, compare all
four region counts with own/shared catches (including Hideyho), confirm reset in
the next instance and entrance exclusion, and inspect readability/dragging/scaling
alongside the other HUDs. The server's `Safari` location spelling is confirmed by
the trace; unit tests and a build do not establish live rendering results.
