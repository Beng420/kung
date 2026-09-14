# Update notices and installation

The Kung settings header shows `Kung - v<installed version>` (for example,
`Kung - v0.3.1`), read from Fabric's metadata for the loaded mod.

The active Minecraft 26.1.2 client checks GitHub's latest release at startup,
when opening Kung settings, and when joining Hypixel. Checks run on the existing
updater worker; overlapping requests are coalesced. An available update, active
download or pending restart is reused rather than checked again.

Once both the player and a newer compatible release are ready, Kung shows one
local English popup per Hypixel connection. The card shows the installed
and latest published versions. It also arrives when the asynchronous check finishes
after joining. Repeated play joins and world changes on the same connection do
not repeat it; reconnecting allows a new notice. Other servers and singleplayer
receive no automatic notice. Failed checks, current versions, incompatible
releases, downloads and updates awaiting restart do not produce an availability
popup. The old availability chat message has been replaced by this card.

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
not alter release/install state or consume the automatic per-connection notification.
`/kung preview` is the shared command branch for previews; the old
`/kung updates preview` route is removed.

This uses Kung's own menu and requires no external Mod Menu mod. The existing
updater selects a newer numeric release with an installable JAR for the running
Minecraft version. Download and restart installation use the existing
[update storage paths](FILE_STORAGE.md). Normal builds neither install the JAR
nor download updates into a game profile.

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
several GUI sizes. The full Java-25 build passes
**345 tests**, with no failures/errors/skips. Actual in-game text wrapping,
layering, history selection/scrolling, link confirmation, command opening and
restart display remain live checks.

`KungUpdater` owns asynchronous release/install state and the Fabric connection
and tick callbacks. `KungUpdateNotification` owns connection deduplication
and hostname matching. `KungUpdateToast` owns the Fabric HUD/screen hooks,
rendering and menu/browser actions. `KungUpdateToastState` shares its animated
layout with hit testing and tracks monotonic visible time independently of ticks.
The client thread handles all notice state and delivery; the worker publishes
its result atomically. Only one card is retained; replacement restarts its lifetime.
`UserCommandGroup` / `KungCommandActions` route `/kung updates` to
`KungConfigScreen.updates()`, queued until chat has finished handling the action.

`KungUpdateNotificationTest` covers hostname boundaries, delayed player/update
readiness, repeated joins, world gaps, reconnects, stale connections and other
servers. `KungUpdateToastStateTest` covers animation/expiry, hover pause, hidden
time, replacement/dismissal, click-through prevention and resized layout/hit
targets. After the alias/preview revision, all 23 focused emote/config/update tests
and the Java 25 active-module build pass: **334 tests**, zero failures/errors/skips.
The built JAR's 0.3.2 metadata and packaged toast were verified; `git diff --check`
passes. Live preview fetching, Hypixel join, lobby transfer, screen layering,
GUI scales, buttons and browser link handling still require an in-game check.
Use `/kung preview updates` to check the
appearance without publishing a release. Tests do not install an update.

Visual behavior was informed by NoammAddons' public
[NotificationManager](https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/ui/notification/NotificationManager.kt)
and [UpdateChecker](https://github.com/Noamm9/NoammAddons/blob/26.1.2/src/main/kotlin/com/github/noamm9/features/impl/dev/UpdateChecker.kt)
(read 2026-09-13). Kung implements its own Java/Fabric card with its own theme,
update actions and connection policy.
