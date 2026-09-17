# Dungeon debuff observations

Dungeon > **Ice Spray Highlight** and **M7 Dragon Debuff** are independent,
default-off features in Minecraft 26.1.2. Neither requires Dungeon Map, Splits or
Util > Hitboxes. They observe incoming packets and draw local UI; they never use
an item, aim, attack or send party chat.

## Ice Spray Highlight

An applied equipment packet giving an invisible armor stand Packed Ice is the
effect marker. This includes markers caused by other players; the caster is not
transmitted. The nearest unambiguous living non-player/non-armor-stand entity is
matched: at most 1.5 blocks from an ordinary mob's box, or 8 blocks from a dragon's
origin. A second eligible candidate within 0.5 blocks of the best distance makes
the match unknown. Once a populated query is ambiguous, later movement cannot
turn it into a successful match. Missing entities/invisibility metadata allow up
to four resolution attempts, retaining the original equipment-packet tick.

This is **spatial inference**, not an explicit server target ID. Crowds can cause
missed highlights, and decorations or another mob near a marker can still be
ambiguous. Normal-mob marker layout has not yet been verified in this profile.
Do not replace this with the user's right-click, held item or a forward cone:
those prove neither a hit nor another player's successful spray.

A matched mob gets an interpolated light-blue bounding box (`#99DDFF`) with a
20%-alpha fill per face. Vanilla depth-tested lines/quads are used. Geometry is
extracted into an immutable frame snapshot; rendering does not scan the world.
**Box Size (%)** sets each dimension to 100–200% in 5% steps (default 100%).
Expansion is centered on the original box and keeps the existing minimum
0.02-block outline clearance; 200% doubles normal mob width, height and depth.
Both outline and fill use the expanded box. Collision and spray matching still
use the original entity bounds. The slider is shared by Kung and OneConfig.
The highlight expires after 100 accepted server ticks from marker observation.
Repeated equipment packets for the same loaded marker do not extend it. Removed
or dead entities are not rendered. There are at most 256 retained markers and
highlighted targets, and 128 eligible candidates per local query; exceeding a
limit leaves the observation unknown rather than selecting from a truncated set.

## M7 Dragon Debuff

Confirmed Catacombs M7 context and an observed Ender Dragon spawn in a recognized
P5 spawn region establish each observation. A new entity UUID starts a fresh
attempt, including respawns of the same color after a failed statue kill. Arrows,
spray timing, timer and chat publication reset together. A range reload of the same
UUID does not restart its clock. Health-zero/death packets confirm death; removal
ends the observed timer without claiming a kill. The distinction is in chat hover.
Reports publish at the end of the client packet batch, after spawn selection has
settled, so early kills retain their buffered hits. Mid-fight enabling does not
invent an earlier spawn.

The live HUD is hidden until a recognized dragon observation is available; it
does not show a heading or waiting message during clear or earlier boss phases.
Nearest Statue mode first waits for that wave's selection to settle. The editor
preview remains available before the dragon phase because it supplies sample data.
The live HUD retains the latest observation per dragon color, including completed
results, until instance exit or disabling. `/kung hud` and the optional native
OneConfig editor share its position and 25–300% scale. Both offer a five-dragon
preview without changing gameplay state. HUD and chat use one colored line per
dragon: `Purple: Time: 16.20s | Arrows: 3 | Sprayed: 9t`. Missing spray is
`Sprayed: no`. There is one final chat report per attempt; the separate spray
announcement and lifetime "Hit sounds" output have been removed. Timing, hit
intervals and observation limitations remain in hover. Time is observed lifetime,
not the length of the arrow-counting window.

**Track** offers **All Dragons** (default, retaining the existing behavior) and
**Nearest Statue**. The latter filters both the HUD and local result chat.
At the first observed spawn of a wave, Kung captures the player's known position.
Spawns in that server tick and the following two ticks form one group, allowing
the initial pair to arrive in either order or adjacent client batches. At the
third tick, Kung chooses the closest horizontal statue position among that group's
dragons. This adds at most three nominal ticks of presentation delay, but records
arrows and spray from the original spawn tick, including a dragon killed before
the choice settles. This grouping is a bounded heuristic, not a server wave ID.

