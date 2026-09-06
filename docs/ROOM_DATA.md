# Dungeon Room Data

Bundled/canonical room data uses `known-rooms.json`.
Kung can still import old `known-rooms.jsonl` files for migration, but active 26er profiles should store learned room data in JSON only.
Runtime loads bundled Jar room data plus local profile room data when present, so `/kung room learn` affects matching immediately after the catalog reloads. The Dungeon Map `Local Data` setting only gates write-heavy development helpers such as auto-learning stable hashes/preload transitions.

## Canonical Format

```json
{
  "schema": 1,
  "rooms": [
    {
      "name": "Atlas",
      "type": "NORMAL",
      "secrets": 6,
      "crypts": 0,
      "variants": [
        {
          "id": "variant-1",
          "components": [
            {
              "dx": 0,
              "dz": 0,
              "hashes": [
                {
                  "core": 1646734052,
                  "stable": 1052073972,
                  "seen": 1,
                  "updatedAt": 0,
                  "source": "bundled-1.21.11"
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

- `room`: one logical dungeon room, keyed by `name`, `type`, and `secrets`.
- `variant`: one known shape/layout of that room.
- `component`: one room cell inside the variant.
- `dx` / `dz`: relative cell position inside the variant, normalized from the top-left cell.
- `hashes`: all known hashes for that cell, including old version-specific hashes and stable hashes.
- `core`: raw room core hash.
- `stable`: stable room hash if known; `0` means unknown.
- `seen`: number of observations merged for the same hash.
- `updatedAt`: timestamp/order source for learned local data.
- `source`: where the hash came from, for auditing and future cleanup.
- `crypts`: total crypt count for the room. Wiki Prince counts are included in this total and must not be added separately.
- Prince room icons are maintained separately in `DungeonKnownRoomCatalog`. Do not infer them from wiki crypt counts alone; for example, `Andesite` is audited as `crypts=0` and must not show a Prince icon.
- The map footer treats `crypts=0` as a known zero. It only shows `+?` when a scanned room is still unidentified or a room has an intentionally uncertain wiki value, currently `Admin` or `Buttons`.

Canonical metadata rules:

- `Blaze` is `PUZZLE` with `secrets=1`.
- Puzzle rooms with no wiki-listed secrets, including `Ice Path`, use `secrets=0`.
- `Deathmite` is `NORMAL` with `secrets=6`; learn legitimate 1x3/1x4 shapes with `/kung room learnmulti Deathmite`.
- `mc26_1_2` is the active room-data target. Keep `mc26_2` untouched unless that port is explicitly resumed.
- Local profile files (`known-rooms.json`, `known-rooms.jsonl`, `known-room-preloads.jsonl`, and `known-room-types.properties`) must not be required for normal users or friends; bake audited data into the Jar before sharing. The 26.1.2 Gradle build runs `syncLocalDungeonRoomData` before `processResources`, copying local learned data from the active Modrinth profile into bundled resources. Override that profile with `-PkungProfileDir=...` or `KUNG_PROFILE_DIR` if needed.
- Variants must be contiguous and at most 4 cells. Larger or disconnected shapes are treated as corrupted data.
- During a run, Kung remembers the first non-empty hash per room cell and stores it together with later manual learns when it differs.
- Matching is core-first: once a room cell has a known `core` or `stable` hash, Kung can label that cell even when the full multi-cell shape is incomplete.
- Adjacent known cells are only grouped when they have the same room metadata and no visible door between them; groups over 4 cells are split into one-cell matches instead of drawing a huge fake room.

## Converting Current Data

Run this after a learning session, not while Minecraft needs the file to stay untouched.
The converter accepts canonical `known-rooms.json`, legacy `known-rooms.jsonl`, or directories containing those files.
Use archived/source files as inputs, not the 26er output JSON you are regenerating, or repeated runs will inflate `seen` counts.

```powershell
node tools/convert-known-rooms.mjs versions/mc26_1_2/src/main/resources/kung-dungeon-scans/known-rooms.json `
  versions/mc1_21_11/src/main/resources/kung-dungeon-scans/known-rooms.json `
  versions/mc1_21_11/src/main/resources/kung-dungeon-scans/known-rooms.jsonl `
  "$env:APPDATA/ModrinthApp/profiles/Dungeons 26.1.2/kung-dungeon-scans/known-rooms.json"
```

