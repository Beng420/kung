# Update notices and installation

The Kung settings header shows `Kung - v<installed version>` (for example,
`Kung - v0.3.1`), read from Fabric's metadata for the loaded mod.

The active Minecraft 26.1.2 client checks GitHub's latest release at startup,
when opening Kung settings, and when joining Hypixel. Checks run on the existing
updater worker; overlapping requests are coalesced. An available update, active
download or pending restart is reused rather than checked again.

Once both the player and a newer compatible release are ready, Kung sends one
local English chat notice per Hypixel connection. The notice shows the installed
and available versions. It also arrives when the asynchronous check finishes
after joining. Repeated play joins and world changes on the same connection do
not repeat it; reconnecting allows a new notice. Other servers and singleplayer
receive no notice. Failed checks, current versions, incompatible releases,
downloads and updates awaiting restart remain silent in chat.

Hypixel matching uses the server address's hostname: `hypixel.net` or a subdomain,
with optional port, case differences or trailing DNS dot. Similar names such as
`nothypixel.net` and `hypixel.net.example.com` are not accepted. Custom aliases or
direct IP connections are not identified as Hypixel.

- **Open Updates** runs the local `/kung updates` command. It opens the existing
  Kung settings screen filtered to the update control, with its status expanded.
  The ordinary settings layout stays intact. Opening this screen does not install
  anything; the user presses the existing update button to start installation.
- **GitHub** opens the repository's latest release page through Minecraft's
  normal link handling.

This uses Kung's own menu and requires no external Mod Menu mod. The existing
updater selects a newer numeric release with an installable JAR for the running
Minecraft version. Download and restart installation use the existing
[update storage paths](FILE_STORAGE.md). Normal builds neither install the JAR
nor download updates into a game profile.

## Ownership and validation

`KungUpdater` owns asynchronous release/install state and the Fabric connection
and tick callbacks. `KungUpdateNotification` owns connection deduplication,
hostname matching and the interactive chat component. The client thread handles
all notice state and delivery; the worker publishes its result atomically.
`UserCommandGroup` / `KungCommandActions` route `/kung updates` to
`KungConfigScreen.updates()`, queued until chat has finished handling the action.

`KungUpdateNotificationTest` covers hostname boundaries, delayed player/update
readiness, repeated joins, world gaps, reconnects, stale connections, other
servers and the menu/link click actions. All five focused tests and the full
Java 25 active-module build pass: **310 tests**, zero failures/errors/skips.
Live Hypixel join, lobby transfer,
opening the menu from chat and browser link handling still require an in-game
check with a newer compatible release. Tests do not install an update.
