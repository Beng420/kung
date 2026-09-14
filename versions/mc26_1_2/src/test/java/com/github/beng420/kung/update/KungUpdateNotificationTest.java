package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public final class KungUpdateNotificationTest {
    @Test
    public void matchesHypixelHostsWithPortsWithoutAcceptingLookalikes() {
        for (String address : List.of("hypixel.net", "mc.hypixel.net", "alpha.hypixel.net",
            " MC.HYPIXEL.NET:25565 ", "hypixel.net.:25565")) {
            assertTrue(address, KungUpdateNotification.isHypixelAddress(address));
        }
        for (String address : List.of("", "localhost", "127.0.0.1:25565", "nothypixel.net",
            "hypixel.net.example.com", "mc.hypixel.net@evil.example", "[::1]:25565",
            "hypixel.net:invalid")) {
            assertFalse(address, KungUpdateNotification.isHypixelAddress(address));
        }
        assertFalse(KungUpdateNotification.isHypixelAddress(null));
    }

    @Test
    public void waitsForBothPlayerAndAsyncResultThenNotifiesOnlyOnce() {
        KungUpdateNotification notification = new KungUpdateNotification();
        Object connection = new Object();
        assertTrue(notification.joined(connection, "mc.hypixel.net"));
        assertFalse(notification.shouldNotify(connection, false, true));
        assertFalse(notification.shouldNotify(connection, true, false));
        assertTrue(notification.shouldNotify(connection, true, true));
        assertFalse(notification.shouldNotify(connection, true, true));
        assertFalse(notification.shouldNotify(connection, false, true));
        assertFalse(notification.shouldNotify(connection, true, true));
    }

    @Test
    public void worldGapsAndRepeatedPlayJoinsDoNotRepeatTheNotice() {
        KungUpdateNotification notification = new KungUpdateNotification();
        Object connection = new Object();
        notification.joined(connection, "mc.hypixel.net");
        assertTrue(notification.shouldNotify(connection, true, true));
        assertFalse(notification.shouldNotify(null, false, true));
        assertFalse(notification.joined(connection, "mc.hypixel.net"));
        assertFalse(notification.shouldNotify(connection, true, true));
    }

    @Test
    public void reconnectAllowsAnotherNoticeButOtherServersAndStaleResultsStayQuiet() {
        KungUpdateNotification notification = new KungUpdateNotification();
        Object first = new Object();
        notification.joined(first, "mc.hypixel.net");
        notification.disconnected(first);
        assertFalse(notification.shouldNotify(first, true, true));
        assertFalse(notification.shouldNotify(null, false, true));

        Object other = new Object();
        assertFalse(notification.joined(other, "example.org"));
        assertFalse(notification.shouldNotify(other, true, true));
        assertFalse(notification.shouldNotify(first, true, true));
        Object second = new Object();
        assertTrue(notification.joined(second, "mc.hypixel.net:25565"));
        notification.disconnected(other);
        assertTrue(notification.shouldNotify(second, true, true));
        notification.disconnected(second);
        Object third = new Object();
        assertTrue(notification.joined(third, "mc.hypixel.net"));
        assertTrue(notification.shouldNotify(third, true, true));
    }

}
