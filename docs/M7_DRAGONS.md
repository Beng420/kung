# Wither Dragons

Active module: Minecraft 26.1.2. **Dungeon > Wither Dragons** holds everything for
the M7 dragons behind one master switch (default off). Every account gets the
**Debuff Tracker** (see [DUNGEON_DEBUFFS.md](DUNGEON_DEBUFFS.md)); the helper parts
below are visible and active only for Beng114's developer account, in both Kung and
native OneConfig. A config from before the merge keeps whichever of the old M7 Dragon
Debuff / M7 Dragon Helper switches it had on until the master is first toggled.

Rows open under the switch they depend on: Debuff Tracker > Track,
Spawn Markers > Marker > Core Parts (Core only), Flight Paths > Path Part. Spawn
Markers, Statue Boxes, Count Notifications and Flight Paths default on beneath the
master. Enable it before dragons spawn: an unidentified dragon remains unassigned
rather than being guessed from its later position.

The feature requires confirmed Catacombs context plus M7 metadata. Wither King
dialogue can establish the arena for this feature when the floor is unknown;
explicit other floors/modes still win. Instance changes and disconnects clear its
observations. Map and split settings are independent.

## Markers and estimated statue ranges

Spawn Markers draws a small point in each dragon's color at the documented spawn
origin, visible through the dragon model. Statue Boxes draws a depth-tested
outline for each unconfirmed statue:

- Gray: no identified, loaded, living dragon for that statue.
- Green: the received dragon origin is inside the estimated bounds.
- Red: that origin is outside the estimated bounds.

A confirmed counted statue's box disappears. These colors describe a geometric
estimate, not a live server verdict. The check uses the dragon's received origin,
not intersection with its large body or the interpolated body-box center.

| Dragon | Spawn X/Y/Z | Statue observation block X/Y/Z |
| --- | --- | --- |
| Red | 27 / 14 / 59 | 32 / 22 / 59 |
| Orange | 85 / 14 / 56 | 80 / 23 / 56 |
| Green | 27 / 14 / 94 | 32 / 23 / 94 |
| Blue | 84 / 14 / 94 | 79 / 23 / 94 |
| Purple | 56 / 14 / 125 | 56 / 22 / 120 |

Each displayed range extends 13.5 blocks from the spawn in X and 18 in Z, with
inclusive Y bounds 6 through 29.5. Z was widened from Skytils' 13.5 after recorded
kills counted up to 17.54 blocks out (Orange +Z, Green -Z, Blue +Z) and a Green kill
failed at 19.92. X has no kill beyond 10.3 yet. These are **estimates, not documented
exact server boxes**. Spawn identity requires the original spawn observation to
be within four blocks of exactly one known spawn.