Write the merged output to `mc26_1_2`, repair accidental local learns if needed, then run:

```powershell
node tools/repair-active-room-data.mjs "Dungeons 26.1.2"
node tools/analyze-room-data.mjs versions/mc26_1_2/src/main/resources/kung-dungeon-scans/known-rooms.json
```

Only sync or audit `mc26_2` when that port is explicitly resumed.

The converter merges duplicate hash observations, preserves source labels for audits, and folds core-only observations into stable observations when the same core hash later has a stable hash.

## Auditing

Use this before trusting a large data import:

```powershell
node tools/analyze-room-data.mjs versions/mc26_1_2/src/main/resources/kung-dungeon-scans/known-rooms.json
node tools/check-prince-rooms.mjs
```

Use room debug in-game before deleting or renaming a suspicious room entry:

```text
/kung room debug
/kung roomdata
```

## Temporary Homeserver Room Sync

Kung can optionally use a homeserver for two separate sync paths. The main gameplay path is live run sync: Kung clients on the same Hypixel server exchange the currently revealed map, room clear/completion state, room secret progress, and per-player run secret/death counts. This is temporary memory-only data and is not written into learned room files.

The older room-data path is still available as a shared learned room-data source while collecting data with trusted friends.
Server data is cached in the active profile as `kung-dungeon-scans/known-rooms-remote.json`.
It is loaded in addition to bundled and local data when Room Sync is enabled; local `known-rooms.json` stays separate.
Room Sync and Room Sync Upload are intentionally session-only toggles. They are off again after restarting Minecraft, even if the server URL and token are still saved in config.

Start the tiny built-in sync server on the homeserver:

```powershell
$env:KUNG_ROOM_SYNC_TOKEN="change-me"
$env:KUNG_ROOM_DATA_PATH="D:\kung-room-sync\known-rooms.json"
node tools/kung-room-sync-server.mjs
```

Mod commands:

```text
/kung roomsync server http://your-server:8765
/kung roomsync token change-me
/kung roomsync enable
/kung roomsync upload true
/kung roomsync ping
/kung roomsync pull
/kung roomsync status
```

With upload enabled, successful `/kung room learn`, `/kung room look`, and `/kung room learnmulti` calls still save locally first and then push a room report to the server in the background.
While Room Sync is enabled in a dungeon, Kung clients on the same Hypixel server exchange live run snapshots through the same homeserver. This lets one Kung user see rooms another Kung user has already revealed in the current run, including live room secret progress and per-player run secret/death counts. Live snapshots are kept in memory on the sync server and expire automatically; they are not written into `known-rooms.json`.
Manual one-room upload is also possible:

```text
/kung roomsync push Grand Library 4 normal
```

Friends can use the same Jar, point it at the same server, enable pull, and optionally enable upload if you trust their reports.
The server only merges room reports; it does not accept deletes/tombstones from clients.
Do not rely on this server mode for broad/public distribution; bake audited room data into bundled `known-rooms.json` before sharing Kung more widely.

## Private Jar Defaults

For trusted private builds, the 26.1.2 Jar can bundle Room Sync server/token defaults so friends do not need to type those values manually.
Do not use this for public releases: a bundled token can be extracted from the Jar by anyone who has the file.
Feature master toggles still default to off; bundled defaults must not auto-enable Room Sync or upload.
Normal builds do not copy the server/token from your local profile. To intentionally bake the values saved in the active profile's `config/kung.json`, pass `-PkungPrivateRoomSyncDefaults=true`; the local `build-and-copy-jar_26.1.2.bat` does this for private test jars.

PowerShell example:

```powershell
$env:KUNG_DEFAULT_ROOM_SYNC_SERVER_URL="http://your-server:8765"
$env:KUNG_DEFAULT_ROOM_SYNC_TOKEN="your-token"
.\gradlew.bat :versions:mc26_1_2:build
```

The same values can also be passed as Gradle properties:

```powershell
.\gradlew.bat :versions:mc26_1_2:build `
  -PkungRoomSyncServerUrl="http://your-server:8765" `
  -PkungRoomSyncToken="your-token"
```
