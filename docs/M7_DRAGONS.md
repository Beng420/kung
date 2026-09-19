# M7 Dragon Helper

Active module: Minecraft 26.1.2. **Dungeon > M7 Dragon Helper** defaults off in
both Kung and native OneConfig. Spawn Markers, Statue Boxes and Count Notifications
default on beneath that master. Enable it before dragons spawn: an unidentified
dragon remains unassigned rather than being guessed from its later position.

The feature requires confirmed Catacombs context plus M7 metadata. Wither King
dialogue can establish the arena for this feature when the floor is unknown;
explicit other floors/modes still win. Instance changes and disconnects clear its
observations. Existing M7 Dragon Debuff, map and split settings are independent.

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

Each displayed range extends 13.5 blocks from the spawn in X and Z, with inclusive
Y bounds 6 through 29.5. These are **Skytils community estimates, not documented
exact server boxes**. Spawn identity requires the original spawn observation to
be within four blocks of exactly one known spawn.

Coordinates follow [Odin's dragon table](https://github.com/odtheking/Odin/blob/38ddc1b5dacd01259b2ec4f5738ada925ba880af/src/main/kotlin/com/odtheking/odin/features/impl/boss/WitherDragonsEnum.kt).
The range and origin-check reference is [Skytils' M7 implementation](https://github.com/Skytils/SkytilsMod/blob/276c07edf0f1e64956424016f438a5059c63a863/src/main/kotlin/gg/skytils/skytilsmod/features/impl/dungeons/MasterMode7Features.kt).
[Hypixel's March 8, 2022 patch](https://hypixel.net/threads/march-8th-small-dungeons-patch.4855706/)
confirms that in-range particles exist, but supplies no exact range geometry.
Particles are diagnostic observations only, not automatic count confirmation.

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
the new developer tools. `KungDeveloperAccess` checks the local Minecraft `User`
profile UUID; player names, tab entries and server-provided player UUIDs do not
grant access. The Developer Diagnostics setting is absent from both public menus,
and a copied true config value remains inactive for other accounts. Existing
public debugging tools are unchanged.

- `/kung dev dragons on` / `off`: enable or disable the developer capture.
- `/kung dev dragons sample`: capture current positions and the looked-at block.
- `/kung dev dragons copy`: copy the bounded report to the clipboard.

Diagnostics can run independently of the public helper master. White points show
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
identities. `M7DragonFeatureTest` covers dungeon/floor gates and the Wither King
fallback; `DeveloperCommandGroupTest` checks that unavailable developer commands
are absent from parsing, usage and completion. Full Java 25 build passes:
782 tests, 781 passed, one Windows symlink skip, zero failures/errors.

Live M7 checks remain: all five spawn identities, visible box colors and boundary
estimates, counted-box disappearance, chat/statue update ordering, simultaneous
deaths, unload/rejoin and next-instance reset. Capture matching origin/body,
particles and confirmation evidence before refining bounds. No exact server-box
claim is established by unit tests. No JAR is installed by this work.
