# Update notices and installation

The Kung settings header shows `Kung - v<installed version>` (for example,
`Kung - v0.3.1`), read from Fabric's metadata for the loaded mod.

The active Minecraft 26.1.2 client checks GitHub's latest release at startup and
every 30 seconds from the client tick, independently of opening Kung settings.
Menu/join requests share that interval instead of creating extra requests or
postponing the next poll. Checks run on the existing updater worker; overlapping
requests are coalesced. Polling continues with an already available update, but
pauses during a download or pending restart. A known release stays usable during
refreshes and temporary request failures; late results cannot overwrite a download
or installation state.

Once both the player and a newly found compatible release are ready, Kung shows a
local English popup with the installed and latest versions. This also works when
the check finishes after joining, without opening a menu. After the card finishes
sliding out or is explicitly closed, a five-minute cooldown starts. Once it expires,
the next Hypixel lobby/world change can show the same update again, followed by
another cooldown. Expiry or polling the same release alone does not repeat it.
Transfers during the card/cooldown are discarded rather than queued. An eligible
transfer can wait for its loading screen to finish. Repeated packet resets during
one transfer coalesce; reconnecting cannot bypass the cooldown. A newly published
version can trigger a fresh notice from polling, still respecting an active card
and cooldown. Other servers and singleplayer receive no automatic notice. Initial
failed checks, current versions, incompatible releases, downloads and updates
awaiting restart do not create an availability popup.

The card slides into the bottom-right corner over 250 ms, stays for eight seconds
of visible reading time, then slides out over 250 ms. A shrinking accent bar
indicates the remaining time. Hovering with an open screen pauses the countdown;
the close button dismisses it immediately. It stays above the chat input, uses
Kung's existing theme and follows the normal GUI scale, shrinking on small windows.
Loading screens/resource overlays and F1 hide it without spending its visible time.
It draws once, over either the HUD or the open screen. Disconnecting, changing
connections or starting installation clears an actual update card.

Hypixel matching uses the server address's hostname: `hypixel.net` or a subdomain,
with optional port, case differences or trailing DNS dot. Similar names such as
`nothypixel.net` and `hypixel.net.example.com` are not accepted. Custom aliases or
direct IP connections are not identified as Hypixel.

- **Open Updates** opens the same menu as the local `/kung updates` command: the existing
  Kung settings screen filtered to the update control, with its status expanded.
  The Updates entry is always expanded, showing Installed/Latest version labels
  and a **Changelogs → Open** button; right-click cannot collapse it.
  Opening this screen does not install
  anything; the user presses the existing update button to start installation.
- **GitHub** opens the repository's latest release page through Minecraft's
  link confirmation screen.

The popup never grabs the mouse or opens a screen automatically. Open chat or
another menu to click its buttons. Clicks on the card are consumed before the
underlying screen, including clicks on its background. Other clicks and gameplay
input remain available. `/kung updates` still works after the popup expires.
`/kung preview updates` uses the same card, buttons and normal notification text,
including in singleplayer. It fetches GitHub's latest release on the updater worker
and shows `Installed: v<loaded version>` and `Latest: v<published version>`.
The title says `Kung update available` for a compatible update, `New Kung release`
for an incompatible newer release, or `Kung is up to date` if the installed version
is equal to or newer than the published one. No version is fabricated and no
preview-specific prose appears on the card. Missing releases/check failures produce
a local error message instead. Repeated pending preview requests are coalesced;
results are discarded after disconnecting or switching connections. Preview does
not alter release/install state, the polling interval or automatic notice eligibility.
Replacing an already-running automatic card ends that card and starts its normal
cooldown; a preview alone never starts a cooldown.
`/kung preview` is the shared command branch for previews; the old
`/kung updates preview` route is removed.

This uses Kung's own menu and requires no external Mod Menu mod. The existing
updater selects a newer numeric release with an installable JAR for the running
Minecraft version. Download and restart installation use the existing
[update storage paths](FILE_STORAGE.md). Normal builds neither install the JAR
nor download updates into a game profile.

## Safe installation and launcher ownership — 2026-09-16

