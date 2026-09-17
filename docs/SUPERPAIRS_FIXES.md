# Superpairs helper

Active module: `versions/mc26_1_2`.

## Display and limits — September 17, 2026

The HUD now leads with **Unseen pairs**: pairs for which neither card has ever
been revealed. Seen single cards are excluded because their hidden partners do
not make a completely unseen pair. With `U` unseen fields and `S` known singles,
the upper bound is `floor(max(0, U - S) / 2)`. Hidden fields may also be bonuses,
so a positive bound is shown as `up to N`, not an exact number.
When the bound reaches zero, `Unseen pairs: 0` is exact even if some partners or
bonuses remain concealed, provided bonus/reward identities were classified correctly.
Only that counter and non-XP reward rows appear in the HUD. Reward rows use
`Growth VI  1/2` or `Growth VI  2/2`; these describe remembered identities, not
server-confirmed reward claims. Enchantments stay first. Technical counts remain
in `/kung log`; enabling Debug adds packet counters there, never overlay rows.

No fixed number of bonuses is assumed. The supplied trace contains two, but one
game does not establish that every board has exactly two. A positive exact count
would require a verified bonus total or a server-supplied reward-pair total, neither
of which the current observer has. Likewise, no hidden-position ranking is inferred
from the trace. Under uniform random placement, all never-revealed positions are
equally likely for a particular missing partner; `1/N` is conditional on one missing
partner among `N` such positions, not evidence that one position is better.

Individual `Enchanting Exp` rows are hidden while their identities still contribute
to all counters. Different amounts and different icons with equal XP amounts stay
distinct. Experience bottles and other item rewards remain visible. Enchantments
sort first, with missing partners before complete matches within each group.
Books named `Enchanted Book` now read the enchantment/level from lore even when
Hypixel uses a sword, bow or armor icon. Without a recognizable lore line the
display retains the original name. Position, scale, default-off toggle and packet
ownership are unchanged; the feature tooltip explains the counts and XP filtering.

The supplied trace created at **2026-09-17 00:18:52 UTC** contains all 28 reveals
of a Metaphysical board: 26 reward cards and two Instant Find bonuses. Equal XP
labels occur on distinct icons (`139k`: bone meal/light-blue dye; `40k`: cocoa/red
dye), so combining their identities would corrupt the counting. At 02:18:36 local
time, three fields are unseen and one known single needs its partner: at most one
fully unseen pair remains. The book reveal at 02:18:41 leaves two fields and two
singles, proving zero fully unseen pairs before the final two reveals. The final
panel has 13 known pairs and only two reward rows: book and Titanic bottle.
The trace lacks book lore, so the specific enchantment cannot be recovered from it.

The follow-up trace created at **2026-09-17 00:34:53 UTC** reveals a real false zero:
at 02:34:27, the feather named `Gained +3 Clicks` was classified as a reward single.
That invented a missing partner. At 02:34:31, five fields remained unseen and the
old model subtracted four singles instead of three, yielding zero instead of an
upper bound of one. The orange `130k Enchanting Exp` pair then appeared in slots
41/37. `Gained +1/+2/+3 Click(s)` now count as bonuses; glass prompts and unrelated
feathers retain their classifications. The complete trace regression fails before
the fix and passes afterward. At the affected point the panel is only:

```text
Unseen pairs: up to 1
Grand Experience Bottle  2/2
```

After the first orange reveal, no completely unseen pair remains; the final model
has 13 known pairs, two bonuses and no singles. The user's saved Debug switch was
enabled, adding six technical lines to the previous HUD. Those lines, the extra
summary rows and `Known`/`Need` prefixes have been removed. The primary counter is
gold; position and scale are preserved. The example above is tested panel text,
not a screenshot of live rendering.

Six new regressions cover both complete traces, unknown-pair bounds, hidden XP with
preserved identities, enchantment priority, equipment-icon lore parsing and gained-click
bonus classification. The prior 00:18 trace remains covered alongside the false-zero fix.
All 23 focused Superpairs/HUD/settings tests and the full Java 25 build pass:
619 cases, 618 passed, no failures/errors, one Windows symlink-permission skip.
`git diff --check` passes. Artifact:
`versions/mc26_1_2/build/libs/kung-26.1.2-0.3.5.jar`; no profile installation.
Live checks remain for the compact panel, actual enchantment names/levels and
counter transitions during a fresh game. Tests do not prove game rendering.

## Earlier evidence — September 11, 2026

User trace: `kung-trace-20260911-175731.log`, from the Modrinth profile
`Here We Go Again (2)`.

- At 17:56:59.465, slot 10 is `cyan_stained_glass` named `Click a second button!`.
  The previous helper checked its broad bonus-name predicate before checking glass.
  Every concealed slot carrying this prompt could enter the remembered bonus set,
  permanently reducing the displayed pair total.
- At 17:57:01.181, slot 14 reveals the actual diamond `Instant Find` bonus.
  At 17:57:01.924–.927, slots 15 and 28 both reveal `133k Enchanting Exp`.
  The automatic partner reveal must count even though only slot 15 was clicked.
- The trace has no dedicated Superpairs state diagnostics. The generic loadout
  diagnostics are heavily suppressed and stop before the end of this game, so
  they cannot establish the complete final board or exact claimed pair count.
- `item=empty` in Kung's click diagnostics is read after local click prediction.
  It is not evidence of a missing server reward packet or a Skyblocker conflict.

## Changes

- `SuperpairsBoard` classifies glass before rewards and bonuses. Pane decorations,
  controls, player inventory and slots outside the supported board are excluded.
- Remembered board membership is a stable union of observed playable slots, not
  a count of whichever items happen to be visible in a frame. Reveals survive
  subsequent glass/empty replacements; repeated packets cannot duplicate a card.
- Four equal reward cards count as two known pairs; five leave one unmatched card.
  Amounts and enchantment names remain part of the identity.
- The model distinguishes known matches from claimed rewards and retains an upper
  bound for total pairs because concealed fields may still contain bonuses. The
  current HUD focuses on fully unseen pairs as described above.
- Slot and full-content observations run after vanilla applies the packet on the
  client thread. The initial menu is seeded once; rendering reads cached counts.
  No Netty-thread access or repeated per-frame board scans remain in this helper.
- A different menu object starts fresh even if its title/container number matches
  the previous game. Resizing/reinitializing the same screen retains discoveries.
  Closing the game or disabling the helper clears the state on the client tick.
- `superpairs` trace events report enabled state, session identity, new reveals,
  count changes and resets without logging unchanged frames/button prompts.

## Earlier validation — September 11, 2026

Eight new model regressions cover the supplied prompt and Instant Find sequence,
duplicate packets, hidden/empty replacements, repeated reward types, distinct
amounts/enchantments, excluded slots, uncertain totals and reset behavior.
`:versions:mc26_1_2:build` passed with Java 25: 175 tests, no failures/errors/skips.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.9.jar` (existing configured version).
The live game rendering and packet integration still require in-game verification.
