# Run statistics: 0.3.1, September 12, 2026

The supplied screenshot matches `logs/latest.log` at **20:04:52** in the
`Dungeons 26.1.2` Modrinth profile: party secrets 52, Beng114 shown as 52/52,
and eight player rows. The user confirmed the actual team as Beng114,
MisterPaladin, GlossyPenguin, marps and DeviousVI. ABCtheBass, ImBlatte and RaketeX
were older party members, with earlier joins retained in the normal game log.
No separate new Kung trace was supplied; the normal log sufficed to identify this run.

## Confirmed causes and correction

`summaryPlayers()` combined the current dungeon roster with global party UUIDs
and general cached player rows. `knownTrackedPlayerUuids()` also included global
party members. This admitted all three stale players even though they had no
current dungeon class row. The run now admits only self and identified dungeon
class rows, at most five, and retains this membership independently of later
party changes. Identity remapping preserves one row and updates its map slot;
instance reset clears membership. This fixes the source instead of truncating
an arbitrary eight-player summary to five rows.

The tab parser incorrectly labeled the shared integer `Secrets Found: n` as
personal. It overwrote even an API-derived personal value and advertised the
shared count to other Kung clients as a self-report. It now updates party counts
only. The installed Skyblocker 6.10.2 tab widget also treats this position as the
shared Dungeon Discoveries/Secrets display; its outdated `Discoveries:` matcher
accounts for the repeated unrelated parser errors in the game log. This correction
supersedes the older assumption that any integer `Secrets Found:` line is personal.

The profile check found `hypixelApiEnabled=false` and no API key. Kung therefore
had no direct API baseline/final measurements for teammates. The missing personal
values cannot be reconstructed from 52 party secrets, room clears or the screenshot.
Unknown stays `?`; the summary explains how many personal counters are available
and whether the API is unconfigured or data was not received. New `run-statistics`
events record roster, values, provenance and API availability without credentials.
API baseline requests now begin during instance preparation instead of waiting
until the run timer has already started. Existing generation/final guards remain.

Sync reports carry `secretsSource` (`API_DELTA` or `PERSONAL`). The client rejects
untagged/old `PERSONAL_TAB` reports so a 0.3.0 peer cannot reintroduce the erroneous
party-as-personal count. Only a local personal/API measurement is sent; received
reports are not forwarded. The companion `tools/kung-room-sync-server.mjs` retains
the field and keeps missing/negative counts unknown rather than converting them
to zero. Update that server alongside clients when using personal-data sync.
Old servers still handle room/door data, but strip the new player provenance.
No running server, profile setting or mod installation was changed.

## Validation and remaining live check

- Three screenshot regressions failed on 0.3.0 first: eight-player roster, 52 secrets
  credited to self and admission of a sixth class row. They pass after correction.
- Additional coverage checks UUID merging/reset, legacy report rejection, valid
  personal report decoding and unknown versus measured-zero explanations.
- Full Minecraft 26.1.2 build with Java 25: **300 tests**, no failures/errors/skips.
- A temporary local HTTP server round trip verified source retention, legacy
  reports remaining unverified and `-1` remaining unknown; server stopped afterward.
- `git diff --check` passes. JAR: `versions/mc26_1_2/build/libs/kung-26.1.2-0.3.1.jar`.

For a live check, finish a run with 0.3.1 and save `/kung log save` immediately
after the summary, with its screenshot. Expect exactly the confirmed team and
no automatic party-total credit to self. Complete personal counts require a
configured Hypixel API connection before the run or valid personal reports from
updated peers/server; this change cannot recover the old run's absent measurements.
