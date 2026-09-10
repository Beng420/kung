package com.github.beng420.kung.skyblock;

import java.util.List;

/** Keeps floor metadata inside the confirmed instance; entry banners describe the next transfer only. */
final class HypixelDungeonFloorState {
    private static final long ENTRY_LIFETIME_MILLIS = 30_000;
    private HypixelDungeonFloor current = HypixelDungeonFloor.UNKNOWN;
    private HypixelDungeonFloor pending = HypixelDungeonFloor.UNKNOWN;
    private long pendingUntil;
    private boolean transferred;

    HypixelDungeonFloor current() { return current; }

    void entryMessage(String message, long now) {
        HypixelDungeonFloor entry = HypixelDungeonFloor.fromEntryMessage(message);
        if (!entry.known()) return;
        pending = entry;
        pendingUntil = now + ENTRY_LIFETIME_MILLIS;
        transferred = false;
    }

    void worldChanged(long now) {
        current = HypixelDungeonFloor.UNKNOWN;
        expire(now);
        // Login/configuration/respawn can all describe the same transfer before location arrives.
        transferred = pending.known();
    }

    void observe(HypixelLocation location, List<String> lines, long now) {
        expire(now);
        if (location.kind() == HypixelLocation.Kind.CATACOMBS) {
            HypixelDungeonFloor visible = HypixelDungeonFloor.fromLines(lines);
            if (visible.known()) current = visible;
            else if (!current.known() && transferred) current = pending;
            if (transferred) clearPending();
        } else if (location.kind() != HypixelLocation.Kind.UNKNOWN) {
            current = HypixelDungeonFloor.UNKNOWN;
            if (transferred) clearPending();
        }
    }

    void reset() {
        current = HypixelDungeonFloor.UNKNOWN;
        clearPending();
    }

    private void expire(long now) {
        if (pending.known() && now > pendingUntil) clearPending();
    }

    private void clearPending() {
        pending = HypixelDungeonFloor.UNKNOWN;
        pendingUntil = 0;
        transferred = false;
    }
}
