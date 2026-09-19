# Dungeon Room Data

Bundled/canonical room data uses `known-rooms.json`.
Kung can still import old `known-rooms.jsonl` files for migration, but active 26er profiles should store learned room data in JSON only.
Runtime normally loads bundled Jar room data. Local profile room data is an opt-in development/override layer behind the Dungeon Map `Local Data` setting, so stale files in a Modrinth profile cannot silently change normal player matching. An explicitly linked development project instead supplies the editable room data directly; see [automatic project saving](#automatic-project-saving).

Since 0.2.16, optional local/remote room files live under
`config/kung/dungeon-data/` in the game profile. The old `kung-dungeon-scans/`
directory migrates at startup; JAR resource paths keep their existing names.
See [file storage](FILE_STORAGE.md) for the full write/migration inventory.

## Metadata Reference

The user-selected primary reference for room names, secret counts, shapes, crypts, and Prince metadata is the [Catacombs Rooms page on the Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki/w/Catacombs_Rooms). Use this page for metadata comparisons; Skyblocker room data is not the naming or secret-count authority for this project.

The saved comparison table is [reference/catacombs-rooms.json](reference/catacombs-rooms.json). Its source, revision, interpretation notes, and readable table are documented in [CATACOMBS_ROOMS_REFERENCE.md](CATACOMBS_ROOMS_REFERENCE.md). Keep this reference separate from runtime `known-rooms.json`: it provides factual metadata for review, not observed room hashes or an automatic import into the mod.

The September 18, 2026 audit rechecked the live page (still revision 795866).
Only `Doors`, `Skull`, `Withermancer` (one each) and `Supertall` (two) have positive
counts in the **Princes** column. The bundled boolean is true for those four rooms.
`Big Red Flag`, `Bridges`, `Chambers`, `Flags`, `Grass Ruin`, `Leaves`, `Market`,
`Pirate`, `Quartz Knight`, `Red Blue`, `Sloth` and `Waterfall` were corrected to
false: the selected source does not list a Prince there. Empty source cells remain
empty in the reference; false in the runtime catalog means no source-backed Prince
marker, not independent live confirmation of absence. Crypt totals and Revive Stones
are not Prince evidence. Legacy fallbacks, aliases and maintenance tools use the
same four positive rooms. `tools/check-prince-rooms.mjs` validates boolean flags and
reports differences from that wiki baseline; add `--wiki-strict` to fail on differences.
Deliberate in-game corrections may differ from the saved wiki reference.
No other metadata, variants or hashes changed in this audit. `Ritual` remains absent
from the catalog because the wiki provides no observed hashes for its identity.

On September 11, 2026, the user confirmed the existing mod crypt totals for `Admin` (34) and `Buttons` (21). These are verified project values and remain unchanged. The wiki snapshot retains its original `[Confirm]` markers as source attribution; those markers do not make these two project values uncertain. `tools/check-room-crypts.mjs` checks both totals for missing entries and mismatches alongside the other crypt expectations, targeting the active `mc26_1_2` module only.

The wiki marks the page as work in progress. Preserve duplicate rows and conflicting values for review rather than silently choosing one. In the saved September 11 reference, `Lava Pit` appears twice with 3 secrets in both rows but different crypt counts. There is no `Lava Pool` entry and the page supplies no Kung core/stable hashes. On September 18 the user identified the previously unlabelled Rare room as a separate Lava Pit; this observation, not the wiki name, establishes its identity. Secrets and crypts remain unverified and use the user's requested 0/0 placeholders.

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
      "prince": false,
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
- `prince`: whether this room can contain Prince. Do not infer it from crypt counts; for example, `Andesite` is audited as `crypts=0` and `prince=false`.
- The map footer treats `crypts=0` as a known zero. It shows `+?` when a scanned room is still unidentified. `Admin` (34 crypts) and `Buttons` (21 crypts) are user-confirmed totals and have no room-specific uncertainty exception.

Canonical metadata rules:

- `RARE` is a separate blue map type with ordinary room/door/clear mechanics. Eight unambiguous Rare names promote legacy `NORMAL`/`UNKNOWN` records on load. `Lava Pit` additionally has two distinct entries: the established NORMAL room (3 secrets, 1 crypt) and the user-identified RARE room (0 secrets, 0 crypts as placeholders). Their shared name does not imply shared geometry or metadata. `Lava Skull`/`Lava Tomb` remain aliases of the NORMAL room.
- Rare Lava Pit uses core `-1005518830` / stable `1296131753`, originally observed on September 11 and stored under the unverified name `Lava Pool`. The 01:18 September 18 trace shows an unlabelled, physically scanned RARE room at cell `1,4`, later visited and cleared. Its exact hashes are not retained in that trace; the association follows from the only configured RARE type-only hash in both bundled and profile type files, together with the user's identification. The new entry uses those already observed hashes, not generated wiki hashes. The old local 2-secret claim is not imported. Verify the label on the next live encounter; 0/0 is intentionally not a verified total and can undercount catalogue estimates until corrected.
- Erroneous older RARE records for the normal Lava Pit are repaired only when one of its known core/stable hashes proves that identity. JSON/legacy imports and local observations retain the repair without collapsing the separate Rare metadata. A remote Lava Pit reported as RARE stays RARE without local hash evidence; an observed NORMAL hash corrects that report. Normal and Rare rooms keep separate owners and clear credit, even when adjacent. Normal builds do not modify any profile files.
- Learning accepts `rare`, for example `/kung room learn Example 2 rare`; `/kung room type rare` learns only the current room type and refreshes the map immediately.
- `Blaze` is `PUZZLE` with `secrets=1`.
- Puzzle rooms with no wiki-listed secrets, including `Ice Path`, use `secrets=0`.
- `Deathmite` is `NORMAL` with `secrets=6`; learn legitimate 1x3/1x4 shapes with `/kung room learnmulti Deathmite`.
- `mc26_1_2` is the active room-data target. Keep `mc26_2` untouched unless that port is explicitly resumed.
- Local profile files (`known-rooms.json`, `known-rooms.jsonl`, `known-room-preloads.jsonl`, and `known-room-types.properties`) must not be required for normal users or friends; bake audited data into the Jar before sharing. Enable `Local Data` only while collecting/testing local room-data overrides. The explicit 26.1.2 Gradle task `syncLocalDungeonRoomData` copies local learned data from the active Modrinth profile into bundled resources. Override that profile with `-PkungProfileDir=...` or `KUNG_PROFILE_DIR` if needed.
- Variants must be contiguous and at most 4 cells. Larger or disconnected shapes are treated as corrupted data.
- During a run, Kung remembers the first non-empty hash per room cell and stores it together with later manual learns when it differs.
- Pre-run observations survive the countdown. Matching refreshes when either the raw or stable hash changes. Same-cell transitions into directly known rooms also create session-only preload hints keyed by both hashes; conflicting pairs are rejected. These hints do not change bundled data or bypass the Local Data setting for profile files.
- Matching is core-first: once a room cell has a known `core` or `stable` hash, Kung can label that cell even when the full multi-cell shape is incomplete.
- Adjacent known cells are grouped when they have the same room metadata and no blocking room boundary. Visibility alone does not disable those connections. Catalog matching is conservative around world doors; render grouping can reconnect fragments such as Layers unless an explicit narrow map door or special door separates them. Both render union passes retain the four-cell limit.

## Prediction stops at observed visibility — 2026-09-18

The first implementation incorrectly disabled room connections as soon as one
cell became visible. The user clarified that obvious partial multi-cell shapes must
still connect and be completed early. Visibility now gates only bundled/session
preload identity aliases, not connections between observed cells. A cell is visible
when the server map reveals it or all chunks intersecting its 32-block footprint
have loaded. World evidence checks chunk presence (at most nine checks per scanned
cell), not server-side block-generation completion; it persists until instance reset.

Direct catalog core/stable hashes still identify rooms, with direct hashes taking
priority over preload aliases. A current nonempty scan replaces stale predictions
after visibility. A previously direct known observation survives later unknown
block states; a new direct known observation can replace it. This retains confirmed
room identity during puzzles without restoring preload guesses. The render fallback
uses the same hint resolver. Remote reports start
with separate cell owners so a shared name cannot bypass the connection rules.

An explicit narrow map connector forms a boundary that even strict templates may
not cross. Missing connector pixels alone are insufficient, including between two
visible cells. Broad observed connectors join compatible fragments, subject to the
four-cell limit. New visibility/connectors invalidate cached matches and predictions;
repeated observations, player movement and clear-state updates do not.

Regression coverage exercises bundled and session predictions before/after both
visibility signals, unchanged-hash invalidation, current unknown scans, reset,
missing edge chunks, strict-template/map conflicts, name-only and remote grouping,
and confirmed connector recovery. The stored upper L-room name from the Bridges
trace remains unverified; this change does not rename catalog entries. Live play
with the rebuilt JAR remains to check.

## Unique partial room completion — 2026-09-18

At least two direct matching cells can reveal every remaining cell of a uniquely
placed room before it is fully visible. This includes two of four cells in a 2x2
or 1x4, as well as the remaining cell of an L. `DungeonRoomPrediction` compares
compatible template placements, including rotations/mirrors, complete alternatives
and larger variants. Only one room identity and footprint may remain;
transformations of the same room with the same footprint do not make the drawing
ambiguous. Two possible sides or
line ends stay unpredicted. Existing room cores, narrow map doors, physical doors,
map-visible cells, fully loaded empty cells and the dungeon grid bounds rule out
conflicting placements. A fully observed room needs no synthetic extension.

The September 19 correction removes an output filter that required exactly one
unseen cell even though candidate matching already supported all missing cells.
Both unseen cells now render together when two observed hashes uniquely locate a
four-cell room; the existing ambiguity, visibility and ownership guards remain.
An empty cell becoming fully loaded also invalidates the cached prediction even
when its hashes are unchanged. This visibility persists through later chunk
unloads; repeated loaded observations do not invalidate the cache again.

Predictions are cached separately alongside catalog matching and used only by the
render layout and its viewport. They never become scan evidence, learning records,
logical owners, score/clear credit or synchronized room data. Rendering rejects a
prediction that would cut up an existing logical room or overwrite remote cells or
doors. Real scans replace/remove predictions through the normal matching revision.
This restores early connections while retaining the Bridges four-cell safeguard.
Regression tests cover unique L/1x4/2x2 completion, adjacent or diagonal square
observations, ambiguity, larger/complete variants, conflicts in either unseen cell,
unchanged factual state, real bundled Museum data and cache invalidation. Live
early-room rendering remains to verify; synthetic predictions remain presentation
only as further cells are observed.

## Trap first loaded after completion — 2026-09-19, 16:53:48

The supplied 16:53:48 trace contains an unidentified room after the reported Trap
was completed before it rendered. Cell `1,0` is the likely candidate: its first
retained scan at 16:51:20.621 is empty (`-318865360` in both hashes), it is visited
by 16:51:47.117 and changes from map-only ownership to physical unknown ownership
at 16:52:17.879. Its first nonempty hash was suppressed by the shared map-change
rate limit. The same profile's Skyblocker log reports `trap-very-hard-3` at
16:51:47 and secret collections at 16:51:49/57, but this does not establish a Kung
hash identity. No catalog variant or metadata is added from this trace.

The map reader previously observed visibility/completion without room type.
It now recognizes generic Trap from a majority of exact palette byte `62` in the
bounded room interior. Installed Skyblocker 6.10.4+26.1.2 independently confirms
`DungeonMapUtils.getRoomType`: 62 is TRAP and 63 is a normal room; both share the
same base orange color. Checkmarks use separate 18/30/34 pixels. This source
establishes map interpretation, not Old/New identity or catalog metadata.

Map type survives later unknown physical hashes within the instance. Known
matches/hints take precedence; otherwise the render layout shows `Trap` with
the existing completion state and unknown secret/crypt totals. No learned identity
or secret count is inferred. The original trace has no retained map pixel data,
so this fallback still needs a live check on a completed Trap.

First nonempty room observations now bypass noisy map-change rate limits and have
a bounded 128-record reserve, also included in `/kung log copy`. This preserves
the evidence needed to add a verified changed-state hash if the case recurs.
Regression tests cover checkmark colors, normal-color rejection, late unknown
scans, known-identity precedence, unknown totals, reset and diagnostic retention.

## Tombstone first observed hashes — 2026-09-19, 16:38:42

The user identifies the question-mark room in `D:/Downloads/message (1).txt` as
Tombstone. In the later run, the 16:37:34.049 first scan records room cell `1,1`
(scan grid `2,2`, world `-153,-153`) with core `1351532750` and stable
`-195425460`. The next discovery record has `owner=cell:1,1`, `type=UNKNOWN` and
no hint. The player is in that cell at capture time, with a `1/2 Secrets` message.
The earlier run's Cage at the same coordinates belongs to a different instance.

Tombstone already had canonical RARE/2-secret metadata but an empty `variants`
array, so there was no identity evidence to match. Its new 1x1 variant contains
only the supplied observed hash pair, attributed to the user's identification.
The existing crypts=0 and prince=false remain unchanged; the saved selected Wiki
reference lists those fields as blank, not independently verified zero/absence.
No other room metadata/hashes, matching code or profile data changes.

The regression fails without the new pair and passes with it, checking raw/stable
lookup plus visible-cell recognition and the RARE render layout using bundled
resources. Live recognition on the next encounter remains to verify.

## Ice Fill first-scan variant — 2026-09-19, 00:10:01

The user identified the top-row `?` as Ice Fill. In
`kung-trace-20260919-001001.log`, cell `3,0` first changes from an empty column
to core `1904169381` / stable `578676452` at 00:08:56.515 and remains unknown.
Neither hash was bundled, and the trace contains no earlier Ice Fill match to
retain. This is a missing observed variant rather than loss of a recognized room.

The exact pair is appended to Ice Fill's existing one-cell variant, retaining
PUZZLE/0 secrets/0 crypts and all prior hashes. No prediction or retention logic
changes. The actual empty-to-visible sequence fails before the data addition and
passes afterward; it also checks direct raw/stable recognition and retention
through a later unknown puzzle-state change. Live recognition remains to verify.

## Blaze recognition and rescan — 2026-09-18, 23:26:08

The trace recognizes Blaze at cell `0,5` from core/stable
`1256805352/-1281477207`, then `-1595482137/994913400` at 23:25:41.672.
At 23:25:43.181 its match disappears and the door targets UNKNOWN. The next
hash pair was suppressed from the trace. `latest.log` reports the puzzle solved
at 23:25:50, then the user's explicit Blaze rescan at 23:26:05 with core
`-1229535227`. The visibility fix had allowed an unknown changed hash to replace
even a direct known observation. Snapshot replacement now distinguishes direct
catalog evidence from preload hints: unknown puzzle/block updates cannot erase
the former, while new direct evidence and instance reset still replace it.
Visibility gates and map boundaries remain in force.

The newly scanned core is bundled under Blaze, PUZZLE, one secret. Its stable hash
was not retained, so the added record uses `stable: 0` rather than inventing one.
This also recognizes the room when first scanned after the puzzle changes.

The profile JSON's 23:26:05 update contained only the initial known hash, despite
the success message naming the new core. With Local Data off, each append loaded
only runtime-enabled sources; appending the initial observation overwrote the
just-saved current one. Learning, crypt edits and deletion now read existing local
JSON for write preservation regardless of that recognition toggle. Local Data
remains off for matching until explicitly enabled. No profile files were changed
by this repair. Two regressions failed before the fixes and passed afterward;
coverage also checks the new bundled core, fresh direct evidence, completion,
reset and continued rejection of visible preload guesses. Live play remains to
verify; the exact first unknown hash and block responsible are not in the trace.

## Bridges owner merge — 2026-09-18, 22:23:34

`kung-trace-20260918-222334.log` retains two Bridges matches at 22:22:41.563:
the upper L at `4,0|5,0|4,1` and the lower pair at `3,2|4,2`. Both have six
secrets and were assigned the same logical owner. Their combined five cells
exceeded the render layout's safety limit, so it displayed five individual
Bridges labels, as in the supplied screenshot. Completion propagated to the
entire mistaken owner. The joining scan point `8,3` was `NONE`, not a blocking
Wither/Blood door. Identity-based merging ignored ordinary map-door boundaries
and neither owner-union pass checked the combined cell count.

Owner merging now rejects a union above four cells, retaining the existing
matched groups, and respects narrow external doors observed on the server map.
Only a successful union adds an internal connector. The map-connection pass uses
the same size guard, before visited/clear/completed state is expanded. Existing
Layers fragments still merge across broad internal connections. The change uses
bounded cached snapshot data and adds no world scan or persistence path.

Two regressions failed against the previous code, then passed: the recorded 3+2
match geometry stays as two render rooms with separate completion, even with a
synthetic erroneous map connection; a narrow map door separates same-named
fragments below the size limit while a broad connection still joins them. The
trace does not retain the final cell hashes, so the replay starts from its
recorded matches rather than claiming an exact raw-scan reconstruction.

The catalog also labels an L variant as Bridges, whereas the saved metadata
reference lists Bridges as 1x2. The user did not visit the upper rooms and cannot
identify them. No names, hashes, variants or secret/crypt totals were changed:
the upper room's correct identity remains unverified, separately from this
confirmed ownership defect. Live rendering with the rebuilt JAR remains to check.

## Manual Prince corrections

Stand in a recognized Catacombs room and run `/kung room prince true` or
`/kung room prince false`. The local confirmation names the room and its type.
The setting applies immediately to every variant of that room and survives
restarts. Unknown rooms and positions outside the Catacombs room grid are rejected.

Explicit corrections live in `config/kung/kung.json`, under
`dungeonMap.princeRoomOverrides`, keyed by normalized name, type and secret count.
They work with Local Data off and take precedence after bundled/local/remote
metadata normalization, including both true and false. NORMAL and RARE Lava Pit
remain separate. Templates, preload hints and existing session aliases all use the
override; catalog revision invalidation refreshes the map without rescanning.
The command changes Prince metadata only, not hashes, crypt counts, secret counts
or the run's Prince-kill bonus. It does not upload a report. With a linked project,
the exact project room record is updated first; profile overrides do not shadow
the linked data. Explicit true and false values survive alias normalization and
subsequent builds, including rooms recognized only through preload hints.

## Automatic project saving

Link the checkout once in the game using:

```text
/kung room project D:/Downloads/macros/kung
```

The validated absolute path persists in `config/kung/kung.json` under
`dungeonMap.roomDataProjectDirectory`. It defaults to empty/off. Paths with spaces
are accepted, with optional enclosing quotes. `/kung room project` (or `status`)
shows the link; `/kung room project off` returns to ordinary profile behavior.

While linked, room commands and automatic learning read and write
`versions/mc26_1_2/src/main/resources/kung-dungeon-scans/` directly:

- `known-rooms.json`: new `/kung room learn`, `look` and `learnmulti` observations,
  automatically learned stable hashes, Prince/crypt corrections, deletion and undo.
- `known-room-types.properties`: `/kung room type` hints and deletion cleanup.
- `known-room-preloads.jsonl`: repeated observed hash transitions. New observations
  carry `observed: true` and still need two agreeing observations, even after
  packaging in a JAR; older curated hints retain their existing trust level.

The linked project is authoritative even with Local Data off. It is not seeded
with stale JAR, profile or remote data, and linking does not import earlier profile
learning or Prince overrides. Live run sync remains independent; remote room
cache and undo history stay in the profile. All untouched JSON metadata, variant
IDs and hash source labels are retained. New observations merge only into the
matching name/type/secret-count identity, preserving both Lava Pit entries.
Unknown rooms still need a name/type/secret count through the existing learning
commands; scanning cannot infer a new room's identity.

Writes replace files atomically and reject missing or invalid project databases;
they do not recreate a moved checkout or fall back to overwriting it with JAR
data. Manual failures are reported in chat, automatic failures in `latest.log`.
Do not run two clients that write the same checkout concurrently. Project writes
occur at learning/edit time; ordinary builds do not sync profiles or install a JAR.
The next build packages the saved resource files for use without the project link.

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
