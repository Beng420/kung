# 6th Visitor Alarm

Garden > **6th Visitor Alarm** defaults off. Volume ranges from 1–100% (default
70%) and also respects Minecraft's master volume. This is independent of Custom
Sounds and has no HUD or automatic visitor interactions.

## Detection and timing

`VisitorQueue` reads the server's ordered tab widget: `Visitors: (N)`, its N names,
and `Next Visitor: 1m 23s` / `Next Visitor: Queue Full!`. Five visitors alone do
not trigger: Hypixel hides the next timer as soon as the fifth arrives. The local
timer learns the interval from a visitor arrival while a numeric timer remains
visible, then carries that interval forward for the hidden sixth visitor.

`VisitorAlarmFeature` polls at most 80 tab entries every five client ticks, using
vanilla tab ordering. It requires a complete roster stable for 500 ms and waits
two seconds after an instance change. A fresh SkyBlock sidebar title and Garden
location are required; `SKYBLOCK GUEST` excludes other players' Gardens. Missing
or partially updated widgets do not count as a departure.

While hidden, mature crop clicks advance the prediction by 100 ms per distinct
consecutively clicked crop position. `GardenCropTracker` recognizes mature
wheat/carrot/potato, Nether Wart and cocoa, plus sugar cane, cactus, melon,
pumpkin/carved pumpkin, red/brown mushrooms, Wild Rose (`ROSE_BUSH`) and
Sunflower/Moonflower (both `SUNFLOWER`). Either flower half is recognized.
The existing game-mode mixin observes both initial and continued mining without
changing either operation; repeated calls on the same last crop count once.
The position guard resets on world/profile changes and feature reset. A confirmed pest kill
advances it by 30 seconds. Field Mouse rewards count only on `Dung`; Lunar Moth
rewards count only on `Enchanted Sunflower`. Other loot from those kills and
`Overclocker 3000` bonus rewards do not advance the timer. No time/name debounce
is used: simultaneous kills of the same pest must still count individually.
Only hidden timers receive these reductions,
so a visible server countdown is not counted twice. Messages are observed before
other mods can hide or reformat them, excluding action bars and player quotes.
Feast Kernel-rate activity uses the same crop classifier with its own position
guard; enabling either feature does not require enabling the other.

At first observation of an already-full queue, optional read-only SkyHanni
compatibility can seed its current profile's known interval and sixth-arrival
timestamp. The installed `SkyHanni-7.56.0-mc26.1.jar` was inspected for the tab
format, timing reductions and storage getters. No SkyHanni code or data is
bundled, written, or required. Missing APIs and invalid/sentinel timestamps fall
back to local tracking. A known ready timestamp triggers after roster stability.

**First-use limitation:** Hypixel does not expose the hidden timer directly. If
Kung starts with five visitors and no usable existing timer, it starts a
conservative full interval (15 minutes before learning the personal interval),
including farming/pest reductions. That first alarm may therefore be late.
Enabling before a visitor arrival allows local calibration. No API requests or
menus are opened in the background.

## Alarm lifecycle

When five visitors and the expired deadline coincide, the alarm latches. Two
alternating pling tones repeat every 500 ms. A local English chat reminder appears
immediately and every 10 seconds while ringing. Missed reminders are not replayed
after a stalled client resumes.
Each reminder replaces the previous owned alarm message, moving the fresh entry
to the bottom of chat and refreshing its visibility time. The previous entry is
removed from both history and all wrapped display lines, so resizing chat cannot
bring it back. Unrelated messages with identical text are preserved. Ownership
survives alarm resets: the next cycle also replaces any remaining prior reminder.
`KungMessages.replaceLocal` uses a small `ChatComponentAccessor` and retains a
surviving scroll anchor when the user is reading older chat. It does not use
signed-message deletion or replace a message with a deletion notice.
There is no timeout. Opening a menu, closing it, farming, missing tab data and
lobby travel do not acknowledge the alarm. The last roster/deadline survive a
lobby change within the same connection; a latched alarm keeps playing outside
the Garden until acknowledged.

Clicking any part of the reminder runs the registered client command
`/kung visitors mute`. It immediately stops both the current sound and further
reminders, including outside the Garden, without disabling the feature. Hover
text and a local confirmation explain that the mute lasts until the next visitor
cycle. Neither the reminders nor the command are sent to server chat. Running
the command with no active alarm does not mute a future cycle.

Clicking `Accept Offer` or `Refuse Offer` immediately acknowledges a ringing
alarm and stops its sound instance in the click handler. This deliberately also
silences an unsuccessful offer attempt; no server reply or departure is required.
The game-mode hook reads the button before local inventory prediction. It checks
the current screen/container ID, a top-inventory left/right or shift-click,
a title matching the visitor roster, and the info item's `Offers Accepted:` lore.
Other menu buttons, player inventory slots and unrelated offers do not silence it.
The click itself is neither changed nor cancelled.

