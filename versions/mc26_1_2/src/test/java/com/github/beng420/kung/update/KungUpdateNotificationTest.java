package com.github.beng420.kung.update;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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

    @Test
    public void noticeOffersLocalMenuAndHttpsReleaseLinkWithHoverHelp() {
        Component message = KungUpdateNotification.message("0.3.1", "0.3.2");
        assertTrue(message.getString().contains("Kung 0.3.2 is available (installed: 0.3.1)"));
        List<Component> actions = new ArrayList<>();
        collectActions(message, actions);
        assertEquals(2, actions.size());
        assertEquals("[Open Updates]", actions.get(0).getString());
        assertEquals(new ClickEvent.RunCommand("/kung updates"), actions.get(0).getStyle().getClickEvent());
        assertEquals("[GitHub]", actions.get(1).getString());
        assertEquals(new ClickEvent.OpenUrl(URI.create("https://github.com/Beng420/kung/releases/latest")),
            actions.get(1).getStyle().getClickEvent());
        for (Component action : actions) {
            assertTrue(action.getStyle().isUnderlined());
            assertTrue(action.getStyle().getHoverEvent() instanceof HoverEvent.ShowText);
        }
    }

    private static void collectActions(Component component, List<Component> actions) {
        if (component.getStyle().getClickEvent() != null) actions.add(component);
        component.getSiblings().forEach(sibling -> collectActions(sibling, actions));
    }
}
