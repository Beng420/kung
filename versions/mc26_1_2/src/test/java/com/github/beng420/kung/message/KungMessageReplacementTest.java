package com.github.beng420.kung.message;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.Test;

public class KungMessageReplacementTest {
    @Test
    public void removesTheOwnedHistoryEntryAndEveryWrappedLineOnly() {
        var reminder = Component.literal("Visitor alert");
        var owned = local(reminder);
        var equalText = local(reminder.copy());
        var server = new GuiMessage(0, reminder, null, GuiMessageSource.SYSTEM_SERVER, null);
        var player = new GuiMessage(0, reminder, null, GuiMessageSource.PLAYER, null);
        var history = new ArrayList<>(List.of(server, owned, equalText, player));
        var lines = new ArrayList<>(List.of(line(server), line(owned), line(owned), line(equalText), line(player)));
        KungMessages.removeLocal(history, lines, reminder);
        assertEquals(List.of(server, equalText, player), history);
        assertEquals(List.of(line(server), line(equalText), line(player)), lines);
    }

    @Test
    public void removingAnOffscreenReminderAlsoPreventsItReturningAfterChatResize() {
        var reminder = Component.literal("Visitor alert");
        var other = local(Component.literal("Newer chat message"));
        var history = new ArrayList<>(List.of(other, local(reminder)));
        var lines = new ArrayList<>(List.of(line(other)));
        KungMessages.removeLocal(history, lines, reminder);
        assertEquals(List.of(other), history);
        assertEquals(history.stream().map(KungMessageReplacementTest::line).toList(), lines);
    }

    @Test
    public void missingEvictedAndClearedMessagesAreHarmless() {
        var reminder = Component.literal("Visitor alert");
        var other = local(Component.literal("Another message"));
        var history = new ArrayList<>(List.of(other));
        var lines = new ArrayList<>(List.of(line(other)));
        KungMessages.removeLocal(history, lines, null);
        KungMessages.removeLocal(history, lines, reminder);
        assertEquals(List.of(other), history);
        assertEquals(List.of(line(other)), lines);
        history.clear();
        lines.clear();
        KungMessages.removeLocal(history, lines, reminder);
        assertTrue(history.isEmpty());
        assertTrue(lines.isEmpty());
    }

    @Test
    public void successiveReplacementsLeaveOneClickableReminderBesideOtherMessages() {
        var history = new ArrayList<GuiMessage>();
        var lines = new ArrayList<GuiMessage.Line>();
        Component previous = null;
        var command = new ClickEvent.RunCommand("/kung visitors mute");
        for (int cycle = 0; cycle < 20; cycle++) {
            var other = local(Component.literal("Other " + cycle));
            history.addFirst(other);
            lines.addFirst(line(other));
            KungMessages.removeLocal(history, lines, previous);
            var next = Component.literal("Visitor alert").withStyle(style -> style.withClickEvent(command));
            var fresh = new GuiMessage(cycle * 200, next, null, GuiMessageSource.SYSTEM_CLIENT, null);
            history.addFirst(fresh);
            lines.addFirst(line(fresh));
            previous = next;
            assertEquals(cycle + 2, history.size());
            assertEquals(cycle + 2, lines.size());
            assertEquals(cycle * 200, lines.getFirst().addedTime());
            assertEquals(command, lines.getFirst().parent().content().getStyle().getClickEvent());
        }
        assertEquals(1, history.stream().filter(entry -> entry.content().getString().equals("Visitor alert")).count());
    }

    private static GuiMessage local(Component content) {
        return new GuiMessage(0, content, null, GuiMessageSource.SYSTEM_CLIENT, null);
    }

    private static GuiMessage.Line line(GuiMessage parent) {
        return new GuiMessage.Line(parent, parent.content().getVisualOrderText(), true);
    }
}