Coordinates follow [Odin's dragon table](https://github.com/odtheking/Odin/blob/38ddc1b5dacd01259b2ec4f5738ada925ba880af/src/main/kotlin/com/odtheking/odin/features/impl/boss/WitherDragonsEnum.kt).
The range and origin-check reference is [Skytils' M7 implementation](https://github.com/Skytils/SkytilsMod/blob/276c07edf0f1e64956424016f438a5059c63a863/src/main/kotlin/gg/skytils/skytilsmod/features/impl/dungeons/MasterMode7Features.kt).
[Hypixel's March 8, 2022 patch](https://hypixel.net/threads/march-8th-small-dungeons-patch.4855706/)
confirms that in-range particles exist, but supplies no exact range geometry.
Particles are diagnostic observations only, not automatic count confirmation.

## Aim point

Flights repeat run after run: 18-38 recorded box-centre paths per statue (Red, Orange,
Green, Blue) deviate by at most 0.26 blocks. **Aim Point** shows where to shoot so an
arrow fired now meets the dragon's body, from the anchor's particle burst (median 5.05 s
before spawn over 19 traced spawns) until 60 server ticks after spawn:

- the body's position when the arrow arrives, raised by the arrow's drop;
- vanilla arrow physics: 3 blocks/tick at full power, drag 0.99, gravity 0.05;
- gray while an arrow fired now would land before the dragon exists, white once it lands on it.

Auto picks by dungeon class. **Terminator** (Archer, Berserker) always fires at full speed;
its marker grows one second before spawn, the cue to run in for the arrow stack.
**Last Breath** (everyone else) fires on release: the current draw sets the arrow speed,
so a weaker draw moves the aim for more lead and drop.

Lead after spawn comes from `m7-dragon-timelines.jsonl`: every part of each dragon's
first 60 server ticks, one row per tick, anchor-relative and shifted onto the last
server position. Only complete windows are kept (a dragon killed early would freeze the
aim), at most five per statue; the newest one drives the aim. Until a statue has one,
the aim sits on the body without lead: on its spawn pose before spawn, on the live body
after. Purple, usually killed at spawn, relies on this. Last Breath and Terminator
arrow speeds are unmeasured.

## Count confirmation

Count Notifications reports a count in local chat and a short title only after
server evidence: an observed statue block changes from present to air, or an exact
Wither King line matches one of:

```text
[BOSS] Wither King: Oh, this one hurts!
[BOSS] Wither King: I have more of those.
[BOSS] Wither King: My soul is disposable.
```

These signals follow [Odin's confirmation handling](https://github.com/odtheking/Odin/blob/38ddc1b5dacd01259b2ec4f5738ada925ba880af/src/main/kotlin/com/odtheking/odin/features/impl/boss/WitherDragons.kt).
A boss line names a color only when exactly one recent death can receive it;
ambiguous or unobserved deaths produce a generic count confirmation. Initially
absent or unloaded statue blocks do not prove destruction. Only five fixed loaded
positions are checked in the arena, without loading chunks or scanning structures.

A death without confirmation after 40 server ticks, or followed by another spawn,
is reported as **unknown**, never as a failed count. Later statue evidence can
confirm it. An unload is not a death, and a respawn does not prove failure:
Hypixel's patch describes prioritizing dragons with remaining statues, not
exclusively spawning them. Resolved statue/death evidence is deduplicated.

## Developer measurements

Only Beng114's verified account UUID `69617dbf-568e-4632-9ee9-80bf67d534d9` can use
the entire M7 Dragon Helper and its developer tools. `KungDeveloperAccess` checks the local Minecraft `User`
profile UUID; player names, tab entries and server-provided player UUIDs do not
grant access. The whole helper entry and all its settings are absent from other
accounts' menus; a copied enabled config cannot activate the feature. Existing
public debugging tools are unchanged.

The initial implementation restricted only Developer Diagnostics, leaving the
helper itself public. Yrkuna's reported menu entry exposed this scope mismatch;
the restriction now applies to both the shared menu catalog and feature execution.

- `/kung dev dragons on` / `off`: enable or disable the developer capture.
- `/kung dev dragons sample`: capture current positions and the looked-at block.
- `/kung dev dragons copy`: copy the bounded report to the clipboard.

Diagnostics can run independently of the helper master. White points show
received origins; yellow points show interpolated body-box centers. Capture keeps
512 rolling sample records plus a separate reserve of 128 important events.
Positions are sampled every five server ticks. At most 16 arena particle types
are sampled, each no more than once per 20 server ticks.

The latest report stays in memory through context exit, disabling capture and
disconnect, so it can be copied after the run. It does not survive a game restart.
Live tracking still resets normally; retaining a report does not retain dragon
state. These commands create no new files. Enable capture before the relevant
spawn, then copy the report after the event or run.

## Validation and remaining checks

`M7DragonTrackerTest` covers coordinates/edges, spawn identification, unload and
reload, confirmation timing, duplicate and ambiguous evidence, respawns, capacity
and reset. Settings/native-menu tests cover defaults, persistence and hidden
developer controls; `KungDeveloperAccessTest` rejects other, offline and absent
identities. `M7DragonFeatureTest` covers denied execution with copied enabled
settings, dungeon/floor gates and the Wither King
fallback; `DeveloperCommandGroupTest` checks that unavailable developer commands
are absent from parsing, usage and completion. Full Java 25 build passes:
784 tests, 783 passed, one Windows symlink skip, zero failures/errors. The copied
enabled-config runtime regression fails without the account guard and passes with
it. Shared-catalog tests cover both account branches; native-tree tests require
the entire helper to be absent for other accounts.

Live checks remain: Yrkuna/Beng114 menu visibility, all five spawn identities,
visible box colors and boundary
estimates, counted-box disappearance, chat/statue update ordering, simultaneous
deaths, unload/rejoin and next-instance reset. Capture matching origin/body,
particles and confirmation evidence before refining bounds. No exact server-box
claim is established by unit tests. No JAR is installed by this work.
