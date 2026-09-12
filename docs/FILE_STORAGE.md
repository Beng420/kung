# Runtime file storage — 0.2.16

Kung writes its own persistent data below `config/kung/` and traces below
`logs/kung/`. The mod JAR lives in `mods/`. These paths are relative to the game
profile; a custom Fabric config directory is respected.

| Location | Created / used when |
| --- | --- |
| `config/kung/kung.json` | First successful config load and settings changes. Old `config/kung.json` remains a readable migration source. |
| `config/kung/custom-sounds/` | Custom Sounds initialization/index refresh. Four bundled WAV presets are copied here if absent; user audio files also live here. |
| `config/kung/dungeon-data/` | Explicit room learning/deletion/type commands, enabled Local Data learning, or enabled Room Sync persistence. Normal bundled recognition does not require this directory. |
| `config/kung/updates/` | Installing an update: downloaded JAR, temporary download, pending marker and installer scripts. Checking for an update does not download a file. |
| `config/kung/legacy/` | Only if relocation encounters different existing files: preserves the old content without replacing the current destination. |
| `logs/kung/kung-trace-*.log` | `/kung log save`. Routine diagnostic events remain in a bounded memory buffer. Normal logger messages still use Minecraft's `logs/latest.log`. |
| `mods/` | The installed JAR; update installation may temporarily back up/replace it. |

Dungeon-data filenames are `known-rooms.json`, `known-rooms-remote.json`,
`known-room-types.properties`, `known-room-preloads.jsonl` and the legacy
learning/undo JSONL formats when those commands need them. The ordinary user
does not need to supply these files. Do not replace the bundled room database
with profile content without reviewing its room/hash evidence.

Static trapped-chest exclusions are different: Buttons/Dueces templates are
resources inside the JAR, never extracted to a profile file. Additional explicit
captures last until restart and can be copied via `/kung mimic copy`.

## Existing profiles

At startup, before config/services/features load, `KungFileLayout` relocates:

| Old profile-root directory | Destination |
| --- | --- |
| `kung-dungeon-scans` | `config/kung/dungeon-data` |
| `kung-custom-sounds` | `config/kung/custom-sounds` |
| `kung-debug` | `logs/kung` |
| `kung-updates` | `config/kung/updates` |

Missing old directories do not create empty destinations. Identical duplicate
files are removed after byte comparison; differing duplicates are preserved under
`config/kung/legacy/<old-directory>/`, with numbered names if necessary. Existing
destination files win. Failed moves leave remaining source data in place and log
a warning for retry on the next start. Symbolic links encountered while merging
are preserved rather than followed. Old pending updater source paths resolve to
the relocated download when necessary.

Do not move files out from under a running older Kung build: it can recreate the
old paths. Install the new JAR with Minecraft closed and let startup migrate them.
No profile data was moved during this development turn.

## Maintenance and validation

`KungPaths.fileLayout()` caches Fabric paths once, avoiding disk checks in recurring
room lookups. `KungFileLayout` is independently testable. The explicit Gradle
`syncLocalDungeonRoomData` task reads the new location, with a legacy fallback;
ordinary builds still never invoke it.

The write-site audit covers config, room catalogue/classifier, custom sounds,
trace saving and updater downloads/scripts. Seven layout tests cover fresh startup,
restart-safe migration, duplicate contents, file/directory conflicts, custom config
locations, pending updater paths and use before Fabric starts. Full active-module
build: **269 tests passing**, no failures, errors or skipped tests. Migration in
a real running Minecraft client has not been exercised here.