Modrinth starts retain update checks/notices, but the installation control reads
**Updates: Use Modrinth** and cannot replace a mod file. Use Modrinth's own update
or import workflow. Detection uses its IPC system properties or the exact
`theseus`/`modrinth` launcher brand, including starts with a custom brand but the
standard IPC properties. This conservatively includes unmanaged files in those
starts; Kung does not edit Modrinth's database or guess which JAR it owns.

The supplied `mods/kung-26.1.2-0.3.1.jar needs repair or re-import` screenshot
matches Modrinth's [managed-file validation error](https://github.com/modrinth/code/blob/1faf434ad5b1a6675f1acbc55c1c7342b2646a98/packages/app-lib/src/state/content_store/commands/instance_files.rs#L71-L116).
Its managed records validate both the path and content; [rescanning preserves
conflicting managed bindings](https://github.com/modrinth/code/blob/1faf434ad5b1a6675f1acbc55c1c7342b2646a98/packages/app-lib/src/state/instances/commands/sync_content_files.rs#L173-L227).
Thus either renaming or replacing a managed JAR externally can invalidate it.
The screenshot alone does not establish which operation caused the friend's error.

For other launchers, installation follows these rules:

- Only the loaded standalone JAR directly inside this profile's `mods/` can be
  replaced. Symlink paths and external/cache origins are rejected. The installed
  filename stays unchanged; Fabric's metadata determines the version.
- Downloads stay under `config/kung/updates/`. The worker enforces the release's
  exact byte count, a 128 MiB limit, the advertised SHA-256 when present, full ZIP
  entry sizes/CRCs and bounded expansion, Kung identity/version/classes and Fabric
  required dependencies/conflicts, including Minecraft, Java and loader versions.
  Only a verified download is published as `verified-<sha256>.jar`.
- The pending transaction records old/new SHA-256 values and exact paths using a
  flushed temporary file and atomic marker publication. Unverified legacy or
  malformed markers are preserved as `rejected-<uuid>.properties`, never executed.
  A changed installed JAR cancels stale recovery rather than downgrading it.
- A JDK-only helper runs from a separate content-addressed JAR, without shell or
  VBS scripts. It waits for the exact game PID/start identity to exit. An installer
  lock serializes transactions; a game session lock lasts until JVM exit and
  prevents replacement once another initialized Kung session owns it.
- The helper verifies the download again, flushes a temporary replacement and
  preserves the old bytes as `previous.jar` outside `mods/`. Its only commit is an
  atomic replacement of the exact original path. There is no delete-first or
  copy-over-active-JAR fallback. Unsupported atomic moves, cross-volume custom
  config paths, access errors and changed files leave the existing JAR intact.
- Success clears the pending marker and keeps the verified source and backup.
  A crash after the commit can be recognized by its new hash without reinstalling.
  Interrupted downloads never create a pending transaction. An interrupted helper
  leaves the transaction retryable: the next start validates it and queues a new
  helper for that session's exit. Startup never replaces an already loaded JAR.
  Consequently, after shutting down the PC before installation finishes, the next
  launch may still use the old version and another close/start applies the update.

The success chat now says **verified and queued**, not **installed**. Installer
results appear in `logs/kung/update-installer.log`; preparation/recovery failures
are in `logs/latest.log`. A blocked recovery disables further installation in that
session instead of overwriting the pending transaction. Managed-launcher startup
preserves any pending marker as rejected without applying it.

`KungUpdateDownloadTest`, `KungUpdateInstallerTest`, `KungUpdateEnvironmentTest`
and `KungUpdateProcessTest` cover local HTTP failures/limits, corrupt/wrong or
incompatible JARs, launcher detection, transaction-boundary interruptions,
unsupported atomic replacement, altered targets/sources, marker recovery and a
real isolated Java helper process including session-lock contention. Temporary
test profiles are the only installation targets. Windows may skip the symlink
creation case when the OS denies that capability; path-confinement tests still run.
The final Java-25 active-module build passed: 575 cases, 574 passed, zero failures
or errors and one skipped Windows symlink-creation case; 71 cases belong to the
update package (38 newly added). The real-process test kills a waiting helper,
retries while a dummy game JVM is alive and confirms installation only after that
JVM exits. The built 0.3.4 JAR includes the standalone helper and nested classes;
`git diff --check` also passes.

These checks do not prove live launcher compatibility or physical power-loss
durability. File data is flushed, but directory flushing is unavailable on some
filesystems, notably Windows. Disk/controller failure remains outside the atomic
replacement guarantee. The session lock starts during Kung initialization, after
Fabric discovery; a very fast new launch during an older helper's commit still
needs live validation. Modrinth's update/import flow, normal exit, interrupted
download, PC shutdown and immediate relaunch remain live checks. No user profile
or launcher database was modified during development.

## Patch notes after installation

The first Kung settings opening for an unseen installed version displays a modal
over the existing menu. This includes `/kung`, `/kung updates` and other entry
points that open `KungConfigScreen`. It uses the loaded Fabric version, so it also
works after replacing the JAR manually. The first use of this feature, without an
existing acknowledgement, offers the current version's notes once as well.

For the initial popup, `KungReleaseNotes` reads GitHub's public release body for the exact installed tag:
first `v<version>`, then `<version>` only after a 404. It never substitutes the
latest release's notes. Empty, missing or mismatched releases are not acknowledged.
The asynchronous request shares the updater's worker and HTTP client, coalesces
overlapping requests and reuses the fetched notes within the session. HTTP requests
have a 15-second timeout; responses over 1 MiB are rejected before parsing/rendering.

The modal shows a loading state, then a scrollable text area with basic Markdown
headings, lists, code text and readable links. Images remain text references;
release prose cannot execute commands or open URLs. **GitHub** opens that version's
release using Minecraft's confirmation screen. **Got it**, Enter or Escape closes
the modal and reveals the unchanged settings screen. The wheel scrolls the hovered
pane; arrows, Page Up/Down and Home/End scroll the last clicked/scrolled pane
(notes initially). All mouse/keyboard input is consumed by the modal;
the transient update toast pauses while it is open.

The first actual rendering of the release body acknowledges the installed version.
`config/kung/updates/release-notes.properties` stores `lastSeenVersion`, respecting
custom Fabric config paths. Explicitly closing with **Close**, **Got it**, Escape
or Enter also stores `lastDismissedVersion`, even during loading or a missing/failed
release. Either receipt suppresses further automatic offers for that installed
version across menu openings and restarts. Dismissal does not claim that the notes
were read. The replacement is atomic where supported; shutdown flushes a queued
receipt, including while a request is still pending. A new installed version gets
its own offer. Loading/failure alone does not dismiss anything. Failed requests
can still be retried manually.

`/kung changelog` and the permanent **Changelogs → Open** button open the modal
even after the installed version was acknowledged or dismissed. They reuse cached current notes,
or requests them again after restarting, without clearing the receipt. Failed
manual requests can be retried just like automatic ones.

The history is a permanent narrow sidebar to the left of the notes. Opening the
popup requests its first list page; no separate History/Back screen is needed.
The installed version stays pinned at the top and returns to its notes on click.
Compact version buttons below it select historical releases, with the selection
highlighted. Each pane has its own scroll position and scrollbar; selecting a
version resets only the notes, while paging resets only the list. Small **<** / **>**
buttons at the sidebar's foot load or revisit pages of ten releases on demand.
Clicking a version requests that exact tag's notes. Published prereleases are
included; drafts and tags without a GitHub release are not. **GitHub** always opens
the selected release. The popup grows up to 560 logical units to accommodate the
100-unit sidebar while retaining the notes' reading width; footer actions are at
most 58 units wide. All controls share the menu's 80% scaling.

`KungReleaseHistory` coalesces pending requests and keeps at most four completed
list pages and eight historical note results in memory. It does no startup work,
body prefetching or disk writes. Only the visible sidebar page is requested when
the popup opens; additional pages require navigation. GitHub's [list-releases API](https://docs.github.com/en/rest/releases/releases#list-releases)
includes bodies in a page response; Kung retains only tags/dates from that response
and fetches the selected body on demand. The API's next-page marker enables the
button; requests always use Kung's own GitHub endpoint, not an arbitrary Link URL.
Responses have the same timeout and size limits as installed-version notes.
Failed pages/bodies offer their own **Retry** in the corresponding pane. Each body result belongs to its tag, so late
responses cannot replace another selection. Reading historical notes cannot
acknowledge or overwrite the installed-version receipt.

The repository's public release list was checked on 2026-09-13: the newest release
was `v0.3.1`, with a heading and bullet-list body. The local `0.3.2` build therefore
needs its matching release/body published before it can display real 0.3.2 notes.
No GitHub release was created or modified by this change.

## Ownership and validation

`KungReleaseNotes` owns the installed release-body request and version receipt;
`KungReleaseHistory` owns lazy list pages and historical body caches.
`KungReleaseNotesPopup` owns wrapping, scrolling and modal input inside
`KungConfigScreen`. `KungClient` flushes the receipt during shutdown.
Ten `KungReleaseNotesTest` cases cover first use, coalescing, display-only
acknowledgement, restarts/version changes, failed/missing releases, corrupt receipts,
shutdown flushing, exact tags, manual reopening/retrying after acknowledgement,
explicit dismissal during loading/failure/missing releases across restarts,
late completion after dismissal, and a real HTTP round trip against a local server. Six `KungReleaseHistoryTest`
cases cover sidebar opening without body prefetching, lazy requests, coalescing, cache eviction, selection while earlier
requests finish, receipt isolation, retries, draft filtering and actual paginated
HTTP/tag requests against a local server. Layout bounds, including separate panes,
compact actions and non-overlapping retry/navigation buttons, are checked at
several GUI sizes. See [current handoff](AI_HANDOFF.md) for full Java-25 validation.
Actual in-game text wrapping,
layering, history selection/scrolling, link confirmation, command opening and
restart display remain live checks.

`KungUpdater` owns asynchronous release/install state and the Fabric connection
and tick callbacks. `KungUpdateCheckSchedule` owns the monotonic 30-second poll
interval. `KungUpdateNotification` owns hostname matching, observed shared instance
epochs, release eligibility and the monotonic cooldown. It only consumes
`HypixelInstanceTracker.instanceEpoch()`; no dungeon lifecycle rules change.
`KungUpdateToast` owns the Fabric HUD/screen hooks,
rendering and menu/browser actions. `KungUpdateToastState` shares its animated
layout with hit testing and tracks monotonic visible time independently of ticks.
The client thread handles all notice state and delivery; the worker publishes
its result atomically. Only one card is retained; replacement restarts its lifetime.
The card's one-shot completion callback starts the automatic cooldown on expiry,
dismissal or cancellation, including disconnect. Passive `update-check` and
`update-notice` trace entries record polls/results, display and cooldown start.
`UserCommandGroup` / `KungCommandActions` route `/kung updates` to
`KungConfigScreen.updates()`, queued until chat has finished handling the action.

`KungUpdateNotificationTest` covers hostname boundaries, delayed player/update
readiness, exact cooldown boundaries, ignored early transfers, stationary expiry,
repeated joins/packet epochs, world gaps, reconnects, stale connections, new releases,
preview isolation and duplicate completion. `KungUpdateCheckScheduleTest` covers
immediate/periodic checks, menu/join requests, busy workers and monotonic clock origins.
`KungUpdateToastStateTest` covers animation/expiry, hover pause, hidden
time, replacement/dismissal, click-through prevention and resized layout/hit
targets. The update package's 33 tests pass after the polling/cooldown revision.
See [current handoff](AI_HANDOFF.md) for the latest full-build count.
The built JAR's 0.3.4 metadata and packaged toast were verified; `git diff --check`
passes. Live background polling, timed lobby reminders, preview fetching, Hypixel join, screen layering,
GUI scales, buttons and browser link handling still require an in-game check.
Use `/kung preview updates` to check the
appearance without publishing a release. Tests do not install an update.

Visual behavior was informed by NoammAddons' public
[NotificationManager](https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/ui/notification/NotificationManager.kt)
and [UpdateChecker](https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/features/impl/dev/UpdateChecker.kt)
(read 2026-09-13). Kung implements its own Java/Fabric card with its own theme,
update actions and connection policy.
