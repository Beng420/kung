package com.github.beng420.kung.update;

import java.util.Locale;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Client-thread notice eligibility; world changes never bypass a completed card's cooldown. */
final class KungUpdateNotification {
    static final long COOLDOWN_MILLIS = 5 * 60_000L;
    private Object connection;
    private boolean hypixel;
    private boolean hasEpoch;
    private long epoch;
    private boolean lobbyNotice;
    private boolean active;
    private boolean coolingDown;
    private long finishedAt;
    private String notifiedVersion = "";

    boolean joined(Object connection, String address, long nowMillis) {
        if (this.connection == connection) return false;
        this.connection = connection;
        hypixel = isHypixelAddress(address);
        hasEpoch = false;
        lobbyNotice = hypixel && !active && cooldownExpired(nowMillis);
        return hypixel;
    }

    void disconnected(Object connection) {
        if (this.connection != connection) return;
        this.connection = null;
        hypixel = false;
        hasEpoch = false;
        lobbyNotice = false;
    }

    boolean shouldNotify(Object connection, long epoch, boolean playerReady, String availableVersion,
                         boolean toastBusy, long nowMillis) {
        if (connection == null || this.connection != connection || !hypixel) return false;
        if (!hasEpoch || this.epoch != epoch) {
            if (hasEpoch && !active && cooldownExpired(nowMillis)) lobbyNotice = true;
            this.epoch = epoch;
            hasEpoch = true;
        }
        if (!playerReady || availableVersion.isBlank() || active || toastBusy || !cooldownExpired(nowMillis)
            || (!lobbyNotice && availableVersion.equals(notifiedVersion))) return false;
        notifiedVersion = availableVersion;
        lobbyNotice = false;
        active = true;
        return true;
    }

    void finished(long nowMillis) {
        if (!active) return;
        active = false;
        coolingDown = true;
        finishedAt = nowMillis;
        // Transfers during display/cooldown are not queued for a stationary reminder later.
        lobbyNotice = false;
    }

    private boolean cooldownExpired(long nowMillis) {
        return !coolingDown || nowMillis - finishedAt >= COOLDOWN_MILLIS;
    }

    static boolean isHypixelAddress(String address) {
        if (address == null || address.isBlank()) return false;
        String host = ServerAddress.parseString(address.trim()).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

}
