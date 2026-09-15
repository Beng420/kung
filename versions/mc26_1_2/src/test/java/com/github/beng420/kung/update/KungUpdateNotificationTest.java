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
    public void waitsForPlayerAndBackgroundResultWithoutOpeningAMenu() {
        KungUpdateNotification notification = new KungUpdateNotification();
        Object connection = new Object();
        assertTrue(notification.joined(connection, "mc.hypixel.net", 0));
        assertFalse(notification.shouldNotify(connection, 1, false, "0.3.3", false, 0));
        assertFalse(notification.shouldNotify(connection, 1, true, "", false, 1));
        assertTrue(notification.shouldNotify(connection, 1, true, "0.3.3", false, 2));
        assertFalse(notification.shouldNotify(connection, 1, true, "0.3.3", false, 3));
        assertFalse(notification.shouldNotify(connection, 2, true, "0.3.3", false, 400_000));
    }

    @Test
    public void cooldownStartsAfterTheCardFinishesAndNeedsALaterLobbyChange() {
        var notification = new KungUpdateNotification();
        var connection = new Object();
        notification.joined(connection, "mc.hypixel.net", 0);
        assertTrue(notify(notification, connection, 1, 0));
        notification.finished(8_500);
        assertFalse(notify(notification, connection, 2, 300_000));
        assertFalse(notify(notification, connection, 3, 308_499));
        assertFalse(notify(notification, connection, 3, 308_500)); // Expiry alone cannot replay an old transfer.
        assertFalse(notify(notification, connection, 3, 600_000)); // Further polling of the same release stays quiet.
        assertTrue(notify(notification, connection, 4, 600_001));
        notification.finished(608_501);
        assertFalse(notify(notification, connection, 5, 908_500));
        assertTrue(notify(notification, connection, 6, 908_501));
    }

    @Test
    public void eligibleTransferWaitsForThePlayerAndCoalescesPacketEpochs() {
        var notification = new KungUpdateNotification();
        var connection = new Object();
        notification.joined(connection, "mc.hypixel.net", 0);
        assertTrue(notify(notification, connection, 1, 0));
        notification.finished(10);
        assertFalse(notification.shouldNotify(null, 2, false, "0.3.3", false, 300_010));
        assertFalse(notification.shouldNotify(connection, 2, false, "0.3.3", false, 300_011));
        assertFalse(notification.shouldNotify(connection, 3, false, "0.3.3", false, 300_012));
        assertTrue(notify(notification, connection, 4, 300_013));
        assertFalse(notification.joined(connection, "mc.hypixel.net", 300_014));
        assertFalse(notify(notification, connection, 5, 300_015));
    }

    @Test
    public void lobbyChangeDuringCooldownDoesNotQueueAReminderWhenLoadingEnds() {
        var notification = new KungUpdateNotification();
        var connection = new Object();
        notification.joined(connection, "mc.hypixel.net", 0);
        assertTrue(notify(notification, connection, 1, 0));
        notification.finished(1_000);
        assertFalse(notification.shouldNotify(connection, 2, false, "0.3.3", false, 300_999));
        assertFalse(notify(notification, connection, 2, 301_001));
        assertTrue(notify(notification, connection, 3, 301_002));
    }

    @Test
    public void reconnectCannotBypassCooldownAndOtherServersOrStaleConnectionsStayQuiet() {
        var notification = new KungUpdateNotification();
        var first = new Object();
        notification.joined(first, "mc.hypixel.net", 0);
        assertTrue(notify(notification, first, 1, 0));
        notification.finished(10);
        notification.disconnected(first);
        assertFalse(notify(notification, first, 2, 20));
        assertFalse(notify(notification, null, 2, 20));
        var other = new Object();
        assertFalse(notification.joined(other, "example.org", 30));
        assertFalse(notify(notification, other, 2, 400_000));
        var second = new Object();
        assertTrue(notification.joined(second, "mc.hypixel.net:25565", 40));
        notification.disconnected(other);
        assertFalse(notify(notification, first, 3, 40));
        assertFalse(notify(notification, second, 3, 40));
        assertFalse(notify(notification, second, 3, 300_010));
        notification.disconnected(second);
        var third = new Object();
        assertTrue(notification.joined(third, "mc.hypixel.net", 300_011));
        assertTrue(notify(notification, third, 4, 300_011));
    }

    @Test
    public void previewDoesNotConsumeTheAutomaticNoticeAndDuplicateCompletionDoesNotExtendCooldown() {
        var notification = new KungUpdateNotification();
        var connection = new Object();
        notification.joined(connection, "mc.hypixel.net", 0);
        assertFalse(notification.shouldNotify(connection, 1, true, "0.3.3", true, 0));
        notification.finished(100); // No automatic card started.
        assertTrue(notify(notification, connection, 1, 101));
        notification.finished(1_000);
        notification.finished(5_000);
        assertTrue(notify(notification, connection, 2, 301_000));
    }

    @Test
    public void newlyPublishedVersionCanNotifyFromPollingButStillHonorsCooldown() {
        var notification = new KungUpdateNotification();
        var connection = new Object();
        notification.joined(connection, "mc.hypixel.net", 0);
        assertTrue(notify(notification, connection, 1, 0));
        notification.finished(1_000);
        assertFalse(notification.shouldNotify(connection, 1, true, "0.3.4", false, 300_999));
        assertTrue(notification.shouldNotify(connection, 1, true, "0.3.4", false, 301_000));
        notification.finished(301_001);
        assertFalse(notification.shouldNotify(connection, 2, true, "", false, 601_001));
        assertFalse(notification.shouldNotify(connection, 2, true, "", false, 900_000));
    }

    private static boolean notify(KungUpdateNotification notification, Object connection, long epoch, long now) {
        return notification.shouldNotify(connection, epoch, true, "0.3.3", false, now);
    }

}
