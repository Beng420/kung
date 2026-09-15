package com.github.beng420.kung.feature.garden;

import static org.junit.Assert.*;

import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.Test;

public class VisitorAlarmReminderTest {
    private static final VisitorQueue.Snapshot FULL = new VisitorQueue.Snapshot(
        Set.of("Dulin", "Trinity", "Maeve", "Ludleth", "Taylor"), null, true);
    private static final VisitorQueue.Snapshot REPLACED = new VisitorQueue.Snapshot(
        Set.of("Beth", "Trinity", "Maeve", "Ludleth", "Taylor"), null, true);

    @Test
    public void remindsImmediatelyAndEveryTenSecondsOnlyWhileRinging() {
        var state = new VisitorAlarmState();
        assertFalse(state.reminderDue(0));
        state.observe(FULL, 0);
        assertFalse(state.reminderDue(1_000));
        state.seed(360_000, 0, 1_000);
        assertTrue(state.reminderDue(1_000));
        assertFalse(state.reminderDue(1_000));
        assertFalse(state.reminderDue(10_999));
        assertTrue(state.reminderDue(11_000));
        assertFalse(state.reminderDue(20_999));
        assertTrue(state.reminderDue(21_000));
    }

    @Test
    public void delayedTicksDoNotSendCatchUpMessages() {
        var state = ready();
        assertTrue(state.reminderDue(0));
        state.observe(null, 65_000);
        assertTrue(state.reminderDue(65_000));
        assertFalse(state.reminderDue(65_000));
        assertFalse(state.reminderDue(65_001));
        assertFalse(state.reminderDue(74_999));
        assertTrue(state.reminderDue(75_000));
    }

    @Test
    public void muteSuppressesSoundAndRemindersUntilTheNextVisitorCycle() {
        var state = ready();
        assertTrue(state.reminderDue(0));
        assertTrue(state.acknowledge());
        state.observe(FULL, 10_000);
        state.observe(null, 20_000);
        state.reduce(30_000, 25_000);
        state.tick(1_000_000);
        assertFalse(state.ringing());
        assertFalse(state.reminderDue(1_000_000));
        state.observe(REPLACED, 1_000_000);
        assertFalse(state.reminderDue(1_000_000));
        state.tick(1_360_000);
        assertTrue(state.ringing());
        assertTrue(state.reminderDue(1_360_000));
    }

    @Test
    public void departureAndResetClearThePreviousReminderDeadline() {
        var state = ready();
        assertTrue(state.reminderDue(0));
        state.observe(REPLACED, 1_000);
        assertFalse(state.reminderDue(1_000));
        state.seed(360_000, 0, 1_000);
        assertTrue(state.reminderDue(1_000));
        state.reset();
        assertFalse(state.reminderDue(2_000));
        state.observe(FULL, 2_000);
        state.seed(360_000, 0, 2_000);
        assertTrue(state.reminderDue(2_000));
    }

    @Test
    public void everyPartOfTheReminderOffersTheLocalMuteCommandAndExplanation() {
        var message = VisitorAlarmFeature.alarmMessage();
        assertTrue(message.getString().contains("click here to mute"));
        var command = new ClickEvent.RunCommand("/kung visitors mute");
        var text = new StringBuilder();
        message.visit((style, part) -> {
            text.append(part);
            assertEquals(command, style.getClickEvent());
            assertTrue(style.getHoverEvent() instanceof HoverEvent.ShowText);
            assertEquals("Mute sound and reminders until the next visitor cycle.",
                ((HoverEvent.ShowText) style.getHoverEvent()).value().getString());
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals(message.getString(), text.toString());
    }

    private static VisitorAlarmState ready() {
        var state = new VisitorAlarmState();
        state.observe(FULL, 0);
        state.seed(360_000, 0, 0);
        return state;
    }
}
