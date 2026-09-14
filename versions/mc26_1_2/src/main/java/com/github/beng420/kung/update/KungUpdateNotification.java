package com.github.beng420.kung.update;

import java.util.Locale;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Client-thread state, scoped to the network connection rather than individual worlds. */
final class KungUpdateNotification {
    private Object connection;
    private boolean hypixel;
    private boolean notified;

    boolean joined(Object connection, String address) {
        if (this.connection == connection) return false;
        this.connection = connection;
        hypixel = isHypixelAddress(address);
        notified = false;
        return hypixel;
    }

    void disconnected(Object connection) {
        if (this.connection != connection) return;
        this.connection = null;
        hypixel = false;
        notified = false;
    }

    boolean shouldNotify(Object connection, boolean playerReady, boolean updateAvailable) {
        if (connection == null || this.connection != connection || !hypixel || notified
            || !playerReady || !updateAvailable) return false;
        notified = true;
        return true;
    }

    static boolean isHypixelAddress(String address) {
        if (address == null || address.isBlank()) return false;
        String host = ServerAddress.parseString(address.trim()).getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

}