For Orange/Purple, a player standing at Purple sees Purple regardless of which
spawn packet arrived first. Moving, dragon flight or either dragon dying does not
switch that choice; a later spawn wave captures a new position and chooses again.
Only newly spawning dragons compete with each other, not old survivors. Missing
player-position evidence leaves the selection unknown. Class/party-priority rules
are not inferred. All observations remain internally available when switching the
setting; previous filtered chat is not replayed. Each wave's selection is retained
for its eventual chat result. Nearest mode has one dragon row and All Dragons
has up to five; position and scale are unchanged, while editor bounds now fit
the compact content.

Arrows count incoming `ClientboundSoundPacket` feedback with sound
`entity.arrow.hit_player` during offsets **0–40 server ticks** after spawn.
Ordinary `entity.arrow.hit` impacts (including blocks/other players), entity-bound
sounds, local playback and damage-source metadata are not inputs. The packet
origin must be within four blocks of the local player's current position, allowing
sound coordinate quantization, head height and some movement. This is an additional
proximity filter, not a shooter-ID guarantee. The 21:45:12 trace below validates
acceptance of recorded player-local feedback; remote-feedback rejection still
needs a live check.

Feedback is assigned **once** to the nearest spawning statue, regardless of the
display scope. The first two ticks are buffered (at most 128 packets) while the
initial pair is grouped, retaining original offsets and same-tick multiplicity.
Other dragons show `Arrows: --`, not a duplicate count or a claim of zero. A
selected dragon with no accepted feedback shows zero. Feedback after tick 40 or
after the observation ends cannot increase the count. A new spawn wave chooses
again and opens its own window.

This signal has no target or weapon ID: it cannot prove an exact Last Breath-only
count or exclude every nearby player's server-generated sound. Using another bow
or hitting another entity during the window can contaminate the estimate. Do not
label the count as source-confirmed, LB-only or an exact server debuff stack count.
Holding/releasing Last Breath alone is also not proof of a successful hit.

Chat hover gives the first, fifth and last hit offsets, first-to-last span, average
interval rate `(count - 1) * 20 / (lastTick - firstTick)`, and the first 64 hit tick
offsets plus consecutive gaps. Same-tick-only samples have no finite interval rate.
All timing is in **observed server ticks**, using the existing standalone nonzero
ping sequence, excluding duplicates and protocol-bundle pings. `/20t` is a nominal
tick rate, not measured wall-clock arrows per second. These are client observations,
not server-internal application timestamps or latency-corrected measurements.

The first uniquely matched ice marker records Ice Spray ticks since observed
spawn; a second spray cannot overwrite the first time. No observed marker is
shown as `Sprayed: no`, including on death. Hover explains that this means no
matching marker was received. Spray tracking continues after the arrow window:
a late confirmed marker still displays its actual offset. Caster/source fields
are omitted from the output.

### Live trace, 2026-09-17 20:33:36

The user's `kung-trace-20260917-203336.log` contains the initial Blue/Purple pair
at tick 10558 and position `(85.1099, 6, 96.3198)`. Blue's old lifetime list begins
`[1, 4, 4, 133, 133, 133, ...]`; the new window retains the first three and assigns
them to Blue based on the recorded position. Previously all overlapping dragons
accumulated the same feedback, producing 73/167-sound reports in the screenshots.
No attributed arrow damage events occur in the retained trace. This supports
removing that unavailable evidence requirement, not claiming exact target IDs.

Ice markers show Purple at 9t, Orange at 4t and Green at 12t. Orange and Green
spawn repeatedly with distinct UUIDs, confirming the fresh-attempt lifecycle in
this capture. Source/spatial filtering cannot be replayed from this old log:
volume, pitch, packet position and held item were not recorded then.

### Live trace, 2026-09-17 21:45:12

The user reports improved spray accuracy in live play. Inspection of
`kung-trace-20260917-214512.log` finds five dragon observations, all ending with
confirmed death:

