package com.github.beng420.kung.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class HypixelChatSenderTest {
    private final HypixelChatSender sender = HypixelChatSender.INSTANCE;

    @Test
    public void addsKungPrefixExactlyOnce() {
        assertEquals("[Kung] hello", sender.prefixedMessage(" hello "));
        assertEquals("[Kung] hello", sender.prefixedMessage("[Kung] hello"));
    }

    @Test
    public void buildsHypixelChatCommands() {
        assertEquals("pc [Kung] ready", sender.command("/pc", "ready"));
        assertEquals("msg Steve [Kung] ready", sender.command("msg Steve", "ready"));
    }

    @Test
    public void rejectsMissingChannels() {
        assertThrows(IllegalArgumentException.class, () -> sender.command("", "hello"));
    }
}
