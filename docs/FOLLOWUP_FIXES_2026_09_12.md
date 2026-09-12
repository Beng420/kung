# Dungeon, audio and calculator follow-up — 2026-09-12

Active target: Minecraft 26.1.2, Kung 0.2.10. Parked modules and user profile files
are untouched. Input: `kung-trace-20260912-025947.log`, calculator HTTP 403 screenshot,
and the reported Salty_Whale CA50 discrepancy.

## Lava Pit

The previous name-based import incorrectly classified Kung's existing Lava Pit as
Rare because both duplicated wiki rows carry that name. The user confirms the
bundled room is normal. Its existing hashes, secrets and crypts are retained, and
its type is NORMAL again. Existing Lava Skull/Lava Tomb aliases follow that type.
JSON imports, remote reports and stale local Rare type hints cannot reintroduce
the error. The separately confirmed Rare core `-1005518830` remains a type-only
hint and is not renamed to Lava Pit.

## Splits

The old ping hook omitted bundled packets and did not deduplicate repeated IDs.
Fixed packet coverage/order and monotonic timing; phases without an observed tick
stream do not report all elapsed time as lag. The exact reported -5 seconds cannot
be determined from the supplied trace. Dialogue itself does not pause either
clock. New phase-boundary diagnostics distinguish counts and arrival gaps.
See [timing evidence and limitations](SPLITS_TIMING_FIXES.md).

## Custom sounds

Imported audio previously opened a separate device line/thread for every cue and
changed the device sample rate for pitch. Playback now mixes a bounded number of
voices into one fixed-rate stereo output with identical left/right samples. Pitch
conversion happens in software; per-file volume remains supported. Decode work
is bounded, stale queued cues are discarded, and disable/world change closes the
output. Decoder/device errors are recorded in the trace.

Sound triggers now run after the corresponding client sound packet was applied,
including bundled packets. Repeated Hyperion clicks no longer postpone an active
Wither Shield expiry estimate. That five-second item-use timer still estimates an
activation; it is not a server-confirmed cooldown measurement.

Ten audio/timer tests exercise real bundled WAV decoding, pitch conversion,
centering, clipping bounds, overlapping voices and simulated device lifecycle.
No real device was opened for tests. The friend's exact audible problem cannot
be reconstructed from this trace: its custom sound lists are empty. Vanilla
sounds remain spatial, and Java Sound uses the OS default output as before.

## Calculator and CA50

The GUI still requested PixelStats; it now shares the profile loader, typed
profile/floor stats and CA50 solver with the chat commands. Failed or unusable
responses are errors, rather than invented player data. Late GUI responses cannot
replace a newer username request.

The Salty_Whale response has selected Watermelon followed by empty Raspberry.
The previous selection loop let Raspberry replace Watermelon. The public
dungeon-only fixture reproduces both the exact incorrect 2436 runs under Derpy
and the correct website result: 1538 without Derpy (484 Archer, 514 Berserk,
540 Mage), or 1026 with Derpy. Selected profiles now retain priority; when none
is selected the strongest dungeon profile is used.

Adjectils supplies raw profile data; Kung performs the calculation locally. The
website's passive class XP factor is also one quarter, so that part was retained.
GUI/chat now share one solver instead of maintaining divergent copies.
The previously omitted Catacombs Explorer bonus is now applied once, separately
from the Expert Ring. The GUI converts the API's cumulative Epic shard count into
level 0–10, supports manual adjustment and labels its maximum preset when attribute
data is unavailable. Chat `!c50` retains its maximum-bonus projection including
Explorer 10. This does not change class XP or the CA50 result above.
Sources: [Adjectils calculator](https://adjectils.com/dungeon.html) and
[website API integration](https://adjectils.com/apireader.js).
See [calculator evidence and attribute sources](CATACOMBS_CALCULATOR_FIXES.md).

## Mimic

No trace exists for the reported missing marker. The current code deliberately
withholds a single-room map marker when candidates occur in several rooms, while
2D waypoints may still appear. An unfiltered static chest can cause this, but it
is not a proven diagnosis for that run. Added `mapReason`/`mapRoom` diagnostics.

The requested proposal is [MIMIC_STATIC_CHESTS_PLAN.md](MIMIC_STATIC_CHESTS_PLAN.md):
verified local chest coordinates per room variant, asymmetric anchors to establish
the actual rotation, exact transformed position checks, and conservative handling
of incomplete chunks or ambiguous rotations. The filter was not implemented in
0.2.10. It is now implemented with explicit capture in 0.2.14; see
[MIMIC_STATIC_CHESTS.md](MIMIC_STATIC_CHESTS.md) for the current state and missing
live templates.

## Validation

Combined active-module build passes with **220 tests, zero failures/errors/skips**.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.10.jar`; packaged version,
sound mixer and applied-ping hook verified. Data audit: 140 rooms, 193 variants,
402 components, 550 hashes, no cross-room hash conflicts. Live Minecraft visual
and headphone checks remain necessary; headless tests cannot establish the
friend's actual audio setup or reproduce the unlogged Mimic run.
