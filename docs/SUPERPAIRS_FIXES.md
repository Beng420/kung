# Superpairs fixes — September 11, 2026

Active module: `versions/mc26_1_2`.

## Evidence

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
- The display uses `Known`, rather than claiming the server awarded every observed
  pair. Until all observed field identities are revealed, total pairs are shown
  as an upper bound because concealed fields may still contain bonuses.
- Slot and full-content observations run after vanilla applies the packet on the
  client thread. The initial menu is seeded once; rendering reads cached counts.
  No Netty-thread access or repeated per-frame board scans remain in this helper.
- A different menu object starts fresh even if its title/container number matches
  the previous game. Resizing/reinitializing the same screen retains discoveries.
  Closing the game or disabling the helper clears the state on the client tick.
- `superpairs` trace events report enabled state, session identity, new reveals,
  count changes and resets without logging unchanged frames/button prompts.

## Validation

Eight new model regressions cover the supplied prompt and Instant Find sequence,
duplicate packets, hidden/empty replacements, repeated reward types, distinct
amounts/enchantments, excluded slots, uncertain totals and reset behavior.
`:versions:mc26_1_2:build` passed with Java 25: 175 tests, no failures/errors/skips.
Artifact: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.9.jar` (existing configured version).
The live game rendering and packet integration still require in-game verification.