| Dragon | Observed lifetime | Early arrows | Spray |
| --- | --- | --- | --- |
| Orange | 1.45s | -- | no |
| Blue | 1.60s | 28 | 4t |
| Red | 0.90s | 3 | 6t |
| Green | 0.80s | 4 | no |
| Purple | 1.05s | 6 | 6t |

Orange/Blue arrive in the same server tick (7579), and the captured position near
Blue selects Blue at tick 7582. Orange does not inherit Blue's arrow count.
Four Blue ice markers at offset 4, four Red markers at offset 6 and eight Purple
markers at offset 6 agree with the report; repeated markers preserve one first
spray time. Later Purple markers arriving around death cannot overwrite 6t.
Green/Orange have no matched dragon ice marker in this capture.

All 19 retained feedback records have `accepted=true local=true`; their origins
are 0.14–1.56 blocks from the player's applied position, inside the four-block
filter. The recorder suppresses duplicate entries, so use the final per-dragon
hit lists/counts rather than counting retained trace lines. Blue's 28 hits include
feedback received while Terminator is held. This confirms the counter must still
be described as early arrow feedback, not LB-only hits; the held item at receipt
cannot identify an in-flight arrow's bow. No remote rejected feedback, post-40t
feedback or repeated-color respawn is present in these five observations, so this
capture does not add live validation for those cases. No gameplay changes were
made from this successful validation run.

## Ownership and validation

`DungeonDebuffFeature` receives applied client-thread packet hooks and the shared
server-tick event. `DungeonDebuffTracker` owns bounded in-memory observations;
`IceSprayHighlightRenderer` draws world geometry; `DragonDebuffHud` draws the HUD.
World/instance epoch changes, leaving Catacombs, disconnect and both toggles off
clear observations. Disabling only dragon tracking clears its results while the
independent highlight can continue. Settings and HUD placement use `dungeonMap`
inside the existing Kung config; no runtime file or profile JAR is added.

`DungeonDebuffTrackerTest` covers same-tick multiplicity, the 40-tick boundary,
single-statue assignment, trace-derived early/late hit offsets, independent effects,
ordinary/remote sound rejection, original observation ticks, expiry,
ambiguous/distant markers, unload versus death, UUID reuse, bounds and retained
HUD results and fresh counters/spray/timers for same-color respawns.
`KungSettingsTest` covers independent defaults, persistence and HUD
placement/scale; existing shared HUD/OneConfig and packet-clock tests also run.
Additional regressions cover centered box expansion and limits, both initial-spawn
orders, captured-position stability, early deaths, next-wave reselection, missing
position evidence, scope switching and saved size/scope settings.
`KungMessagesTest` also covers semantic colors and preservation of message text,
commands, file paths and severity styling. Full-build results are in the current
handoff. No profile JAR is installed by the build.
Live checks remain: normal mob versus dragon marker geometry, spray by a teammate,
20% fill, repeated spray/expiry, shooter-feedback packet positions, overlapping
dragons/nearby players, LB-to-Terminator transitions, death/unload ordering and both
HUD editors. The size slider and nearest
statue selection need live visual checks, including actual initial-pair packet spacing.

Save `/kung log save` immediately after a normal-mob spray and an M7 dragon test.
The `dungeon-debuff` area records marker IDs/target decisions, observed ticks,
accepted/rejected arrow feedback with position, player position, volume/pitch and
held-item name (diagnostic only), spawn/player positions,
`statue-selection` decisions and final reports.

Protocol-reference inspection on 2026-09-17:
[Noamm DragonCheck](https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/features/impl/floor7/dragons/DragonCheck.kt)
uses Packed Ice armor-stand equipment and nearby dragons, and assigns shooter
feedback to its priority dragon during an early window. Kung uses the user's
nearest-statue selection and a fixed 40-tick window, with the limitations above.
Spawn regions were cross-checked against
[Odin WitherDragonsEnum](https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/boss/WitherDragonsEnum.kt).
Minecraft APIs were checked against the local 26.1.2 game JAR.