Acknowledgement lasts for the current visitor cycle: old tab data, missing data,
repeated timer updates, crop reductions and pest drops cannot restart the alarm.
A visitor disappearing from the stable server roster also stops the sound and
rearms the next cycle, including when the sixth visitor immediately replaces them
and the count stays five. A new hidden interval then starts. Disabling the feature,
disconnecting, shutting down or changing SkyBlock profile clears the state and
stops only this feature's sound instance. Timer state is session-only; settings
are saved through the ordinary Kung configuration.

## Validation

`VisitorAlarmTest` covers complete/partial widgets, numeric/full/locked states,
guest gating, fifth versus sixth arrival, personal intervals, crop/pest timing,
indefinite latching, departures with immediate replacement, reset and optional
timestamp validation. `ConfigRegressionTest` covers defaults, normalization and
saved enable/volume settings. `VisitorPestTimingTest` replays the live multi-drop
burst and covers Field Mouse, Lunar Moth, bonus loot and simultaneous kills.
`VisitorCropTrackerTest` uses actual Minecraft block states for flower halves,
Garden pumpkins, existing maturity gates, non-crops and repeated/mutable input
positions. It combines the latest trace's eight pest timestamps with a controlled
450-Wild-Rose-click fixture: each click subtracts once and the final pest triggers
the alarm. The live trace has no individual crop count, so 450 is a test input,
not a claimed live measurement. Without harvest reductions the same pest sequence
leaves exactly the trace's 44,375 ms. Visible countdowns remain server-owned.
`VisitorOfferTest` covers accept/refuse acknowledgement with the NPC still
present, delayed data, unrelated controls, reset and rearming after replacement.
`VisitorAlarmReminderTest` covers immediate/10-second delivery, stalled ticks,
mute/rearming, departure/reset and inherited click/hover events across the message.
`VisitorAlarmCommandTest` verifies that the mute action resolves in the client
command tree.
`KungMessageReplacementTest` covers wrapped/offscreen removal, preservation of
equal-text and server/player messages, cleared history and repeated clickable
replacements. The active Minecraft 26.1.2 chat implementation and installed
SkyHanni chat-render mixin were inspected locally; no SkyHanni dependency is added.
Run these and the full active-module build.

The 2026-09-15 Java 25 full build passes **519 tests**, with zero failures,
errors or skips. The focused crop/alarm/message/config suite passes **41 tests**, including five
new crop regressions. Three of the new tests fail against the old crop classifier
and pass with the missing blocks included. The built JAR contains the crop tracker;
`git diff --check` passes. No game profile was modified or JAR installed.

The supplied 20:39:12 trace and matching `Here We Go Again (2)/logs/latest.log`
show a false alarm at 20:38:50.760, only 3.913 seconds after the fifth visitor's
360-second timer began. A single Field Mouse emitted seven reward messages;
the old parser subtracted 30 seconds for every line. The complete burst contains
14 rewards for eight kills, including two Mites and two Rats. The new replay
failed before the fix and passes afterward: eight reductions leave 116,087 ms
at the false-alarm timestamp, then trigger at the later predicted deadline.
The installed SkyHanni pest-profit tracker confirms the guaranteed-reward rules
for Field Mouse and Lunar Moth and the Overclocker exclusion.

The 2026-09-15 01:15:07 UTC trace (03:15:07 local) and user confirmation identify
a late alarm while farming Wild Roses. The fifth visitor starts a 360-second
hidden interval at 03:13:25.443. Eight pest kills correctly subtract 240 seconds;
at 03:14:41.068 the local remainder is 44,375 ms, accounting for elapsed time and
pests but no farming reductions. `ROSE_BUSH` was missing from the classifier.
The installed SkyHanni crop mappings also identify `SUNFLOWER` and
`CARVED_PUMPKIN`, which were missing too. All three now count. The same trace
records other alarm starts and immediate Refuse Offer acknowledgements; it does
not establish correct timing for this newly fixed crop path.

Live checks remain: sound volume and repetition, replacement of the 10-second
chat reminder (including wrapping, resizing and scroll position) and
immediate chat-click mute, a full farming cycle,
immediate click silence for accept/refuse (including unsuccessful attempts),
next-cycle rearming after replacement,
off-toggle silence and the optional startup seed. `visitor-alarm` trace records
include source/reason, roster, remaining time, interval, alarm state and the
number of crop reductions in the current roster/full-state interval.
`visitor-pest` records the pest, item, count decision and remaining milliseconds
before/after each hidden-timer reward, including ignored bonuses, plus that crop
count. `visitor-crop` records the block, count and before/after time on the first
and each 100th crop reduction while the hidden timer is positive, avoiding
per-block trace spam. Save
`/kung log save` immediately if a live countdown or stop differs.
