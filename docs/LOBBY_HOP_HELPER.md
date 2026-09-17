# Lobby Hop Helper

Util > Lobby Hop Helper defaults off. While enabled, it remembers up to 128 lobby
IDs from the shared packet-driven `HypixelInstanceTracker`. A first visit is silent.
Returning to a remembered ID keeps the `Swap Lobbies` title and pling, adds a
subtitle such as `mini123 - 1m5s ago`, and posts a local warning:

`[Kung] You've been on lobby mini123 before! 1m5s ago.`

The monotonic timestamp refreshes on every valid tick in the current lobby. The
elapsed time therefore measures time since the last observed presence, not since
the previous arrival. Both messages use the same snapshot, formatted as whole
seconds below one minute and minutes plus seconds thereafter. Remaining in the
same lobby never repeats the alert; blank server IDs are not recorded.

History stays in memory across lobby travel and reconnects while enabled. Turning
the feature off clears it; restarting the game starts fresh. Capacity eviction
retains the existing first-insertion order, removing the oldest remembered ID.

`LobbyHopHelperFeatureTest` covers last-presence timing, repeated visits, quiet
same-lobby ticks, blank IDs, clearing, capacity eviction and compact time formatting.
Focused lobby/config tests and the full active-module Java 25 build validate the
implementation. Live subtitle/chat appearance and a timed return still need a game check.
